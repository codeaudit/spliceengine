package com.splicemachine.derby.hbase;

import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.splicemachine.SpliceKryoRegistry;
import com.splicemachine.constants.SIConstants;
import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.derby.cache.SpliceCache;
import com.splicemachine.derby.impl.job.coprocessor.CoprocessorJob;
import com.splicemachine.derby.impl.job.scheduler.AsyncJobScheduler;
import com.splicemachine.derby.impl.job.scheduler.SimpleThreadedTaskScheduler;
import com.splicemachine.derby.impl.sql.execute.operations.Sequence;
import com.splicemachine.derby.impl.store.access.SpliceAccessManager;
import com.splicemachine.derby.logging.DerbyOutputLoggerWriter;
import com.splicemachine.derby.utils.ErrorReporter;
import com.splicemachine.derby.utils.SpliceUtils;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.hbase.SpliceMetrics;
import com.splicemachine.hbase.TempCleaner;
import com.splicemachine.hbase.writer.WriteCoordinator;
import com.splicemachine.job.*;
import com.splicemachine.si.api.HTransactorFactory;
import com.splicemachine.tools.CachedResourcePool;
import com.splicemachine.tools.EmbedConnectionMaker;
import com.splicemachine.tools.ResourcePool;
import com.splicemachine.utils.Snowflake;
import com.splicemachine.utils.SpliceLogUtils;
import com.splicemachine.utils.ZkUtils;
import com.splicemachine.utils.kryo.KryoPool;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.reporting.JmxReporter;
import net.sf.ehcache.Cache;
import org.apache.derby.drda.NetworkServerControl;
import org.apache.derby.iapi.db.OptimizerTrace;
import org.apache.derby.iapi.error.StandardException;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.PleaseHoldException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.log4j.Logger;

import javax.management.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Scott Fines
 *         Created on: 3/1/13
 */
public class SpliceDriver extends SIConstants {
    private static final Logger LOG = Logger.getLogger(SpliceDriver.class);
    private final List<Service> services = new CopyOnWriteArrayList<Service>();
    protected SpliceCache cache;
    private JmxReporter metricsReporter;

    public static enum State{
        NOT_STARTED,
        INITIALIZING,
        RUNNING,
        STARTUP_FAILED, SHUTDOWN
    }

    public static interface Service{

        boolean start();

        boolean shutdown();
    }

    private String startNode;
    private static final SpliceDriver INSTANCE = new SpliceDriver();
    private static final KryoPool kryoPool;
    static{
        kryoPool = new KryoPool(SpliceConstants.kryoPoolSize);
        kryoPool.setKryoRegistry(new SpliceKryoRegistry());
    }

    private AtomicReference<State> stateHolder = new AtomicReference<State>(State.NOT_STARTED);

    private volatile Properties props = new Properties();

    private volatile NetworkServerControl server;

    private volatile WriteCoordinator writerPool;
    private volatile CountDownLatch initalizationLatch = new CountDownLatch(1);

    private ExecutorService executor;
    private TaskScheduler threadTaskScheduler;
    private JobScheduler jobScheduler;
    private TaskMonitor taskMonitor;
    private TempCleaner tempCleaner;
    private SnowflakeLoader snowLoader;
    private volatile Snowflake snowflake;
    private final MetricsRegistry spliceMetricsRegistry = new MetricsRegistry();

    private ResourcePool<Sequence,Sequence.Key> sequences = CachedResourcePool.
            Builder.<Sequence,Sequence.Key>newBuilder().expireAfterAccess(1l,TimeUnit.MINUTES).generator(new ResourcePool.Generator<Sequence,Sequence.Key>() {
        @Override
        public Sequence makeNew(Sequence.Key refKey) throws StandardException {
            return new Sequence(refKey.getTable(),
                    SpliceConstants.sequenceBlockSize,refKey.getSysColumnsRow(),
                    refKey.getStartingValue(),refKey.getIncrementSize());
        }

        @Override
        public void close(Sequence entity) throws Exception{
            entity.close();
        }
    }).build();

    private SpliceDriver(){
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("splice-lifecycle-manager").build();
        executor = Executors.newSingleThreadExecutor(factory);


        try {
            snowLoader = new SnowflakeLoader();
            writerPool = WriteCoordinator.create(SpliceUtils.config);
            threadTaskScheduler = SimpleThreadedTaskScheduler.create(SpliceUtils.config);
            jobScheduler = new AsyncJobScheduler(ZkUtils.getZkManager(),SpliceUtils.config);
            taskMonitor = new ZkTaskMonitor(SpliceConstants.zkSpliceTaskPath,ZkUtils.getRecoverableZooKeeper());
            tempCleaner = new TempCleaner(SpliceUtils.config);
        } catch (Exception e) {
            throw new RuntimeException("Unable to boot Splice Driver",e);
        }
    }

    public ZkTaskMonitor getTaskMonitor() {
        return (ZkTaskMonitor)taskMonitor;
    }

    public WriteCoordinator getTableWriter() {
        return writerPool;
    }

    public Properties getProperties() {
        return props;
    }

    public TempCleaner getTempCleaner() {
        return tempCleaner;
    }

    public <T extends Task> TaskScheduler<T> getTaskScheduler() {
        return (TaskScheduler<T>)threadTaskScheduler;
    }

    public TaskSchedulerManagement getTaskSchedulerManagement() {
        //this only works IF threadTaskScheduler implements the interface!
        return (TaskSchedulerManagement)threadTaskScheduler;
    }

    public <J extends CoprocessorJob> JobScheduler<J> getJobScheduler(){
        return (JobScheduler<J>)jobScheduler;
    }

    public JobSchedulerManagement getJobSchedulerManagement() {
        return (JobSchedulerManagement)jobScheduler;
    }

    public void registerService(Service service){
        this.services.add(service);
        //If the service is registered after we've successfully started up, let it know on the same thread.
        if(stateHolder.get()==State.RUNNING)
            service.start();
    }

    public void deregisterService(Service service){
        this.services.remove(service);
    }

    public static SpliceDriver driver(){
        return INSTANCE;
    }

    public State getCurrentState(){
        return stateHolder.get();
    }

    public void start(){
        if(stateHolder.compareAndSet(State.NOT_STARTED,State.INITIALIZING)){
            executor.submit(new Callable<Void>(){
                @Override
                public Void call() throws Exception {
                    try{
                        SpliceLogUtils.info(LOG,"Booting the SpliceDriver");

                        SpliceLogUtils.info(LOG,"Starting Cache");
                        startCache();


                        writerPool.start();

                        //all we have to do is create it, it will register itself for us
                        SpliceMetrics metrics = new SpliceMetrics();

                        boolean setRunning = true;
                        SpliceLogUtils.debug(LOG, "Booting Database");
                        setRunning = bootDatabase();
                        //table is set up
                        snowflake = snowLoader.load();
                        SpliceLogUtils.debug(LOG, "Finished Booting Database");

                        //register JMX items --have to wait for the db to boot first
                        registerJMX();

                        if(!setRunning){
                            abortStartup();
                            return null;
                        }
                        SpliceLogUtils.debug(LOG, "Starting Services");
                        setRunning = startServices();
                        SpliceLogUtils.debug(LOG, "Done Starting Services");
                        if(!setRunning) {
                            abortStartup();
                            return null;
                        }

                        SpliceLogUtils.debug(LOG, "Starting Server");
                        setRunning = startServer();
                        SpliceLogUtils.debug(LOG, "Done Starting Server");
                        if(!setRunning) {
                            abortStartup();
                            return null;
                        } else
                            stateHolder.set(State.RUNNING);
                        initalizationLatch.countDown();
                        return null;
                    }catch(Exception e){
                        SpliceLogUtils.error(LOG,"Unable to boot Splice Machine",e);
                        ErrorReporter.get().reportError(SpliceDriver.class,e);
                        throw e;
                    }
                }
            });
        }
    }

    private boolean bootDatabase() throws Exception {
    	HBaseAdmin admin = null;
    	Connection connection = null;
        try{
        	admin = SpliceAccessManager.getAdmin(config);
        	HTableDescriptor desc = new HTableDescriptor(SpliceMasterObserver.INIT_TABLE);
        	admin.createTable(desc);

        	return false;
        }  catch (PleaseHoldException pe) {
        	Thread.currentThread().sleep(5000);
        	SpliceLogUtils.info(LOG, "Waiting for Splice Schema to Create");
        	return bootDatabase();
        } catch (Exception e) {
			EmbedConnectionMaker maker = new EmbedConnectionMaker();
			connection = maker.createNew();
			return true;
        } finally{
        	if (connection != null)
        		connection.close();
        	Closeables.close(admin,true);
        }
    }

    public ResourcePool<Sequence,Sequence.Key> getSequencePool(){
        return sequences;
    }

    public void shutdown(){
        executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try{
                    SpliceLogUtils.info(LOG,"Shutting down connections");
                    if(server!=null) server.shutdown();

                    SpliceLogUtils.info(LOG,"Shutting down services");
                    for(Service service:services){
                        service.shutdown();
                    }

                    metricsReporter.shutdown();
                    SpliceLogUtils.info(LOG,"Destroying internal Engine");
                    stateHolder.set(State.SHUTDOWN);
                }catch(Exception e){
                    SpliceLogUtils.error(LOG,
                            "Unable to shut down properly, this may affect the next time the service is started",e);
                }
                return null;
            }
        });
    }

/********************************************************************************************/
    /*private helper methods*/

    private void registerJMX()  {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try{

            writerPool.registerJMX(mbs);

            //registry metricsRegistry
            metricsReporter = new JmxReporter(spliceMetricsRegistry);
            metricsReporter.start();;

            //register error reporter
            ObjectName errorReporterName = new ObjectName("com.splicemachine.error:type=ErrorReport");
            mbs.registerMBean(ErrorReporter.get(),errorReporterName);

            //register TaskScheduler
            ObjectName taskSchedulerName = new ObjectName("com.splicemachine.job:type=TaskSchedulerManagement");
            mbs.registerMBean(threadTaskScheduler,taskSchedulerName);

            //register TaskMonitor
            ObjectName taskMonitorName = new ObjectName("com.splicemachine.job:type=TaskMonitor");
            mbs.registerMBean(taskMonitor,taskMonitorName);

            //register JobScheduler
            ObjectName jobSchedulerName = new ObjectName("com.splicemachine.job:type=JobSchedulerManagement");
            mbs.registerMBean(jobScheduler.getJobMetrics(),jobSchedulerName);

            //register transaction stuff
            ObjectName transactorName = new ObjectName("com.splicemachine.txn:type=TransactorStatus");
            mbs.registerMBean(HTransactorFactory.getTransactorStatus(), transactorName);
        } catch (MalformedObjectNameException e) {
            //we want to log the message, but this shouldn't affect startup
            SpliceLogUtils.error(LOG,"Unable to register JMX entries",e);
        } catch (NotCompliantMBeanException e) {
            SpliceLogUtils.error(LOG, "Unable to register JMX entries", e);
        } catch (InstanceAlreadyExistsException e) {
            SpliceLogUtils.error(LOG, "Unable to register JMX entries", e);
        } catch (MBeanRegistrationException e) {
            SpliceLogUtils.error(LOG, "Unable to register JMX entries", e);
        }
    }

    @SuppressWarnings("deprecation")
	private boolean ensureHBaseTablesPresent() {
        SpliceLogUtils.info(LOG, "Ensuring Required Hbase Tables are present");
        HBaseAdmin admin = null;
        try{
            admin = new HBaseAdmin(SpliceUtils.config);
            if(!admin.tableExists(TEMP_TABLE_BYTES)){
                HTableDescriptor td = SpliceUtils.generateDefaultSIGovernedTable(TEMP_TABLE);
                admin.createTable(td);
                SpliceLogUtils.info(LOG, TEMP_TABLE+" created");
            }
            if (!admin.tableExists(TRANSACTION_TABLE_BYTES)) {
                HTableDescriptor desc = new HTableDescriptor(TRANSACTION_TABLE_BYTES);
                desc.addFamily(new HColumnDescriptor(DEFAULT_FAMILY.getBytes(),
                        Integer.MAX_VALUE,
                        compression,
                        DEFAULT_IN_MEMORY,
                        DEFAULT_BLOCKCACHE,
                        Integer.MAX_VALUE,
                        DEFAULT_BLOOMFILTER));
                desc.addFamily(new HColumnDescriptor(DEFAULT_FAMILY));
                admin.createTable(desc);
            }
            return true;
        }catch(Exception e){
            SpliceLogUtils.error(LOG,"Unable to set up HBase Tables",e);
            return false;
        }finally{
            if(admin!=null){
                try{
                    admin.close();
                } catch (IOException e) {
                    SpliceLogUtils.error(LOG,"Unable to close Hbase admin, this could be symptomatic of a deeper problem",e);
                }
            }
        }
    }
     private boolean startServices() {
        try{
            SpliceLogUtils.info(LOG, "Splice Engine is Running, Enabling Services");
            boolean started=true;
            for(Service service:services){
                started = started &&service.start();
            }
            return started;
        }catch(Exception e){
            //just in case the outside services decide to blow up on me
            SpliceLogUtils.error(LOG,"Unable to start services, aborting startup",e);
            return false;
        }
    }

    private void abortStartup() {
        stateHolder.set(State.STARTUP_FAILED);
    }

    private boolean startServer() {
        SpliceLogUtils.info(LOG, "Services successfully started, enabling Connections");
        try{
            server = new NetworkServerControl(InetAddress.getByName(derbyBindAddress),derbyBindPort);
            server.setLogConnections(true);
            server.start(new DerbyOutputLoggerWriter());
            SpliceLogUtils.info(LOG,"Ready to accept connections");
            return true;
        }catch(Exception e){
            SpliceLogUtils.error(LOG,"Unable to start Client/Server Protocol",e);
            return false;
        }
    }
    
    private boolean startCache() {
    		//cache = new SpliceCache("Splice");
    		return true;    	
    }
    
    public Cache getCache(String cacheName) {
    	return cache.getCacheManager().getCache(cacheName);
    }

    public Snowflake getUUIDGenerator(){
        return snowflake;
    }

    public void loadUUIDGenerator() throws IOException {
        snowflake =  snowLoader.load();
    }

    public MetricsRegistry getRegistry(){
        return spliceMetricsRegistry;
    }

    
    public static void setOptimizerTrace(boolean onOrOff) {
    	SpliceLogUtils.trace(LOG, "setOptimizerTrace %s", onOrOff);
    	OptimizerTrace.setOptimizerTrace(onOrOff);
    }

    public static void writeOptimizerTraceOutput(String filename) throws StandardException {
    	SpliceLogUtils.trace(LOG, "writeOptimizerTraceOutput %s", filename);
    	OptimizerTrace.writeOptimizerTraceOutputText(filename);
    }

    public static KryoPool getKryoPool(){
        return kryoPool;
    }

}
