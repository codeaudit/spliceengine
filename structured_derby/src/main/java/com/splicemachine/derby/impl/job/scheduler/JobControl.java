package com.splicemachine.derby.impl.job.scheduler;

import com.splicemachine.constants.bytes.BytesUtil;
import com.splicemachine.derby.impl.job.coprocessor.*;
import com.splicemachine.derby.stats.TaskStats;
import com.splicemachine.derby.utils.AttemptsExhaustedException;
import com.splicemachine.job.JobFuture;
import com.splicemachine.job.JobStats;
import com.splicemachine.job.Status;
import com.splicemachine.job.TaskFuture;
import com.splicemachine.utils.SpliceLogUtils;
import com.splicemachine.utils.SpliceZooKeeperManager;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author Scott Fines
 * Created on: 9/17/13
 */
class JobControl implements JobFuture {
    private static final Logger LOG = Logger.getLogger(JobControl.class);
    private final CoprocessorJob job;
    private final NavigableSet<RegionTaskControl> tasksToWatch;
    private final BlockingQueue<RegionTaskControl> changedTasks;
    private final Set<RegionTaskControl> failedTasks;
    private final Set<RegionTaskControl> completedTasks;
    private final Set<RegionTaskControl> cancelledTasks;

    private volatile boolean cancelled = false;
    private final JobStatsAccumulator stats;
    private final SpliceZooKeeperManager zkManager;
    private final int maxResubmissionAttempts;
    private final JobMetrics jobMetrics;
    private final String jobPath;

    JobControl(CoprocessorJob job, String jobPath,SpliceZooKeeperManager zkManager, int maxResubmissionAttempts, JobMetrics jobMetrics){
        this.job = job;
        this.jobPath = jobPath;
        this.zkManager = zkManager;
        this.jobMetrics = jobMetrics;
        this.stats = new JobStatsAccumulator(job.getJobId());
        this.tasksToWatch = new ConcurrentSkipListSet<RegionTaskControl>();

        this.changedTasks = new LinkedBlockingQueue<RegionTaskControl>();
        this.failedTasks = Collections.newSetFromMap(new ConcurrentHashMap<RegionTaskControl, Boolean>());
        this.completedTasks = Collections.newSetFromMap(new ConcurrentHashMap<RegionTaskControl, Boolean>());
        this.cancelledTasks = Collections.newSetFromMap(new ConcurrentHashMap<RegionTaskControl, Boolean>());
        this.maxResubmissionAttempts = maxResubmissionAttempts;
    }

    @Override
    public Status getStatus() throws ExecutionException {
        if(failedTasks.size()>0) return Status.FAILED;
        else if(cancelled) return Status.CANCELLED;
        else if(completedTasks.size()>=tasksToWatch.size()) return Status.COMPLETED;
        else return Status.EXECUTING;
    }

    @Override
    public void completeAll() throws ExecutionException, InterruptedException, CancellationException {
        while(getRemainingTasks()>0)
            completeNext();
    }

    @Override
    public void completeNext() throws ExecutionException, InterruptedException, CancellationException {
        if(failedTasks.size()>0){
            for(RegionTaskControl taskControl:failedTasks)
                taskControl.complete(); //throw the error right away
        }else if(cancelled)
            throw new CancellationException();

        RegionTaskControl changedFuture;
        int futuresRemaining = getRemainingTasks();
        SpliceLogUtils.trace(LOG,"[%s]Tasks remaining: %d",job.getJobId(),futuresRemaining);
        boolean found;
        while(futuresRemaining>0){
            changedFuture = changedTasks.take();
            found = !completedTasks.contains(changedFuture) &&
                    !failedTasks.contains(changedFuture) &&
                    !cancelledTasks.contains(changedFuture);

            futuresRemaining = getRemainingTasks();
            if(!found) continue;

            Status status = changedFuture.getStatus();
            switch (status) {
                case INVALID:
                    SpliceLogUtils.trace(LOG,"[%s] Task %s is invalid, resubmitting",job.getJobId(),changedFuture.getTaskNode());
                    stats.invalidTaskCount.incrementAndGet();
                    changedFuture.resubmit();
                    break;
                case PENDING:
                case EXECUTING:
                    break;
                case FAILED:
                    try{
                        SpliceLogUtils.trace(LOG, "[%s] Task %s failed",job.getJobId(),changedFuture.getTaskNode());
                        stats.addFailedTask(changedFuture.getTaskId());
                        changedFuture.dealWithError();
                    }catch(ExecutionException ee){
                        //update our metrics
                        failedTasks.add(changedFuture);
                        throw ee;
                    }
                    break;
                case COMPLETED:
                    SpliceLogUtils.trace(LOG,"[%s] Task %s completed successfully",job.getJobId(),changedFuture.getTaskNode());
                    try{
                        if(changedFuture.commit(maxResubmissionAttempts,maxResubmissionAttempts)){
                            TaskStats taskStats = changedFuture.getTaskStats();
                            if(taskStats!=null)
                                this.stats.addTaskStatus(changedFuture.getTaskNode(),taskStats);
                            completedTasks.add(changedFuture);
                            return;
                        }else{
                            //our commit failed, we have to resubmit the task (if possible)
                            SpliceLogUtils.debug(LOG,"[%s] Task %s did not successfully commit",job.getJobId(),changedFuture.getTaskNode());
                            changedFuture.dealWithError();
                        }
                    }catch(ExecutionException ee){
                        //we could not commit, and we can't retry--this is terminally bad
                        failedTasks.add(changedFuture);
                    }
                    break;
                case CANCELLED:
                    SpliceLogUtils.trace(LOG,"[%s] Task %s is cancelled",job.getJobId(),changedFuture.getTaskNode());
                    cancelledTasks.add(changedFuture);
                    throw new CancellationException();
                default:
                    SpliceLogUtils.trace(LOG,"[%s] Task %s is in state %s",job.getJobId(),changedFuture.getTaskNode(),status);
            }
        }

        //update our job metrics
        Status finalStatus = getStatus();
        jobMetrics.jobFinished(finalStatus);

        SpliceLogUtils.trace(LOG,"completeNext finished");
    }

    @Override
    public void cancel() throws ExecutionException {
        throw new UnsupportedOperationException("Currently unsupported. Implementation needed");
    }

    @Override
    public double getEstimatedCost() throws ExecutionException {
        double maxCost = 0d;
        for(TaskFuture future:tasksToWatch){
            if(maxCost < future.getEstimatedCost()){
                maxCost = future.getEstimatedCost();
            }
        }
        return maxCost;
    }

    @Override
    public JobStats getJobStats() {
        return stats;
    }

    @Override
    public void cleanup() throws ExecutionException {
        SpliceLogUtils.trace(LOG, "cleaning up job %s", job.getJobId());
        try {
            zkManager.execute(new SpliceZooKeeperManager.Command<Void>() {
                @Override
                public Void execute(RecoverableZooKeeper zooKeeper) throws InterruptedException, KeeperException {
                    try{
                        zooKeeper.delete(CoprocessorTaskScheduler.getJobPath()+"/"+job.getJobId(),-1);
                    }catch(KeeperException ke){
                        if(ke.code()!= KeeperException.Code.NONODE)
                            throw ke;
                    }

                    for(RegionTaskControl task:tasksToWatch){
                        try{
                            zooKeeper.delete(task.getTaskNode(),-1);
                        }catch(KeeperException ke){
                            if(ke.code()!= KeeperException.Code.NONODE)
                                throw ke;
                        }
                    }
                    return null;
                }
            });
        } catch (InterruptedException e) {
            throw new ExecutionException(e);
        } catch (KeeperException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public int getNumTasks() {
        return tasksToWatch.size();
    }

    @Override
    public int getRemainingTasks() {
        return tasksToWatch.size()-completedTasks.size()-failedTasks.size()-cancelledTasks.size();
    }

    void taskChanged(RegionTaskControl taskControl){
        this.changedTasks.add(taskControl);
    }

    public void markInvalid(RegionTaskControl regionTaskControl) {
        stats.invalidTaskCount.incrementAndGet();
    }

    void resubmit(RegionTaskControl task,
                  int tryCount) throws ExecutionException {
        //only submit so many times
        if(tryCount>=maxResubmissionAttempts){
            ExecutionException ee = new ExecutionException(
                    new AttemptsExhaustedException("Unable to complete task "+ task.getTaskNode()+", it was invalidated more than "+ maxResubmissionAttempts+" times"));
            task.fail(ee.getCause());
            throw ee;
        }

        //get the next higher task
        RegionTaskControl next = task;
        do{
            next = tasksToWatch.higher(next);
        }while(next!=null && next.compareTo(task)==0);

        tasksToWatch.remove(task);

        byte[] endRow;
        byte[] start = task.getStartRow();
        if(next!=null){
            byte[] nextStart = next.getStartRow();
            endRow = new byte[nextStart.length];
            System.arraycopy(nextStart,0,endRow,0,endRow.length);

            BytesUtil.unsignedDecrement(endRow, endRow.length - 1);
        }else
            endRow = HConstants.EMPTY_END_ROW;

        try{
            Pair<RegionTask,Pair<byte[],byte[]>> newTaskData = job.resubmitTask(task.getTask(), start, endRow);
            if (LOG.isTraceEnabled())
                SpliceLogUtils.trace(LOG, "executing submit on resubmitted job %s", job.getJobId());
            //resubmit the task
            submit(newTaskData.getFirst(), newTaskData.getSecond(), job.getTable(),tryCount + 1);
        }catch(IOException ioe){
            throw new ExecutionException(ioe);
        }
    }

    void submit(final RegionTask task,
                        Pair<byte[], byte[]> range,
                        HTableInterface table,
                        final int tryCount) throws ExecutionException {
        byte[] start = range.getFirst();
        byte[] stop = range.getSecond();

        try{
            table.coprocessorExec(SpliceSchedulerProtocol.class,start,stop,
                    new Batch.Call<SpliceSchedulerProtocol, TaskFutureContext>() {
                @Override
                public TaskFutureContext call(SpliceSchedulerProtocol instance) throws IOException {
                    return instance.submit(task);
                }
            }, new Batch.Callback<TaskFutureContext>() {
                        @Override
                        public void update(byte[] region, byte[] row, TaskFutureContext result) {
                            RegionTaskControl control = new RegionTaskControl(row,task,JobControl.this,result,tryCount,zkManager);
                            tasksToWatch.add(control);
                            taskChanged(control);
                        }
                    });
        }catch (Throwable throwable) {
            throw new ExecutionException(throwable);
        }
    }

    void fail(RegionTaskControl task) {
        failedTasks.add(task);
        changedTasks.add(task); //force the error to propagate
    }
}
