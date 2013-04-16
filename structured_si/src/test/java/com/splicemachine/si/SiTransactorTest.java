package com.splicemachine.si;

import com.splicemachine.si.data.api.SDataLib;
import com.splicemachine.si.data.api.SGet;
import com.splicemachine.si.data.api.SScan;
import com.splicemachine.si.data.api.STable;
import com.splicemachine.si.data.api.STableReader;
import com.splicemachine.si.api.FilterState;
import com.splicemachine.si.api.TransactionId;
import com.splicemachine.si.api.Transactor;
import com.splicemachine.si.impl.SiFilterState;
import com.splicemachine.si.impl.Transaction;
import org.apache.hadoop.hbase.filter.Filter;
import org.junit.Assert;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SiTransactorTest {
    boolean useSimple = true;

    StoreSetup storeSetup;
    TransactorSetup transactorSetup;
    Transactor transactor;

    void baseSetUp() {
        transactor = transactorSetup.transactor;
    }

    @Before
    public void setUp() {
        storeSetup = new LStoreSetup();
        transactorSetup = new TransactorSetup(storeSetup);
        baseSetUp();
    }

    @After
    public void tearDown() throws Exception {
    }

    private void insertAge(TransactionId transactionId, String name, int age) throws IOException {
        insertAgeDirect(useSimple, transactorSetup, storeSetup, transactionId, name, age);
    }

    private void insertJob(TransactionId transactionId, String name, String job) throws IOException {
        insertJobDirect(useSimple, transactorSetup, storeSetup, transactionId, name, job);
    }

    private void deleteRow(TransactionId transactionId, String name) throws IOException {
        deleteRowDirect(useSimple, transactorSetup, storeSetup, transactionId, name);
    }

    private String read(TransactionId transactionId, String name) throws IOException {
        return readAgeDirect(useSimple, transactorSetup, storeSetup, transactionId, name);
    }

    private String scan(TransactionId transactionId, String name) throws IOException {
        return scanAgeDirect(useSimple, transactorSetup, storeSetup, transactionId, name);
    }

    private String scanAll(TransactionId transactionId, String startKey, String stopKey) throws IOException {
        return scanAllDirect(useSimple, transactorSetup, storeSetup, transactionId, startKey, stopKey);
    }

    static void insertAgeDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String name, int age) throws IOException {
        insertField(useSimple, transactorSetup, storeSetup, transactionId, name, transactorSetup.ageQualifier, age);
    }

    static void insertJobDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String name, String job) throws IOException {
        insertField(useSimple, transactorSetup, storeSetup, transactionId, name, transactorSetup.jobQualifier, job);
    }

    private static void insertField(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                    TransactionId transactionId, String name, Object qualifier, Object fieldValue)
            throws IOException {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        Object key = dataLib.newRowKey(new Object[]{name});
        Object put = dataLib.newPut(key);
        dataLib.addKeyValueToPut(put, transactorSetup.family, qualifier, null, dataLib.encode(fieldValue));
        transactorSetup.clientTransactor.initializePut(transactionId.getTransactionIdString(), put);

        processPutDirect(useSimple, transactorSetup, storeSetup, reader, put);
    }

    static void deleteRowDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String name) throws IOException {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        Object key = dataLib.newRowKey(new Object[]{name});
        Object deletePut = transactorSetup.clientTransactor.newDeletePut(transactionId, key);
        processPutDirect(useSimple, transactorSetup, storeSetup, reader, deletePut);
    }

    private static void processPutDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup, STableReader reader, Object put) throws IOException {
        STable testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            if (useSimple) {
                try {
                    Assert.assertTrue(transactorSetup.transactor.processPut(testSTable, put));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                storeSetup.getWriter().write(testSTable, put);
            }
        } finally {
            reader.close(testSTable);
        }
    }

    static String readAgeDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String name) throws IOException {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        Object key = dataLib.newRowKey(new Object[]{name});
        SGet get = dataLib.newGet(key, null, null, null);
        transactorSetup.clientTransactor.initializeGet(transactionId.getTransactionIdString(), get);
        STable testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            Object rawTuple = reader.get(testSTable, get);
            return readRawTuple(useSimple, storeSetup, transactorSetup, transactionId, name, dataLib, testSTable,
                    rawTuple, true);
        } finally {
            reader.close(testSTable);
        }
    }

    static String scanAgeDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String name) throws IOException {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        Object key = dataLib.newRowKey(new Object[]{name});
        SScan get = dataLib.newScan(key, key, null, null, null);
        transactorSetup.clientTransactor.initializeScan(transactionId.getTransactionIdString(), get);
        STable testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            Iterator results = reader.scan(testSTable, get);
            Assert.assertTrue(results.hasNext());
            Object rawTuple = results.next();
            Assert.assertTrue(!results.hasNext());
            return readRawTuple(useSimple, storeSetup, transactorSetup, transactionId, name, dataLib, testSTable,
                    rawTuple, false);
        } finally {
            reader.close(testSTable);
        }
    }

    static String scanAllDirect(boolean useSimple, TransactorSetup transactorSetup, StoreSetup storeSetup,
                                TransactionId transactionId, String startKey, String stopKey) throws IOException {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        Object key = dataLib.newRowKey(new Object[]{startKey});
        Object endKey = dataLib.newRowKey(new Object[]{stopKey});
        SScan get = dataLib.newScan(key, endKey, null, null, null);
        transactorSetup.clientTransactor.initializeScan(transactionId.getTransactionIdString(), get);
        STable testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            Iterator results = reader.scan(testSTable, get);
            StringBuilder result = new StringBuilder();
            while (results.hasNext()) {
                final Object value = results.next();
                final String name = (String) dataLib.decode(dataLib.getResultKey(value), String.class);
                final String s = readRawTuple(useSimple, storeSetup, transactorSetup, transactionId, name, dataLib, testSTable,
                        value, false);
                result.append(s);
                if (s.length() > 0) {
                    result.append("\n");
                }
            }
            return result.toString();
        } finally {
            reader.close(testSTable);
        }
    }

    private static String readRawTuple(boolean useSimple, StoreSetup storeSetup, TransactorSetup transactorSetup,
                                       TransactionId transactionId, String name, SDataLib dataLib, STable testSTable,
                                       Object rawTuple, boolean singleRowRead) throws IOException {
        if (rawTuple != null) {
            Object result = rawTuple;
            if (useSimple) {
                final FilterState filterState;
                try {
                    filterState = transactorSetup.transactor.newFilterState(testSTable, transactionId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                result = filterResult(storeSetup, transactorSetup, filterState, rawTuple);
            }
            if (result != null) {
                final Object ageValue = dataLib.getResultValue(result, transactorSetup.family, transactorSetup.ageQualifier);
                Integer age = (Integer) dataLib.decode(ageValue, Integer.class);
                final Object jobValue = dataLib.getResultValue(result, transactorSetup.family, transactorSetup.jobQualifier);
                String job = (String) dataLib.decode(jobValue, String.class);
                return name + " age=" + age + " job=" + job;
            }
        }
        if (singleRowRead) {
            return name + " age=" + null + " job=" + null;
        } else {
            return "";
        }
    }

    private static Object filterResult(StoreSetup storeSetup, TransactorSetup transactorSetup,
                                       FilterState filterState, Object result) throws IOException {
        SiFilterState siFilterState = (SiFilterState) filterState;
        //ensureTransactionActive(siFilterState.transactionId);

        final SDataLib dataLib = storeSetup.getDataLib();
        List<Object> filteredCells = new ArrayList<Object>();
        final List keyValues = dataLib.listResult(result);
        if (keyValues != null) {
            Object qualifierToSkip = null;
            Object familyToSkip = null;

            for (Object keyValue : keyValues) {
                if (familyToSkip != null
                        && dataLib.valuesEqual(familyToSkip, dataLib.getKeyValueFamily(keyValue))
                        && dataLib.valuesEqual(qualifierToSkip, dataLib.getKeyValueQualifier(keyValue))) {
                    // skipping to next column
                } else {
                    familyToSkip = null;
                    qualifierToSkip = null;
                    Filter.ReturnCode returnCode = transactorSetup.transactor.filterKeyValue(filterState, keyValue);
                    switch (returnCode) {
                        case SKIP:
                            break;
                        case INCLUDE:
                            filteredCells.add(keyValue);
                            qualifierToSkip = dataLib.getKeyValueQualifier(keyValue);
                            familyToSkip = dataLib.getKeyValueFamily(keyValue);
                            break;
                        case NEXT_COL:
                            qualifierToSkip = dataLib.getKeyValueQualifier(keyValue);
                            familyToSkip = dataLib.getKeyValueFamily(keyValue);
                            break;
                    }
                }
            }
        }
        if (filteredCells.isEmpty()) {
            return null;
        } else {
            return dataLib.newResult(dataLib.getResultKey(result), filteredCells);
        }
    }

    private void dumpStore() {
        dumpStore("");
    }

    private void dumpStore(String label) {
        if (useSimple) {
            System.out.println("store " + label + " =" + storeSetup.getStore());
        }
    }

    @Test
    public void writeRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe9 age=null job=null", read(t1, "joe9"));
        insertAge(t1, "joe9", 20);
        Assert.assertEquals("joe9 age=20 job=null", read(t1, "joe9"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe9 age=20 job=null", read(t2, "joe9"));
    }

    @Test
    public void writeReadOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe8 age=null job=null", read(t1, "joe8"));
        insertAge(t1, "joe8", 20);
        Assert.assertEquals("joe8 age=20 job=null", read(t1, "joe8"));

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe8 age=20 job=null", read(t1, "joe8"));
        Assert.assertEquals("joe8 age=null job=null", read(t2, "joe8"));
        transactor.commit(t1);
        Assert.assertEquals("joe8 age=null job=null", read(t2, "joe8"));
    }

    @Test
    public void writeWrite() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe", 20);
        Assert.assertEquals("joe age=20 job=null", read(t1, "joe"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe age=20 job=null", read(t2, "joe"));
        insertAge(t2, "joe", 30);
        Assert.assertEquals("joe age=30 job=null", read(t2, "joe"));
        transactor.commit(t2);
    }

    @Test
    public void writeWriteOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe2 age=null job=null", read(t1, "joe2"));
        insertAge(t1, "joe2", 20);
        Assert.assertEquals("joe2 age=20 job=null", read(t1, "joe2"));

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe2 age=20 job=null", read(t1, "joe2"));
        Assert.assertEquals("joe2 age=null job=null", read(t2, "joe2"));
        try {
            insertAge(t2, "joe2", 30);
            Assert.fail();
        } catch (RuntimeException e) {
            // TODO: expected write/write conflict
            //DoNotRetryIOException dnrio = (DoNotRetryIOException) e.getCause();
            //Assert.assertTrue(dnrio.getMessage().indexOf("write/write conflict") >= 0);
        }
        Assert.assertEquals("joe2 age=20 job=null", read(t1, "joe2"));
        transactor.commit(t1);
        try {
            transactor.commit(t2);
            Assert.fail();
        } catch (DoNotRetryIOException dnrio) {
            Assert.assertTrue(dnrio.getMessage().startsWith("transaction is not ACTIVE"));
        }
    }

    @Test
    public void readAfterCommit() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe3", 20);
        transactor.commit(t1);
        Assert.assertEquals("joe3 age=20 job=null", read(t1, "joe3"));
    }

    @Test
    public void writeScan() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe4 age=null job=null", read(t1, "joe4"));
        insertAge(t1, "joe4", 20);
        Assert.assertEquals("joe4 age=20 job=null", read(t1, "joe4"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe4 age=20 job=null", scan(t2, "joe4"));

        Assert.assertEquals("joe4 age=20 job=null", read(t2, "joe4"));
    }

    @Test
    public void writeScanMultipleRows() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "17joe", 20);
        insertAge(t1, "17bob", 30);
        insertAge(t1, "17boe", 40);
        insertAge(t1, "17tom", 50);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        String expected = "17bob age=30 job=null\n" +
                "17boe age=40 job=null\n" +
                "17joe age=20 job=null\n" +
                "17tom age=50 job=null\n";
        Assert.assertEquals(expected, scanAll(t2, "17a", "17z"));
    }

    @Test
    public void writeWriteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe5", 20);
        Assert.assertEquals("joe5 age=20 job=null", read(t1, "joe5"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe5 age=20 job=null", read(t2, "joe5"));
        insertJob(t2, "joe5", "baker");
        Assert.assertEquals("joe5 age=20 job=baker", read(t2, "joe5"));
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe5 age=20 job=baker", read(t3, "joe5"));
    }

    @Test
    public void multipleWritesSameTransaction() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe16 age=null job=null", read(t1, "joe16"));
        insertAge(t1, "joe16", 20);
        Assert.assertEquals("joe16 age=20 job=null", read(t1, "joe16"));

        insertAge(t1, "joe16", 21);
        Assert.assertEquals("joe16 age=21 job=null", read(t1, "joe16"));

        insertAge(t1, "joe16", 22);
        Assert.assertEquals("joe16 age=22 job=null", read(t1, "joe16"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe16 age=22 job=null", read(t2, "joe16"));
        transactor.commit(t2);
    }

    @Test
    public void manyWritesManyRollbacksRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe6", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        insertJob(t2, "joe6", "baker");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        insertJob(t3, "joe6", "butcher");
        transactor.commit(t3);

        TransactionId t4 = transactor.beginTransaction(true, false, false);
        insertJob(t4, "joe6", "blacksmith");
        transactor.commit(t4);

        TransactionId t5 = transactor.beginTransaction(true, false, false);
        insertJob(t5, "joe6", "carter");
        transactor.commit(t5);

        TransactionId t6 = transactor.beginTransaction(true, false, false);
        insertJob(t6, "joe6", "farrier");
        transactor.commit(t6);

        TransactionId t7 = transactor.beginTransaction(true, false, false);
        insertAge(t7, "joe6", 27);
        transactor.rollback(t7);

        TransactionId t8 = transactor.beginTransaction(true, false, false);
        insertAge(t8, "joe6", 28);
        transactor.rollback(t8);

        TransactionId t9 = transactor.beginTransaction(true, false, false);
        insertAge(t9, "joe6", 29);
        transactor.rollback(t9);

        TransactionId t10 = transactor.beginTransaction(true, false, false);
        insertAge(t10, "joe6", 30);
        transactor.rollback(t10);

        TransactionId t11 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe6 age=20 job=farrier", read(t11, "joe6"));
    }

    @Test
    public void writeDelete() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe10", 20);
        Assert.assertEquals("joe10 age=20 job=null", read(t1, "joe10"));
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe10 age=20 job=null", read(t2, "joe10"));
        deleteRow(t2, "joe10");
        Assert.assertEquals("joe10 age=null job=null", read(t2, "joe10"));
        transactor.commit(t2);
    }

    @Test
    public void writeDeleteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe11", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        deleteRow(t2, "joe11");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe11 age=null job=null", read(t3, "joe11"));
        transactor.commit(t3);
    }

    @Test
    public void writeDeleteOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe12", 20);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        try {
            deleteRow(t2, "joe12");
            Assert.fail();
        } catch (RuntimeException e) {
            // TODO: expected write/write conflict
            //DoNotRetryIOException dnrio = (DoNotRetryIOException) e.getCause();
            //Assert.assertTrue(dnrio.getMessage().indexOf("write/write conflict") >= 0);
        }
        Assert.assertEquals("joe12 age=20 job=null", read(t1, "joe12"));
        Assert.assertEquals("joe12 age=20 job=null", read(t1, "joe12"));
        transactor.commit(t1);
        try {
            transactor.commit(t2);
            Assert.fail();
        } catch (DoNotRetryIOException dnrio) {
            Assert.assertTrue(dnrio.getMessage().startsWith("transaction is not ACTIVE"));
        }
    }

    @Test
    public void writeWriteDeleteOverlap() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "jo13", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);
        deleteRow(t1, "joe13");

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        try {
            insertAge(t2, "joe13", 21);
            Assert.fail();
        } catch (RuntimeException e) {
            // TODO: expected write/write conflict
            //DoNotRetryIOException dnrio = (DoNotRetryIOException) e.getCause();
            //Assert.assertTrue(dnrio.getMessage().indexOf("write/write conflict") >= 0);
        }
        Assert.assertEquals("joe13 age=null job=null", read(t1, "joe13"));
        transactor.commit(t1);
        try {
            transactor.commit(t2);
            Assert.fail();
        } catch (DoNotRetryIOException dnrio) {
            Assert.assertTrue(dnrio.getMessage().startsWith("transaction is not ACTIVE"));
        }

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe13 age=null job=null", read(t3, "joe13"));
        transactor.commit(t3);
    }

    @Test
    public void writeWriteDeleteWriteRead() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "joe14", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertJob(t1, "joe14", "baker");
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        deleteRow(t2, "joe14");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        insertJob(t3, "joe14", "smith");
        Assert.assertEquals("joe14 age=null job=smith", read(t3, "joe14"));
        transactor.commit(t3);

        TransactionId t4 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe14 age=null job=smith", read(t4, "joe14"));
        transactor.commit(t4);
    }

    @Test
    public void writeWriteDeleteWriteDeleteWriteRead() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "joe15", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertJob(t1, "joe15", "baker");
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        deleteRow(t2, "joe15");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        insertJob(t3, "joe15", "smith");
        Assert.assertEquals("joe15 age=null job=smith", read(t3, "joe15"));
        transactor.commit(t3);

        TransactionId t4 = transactor.beginTransaction(true, false, false);
        deleteRow(t4, "joe15");
        transactor.commit(t4);

        TransactionId t5 = transactor.beginTransaction(true, false, false);
        insertAge(t5, "joe15", 21);
        transactor.commit(t5);

        TransactionId t6 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe15 age=21 job=null", read(t6, "joe15"));
        transactor.commit(t6);
    }

    @Test
    public void writeManyDeleteOneGets() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe47", 20);
        insertAge(t1, "toe47", 30);
        insertAge(t1, "boe47", 40);
        insertAge(t1, "moe47", 50);
        insertAge(t1, "zoe47", 60);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        deleteRow(t2, "moe47");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe47 age=20 job=null", read(t3, "joe47"));
        Assert.assertEquals("toe47 age=30 job=null", read(t3, "toe47"));
        Assert.assertEquals("boe47 age=40 job=null", read(t3, "boe47"));
        Assert.assertEquals("moe47 age=null job=null", read(t3, "moe47"));
        Assert.assertEquals("zoe47 age=60 job=null", read(t3, "zoe47"));
    }

    @Test
    public void writeManyDeleteOneScan() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "48joe", 20);
        insertAge(t1, "48toe", 30);
        insertAge(t1, "48boe", 40);
        insertAge(t1, "48moe", 50);
        insertAge(t1, "48xoe", 60);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        deleteRow(t2, "48moe");
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        String expected = "48boe age=40 job=null\n" +
                "48joe age=20 job=null\n" +
                "48toe age=30 job=null\n" +
                "48xoe age=60 job=null\n";
        Assert.assertEquals(expected, scanAll(t3, "48a", "48z"));
    }

    @Test
    public void fourTransactions() throws Exception {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe7", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe7 age=20 job=null", read(t2, "joe7"));
        insertAge(t2, "joe7", 30);
        Assert.assertEquals("joe7 age=30 job=null", read(t2, "joe7"));

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe7 age=20 job=null", read(t3, "joe7"));

        transactor.commit(t2);

        TransactionId t4 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe7 age=30 job=null", read(t4, "joe7"));
        //System.out.println(store);
    }

    @Test
    public void writeReadOnly() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe18", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe18 age=20 job=null", read(t2, "joe18"));
        try {
            insertAge(t2, "joe18", 21);
            Assert.fail("expected exception performing a write on a read-only transaction");
        } catch (RuntimeException e) {
        }
    }

    @Test
    public void writeReadCommitted() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe19", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(false, false, true);
        Assert.assertEquals("joe19 age=20 job=null", read(t2, "joe19"));
    }

    @Test
    public void writeReadCommittedOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe20", 20);

        TransactionId t2 = transactor.beginTransaction(false, false, true);

        Assert.assertEquals("joe20 age=null job=null", read(t2, "joe20"));
        transactor.commit(t1);
        Assert.assertEquals("joe20 age=20 job=null", read(t2, "joe20"));
    }

    @Test
    public void writeReadDirty() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe22", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(false, true, true);
        Assert.assertEquals("joe22 age=20 job=null", read(t2, "joe22"));
    }

    @Test
    public void writeReadDirtyOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe21", 20);

        TransactionId t2 = transactor.beginTransaction(false, true, true);

        Assert.assertEquals("joe21 age=20 job=null", read(t2, "joe21"));
        transactor.commit(t1);
        Assert.assertEquals("joe21 age=20 job=null", read(t2, "joe21"));
    }

    @Test
    public void writeRollbackWriteReadDirtyOverlap() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe23", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        insertAge(t2, "joe23", 21);

        TransactionId t3 = transactor.beginTransaction(false, true, true);
        Assert.assertEquals("joe23 age=21 job=null", read(t3, "joe23"));

        transactor.rollback(t2);
        Assert.assertEquals("joe23 age=20 job=null", read(t3, "joe23"));

        TransactionId t4 = transactor.beginTransaction(true, false, false);
        insertAge(t4, "joe23", 22);
        Assert.assertEquals("joe23 age=22 job=null", read(t3, "joe23"));
    }

    @Test
    public void childDependentTransactionWriteRollbackRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe24", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe24", 21);
        Assert.assertEquals("joe24 age=21 job=null", read(t1, "joe24"));
        transactor.rollback(t2);
        Assert.assertEquals("joe24 age=20 job=null", read(t1, "joe24"));
        transactor.commit(t1);

        TransactionId t3 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe24 age=20 job=null", read(t3, "joe24"));
    }

    @Test
    public void childDependentSeesParentWrites() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe40", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        Assert.assertEquals("joe40 age=20 job=null", read(t2, "joe40"));
    }

    @Test
    public void childIndependentSeesParentWrites() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe41", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        Assert.assertEquals("joe41 age=20 job=null", read(t2, "joe41"));
    }

    @Test
    public void childDependentTransactionWriteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe25", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe25", 21);
        Assert.assertEquals("joe25 age=21 job=null", read(t1, "joe25"));
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe25 age=null job=null", read(t3, "joe25"));

        Assert.assertEquals("joe25 age=21 job=null", read(t1, "joe25"));
        transactor.commit(t1);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe25 age=21 job=null", read(t4, "joe25"));
    }

    @Test
    public void childDependentTransactionWithOtherCommitBetweenParentAndChild() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "joe37", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);

        TransactionId otherTransaction = transactor.beginTransaction(true, false, false);
        insertAge(otherTransaction, "joe37", 30);
        transactor.commit(otherTransaction);

        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        Assert.assertEquals("joe37 age=20 job=null", read(t2, "joe37"));
        transactor.commit(t2);
        transactor.commit(t1);
    }

    @Test
    public void childIndependentTransactionWithOtherCommitBetweenParentAndChild() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "joe38", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);

        TransactionId otherTransaction = transactor.beginTransaction(true, false, false);
        insertAge(otherTransaction, "joe38", 30);
        transactor.commit(otherTransaction);

        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, true);
        Assert.assertEquals("joe38 age=30 job=null", read(t2, "joe38"));
        transactor.commit(t2);
        transactor.commit(t1);
    }

    @Test
    public void childIndependentTransactionWithReadCommittedOffWithOtherCommitBetweenParentAndChild() throws IOException {
        TransactionId t0 = transactor.beginTransaction(true, false, false);
        insertAge(t0, "joe39", 20);
        transactor.commit(t0);

        TransactionId t1 = transactor.beginTransaction(true, false, false);

        TransactionId otherTransaction = transactor.beginTransaction(true, false, false);
        insertAge(otherTransaction, "joe39", 30);
        transactor.commit(otherTransaction);

        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        Assert.assertEquals("joe39 age=20 job=null", read(t2, "joe39"));
        transactor.commit(t2);
        transactor.commit(t1);
    }

    @Test
    public void multipleChildDependentTransactionWriteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe26", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        TransactionId t3 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe26", 21);
        insertJob(t3, "joe26", "baker");
        Assert.assertEquals("joe26 age=21 job=baker", read(t1, "joe26"));
        transactor.commit(t2);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe26 age=null job=null", read(t4, "joe26"));

        transactor.commit(t3);

        TransactionId t5 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe26 age=null job=null", read(t5, "joe26"));

        Assert.assertEquals("joe26 age=21 job=baker", read(t1, "joe26"));
        transactor.commit(t1);

        TransactionId t6 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe26 age=21 job=baker", read(t6, "joe26"));
    }

    @Test
    public void multipleChildDependentTransactionsRollbackThenWrite() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe45", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe45", 21);
        TransactionId t3 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertJob(t3, "joe45", "baker");
        Assert.assertEquals("joe45 age=21 job=baker", read(t1, "joe45"));
        Assert.assertEquals("joe45 age=21 job=baker", read(t2, "joe45"));
        Assert.assertEquals("joe45 age=21 job=baker", read(t3, "joe45"));
        transactor.rollback(t2);
        Assert.assertEquals("joe45 age=20 job=baker", read(t1, "joe45"));
        Assert.assertEquals("joe45 age=20 job=baker", read(t3, "joe45"));
        transactor.rollback(t3);
        Assert.assertEquals("joe45 age=20 job=null", read(t1, "joe45"));
        TransactionId t4 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t4, "joe45", 24);
        Assert.assertEquals("joe45 age=24 job=null", read(t1, "joe45"));
        Assert.assertEquals("joe45 age=24 job=null", read(t4, "joe45"));
        transactor.commit(t4);
        Assert.assertEquals("joe45 age=24 job=null", read(t1, "joe45"));
        Assert.assertEquals("joe45 age=24 job=null", read(t4, "joe45"));

        TransactionId t5 = transactor.beginTransaction(false, false, true);
        Assert.assertEquals("joe45 age=null job=null", read(t5, "joe45"));
        transactor.commit(t1);
        Assert.assertEquals("joe45 age=24 job=null", read(t5, "joe45"));
    }

    @Test
    public void multipleChildCommitParentRollback() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe46", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertJob(t2, "joe46", "baker");
        transactor.commit(t2);
        transactor.rollback(t1);
        TransactionId t3 = transactor.beginChildTransaction(t1, true, true, null, null);
        Assert.assertEquals("joe46 age=null job=null", read(t3, "joe46"));
    }

    @Test
    public void childDependentTransactionWriteRollbackParentRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe27", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe27", 21);
        transactor.commit(t2);
        transactor.rollback(t1);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe27 age=null job=null", read(t4, "joe27"));
    }

    @Test
    public void childIndependentTransactionWriteRollbackRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe28", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        insertAge(t2, "joe28", 21);
        Assert.assertEquals("joe28 age=21 job=null", read(t1, "joe28"));
        transactor.rollback(t2);
        Assert.assertEquals("joe28 age=20 job=null", read(t1, "joe28"));
        transactor.commit(t1);

        TransactionId t3 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe28 age=20 job=null", read(t3, "joe28"));
    }

    @Test
    public void multipleChildIndependentTransactionWriteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe31", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        TransactionId t3 = transactor.beginChildTransaction(t1, false, true, null, null);
        insertAge(t2, "joe31", 21);
        insertJob(t3, "joe31", "baker");
        Assert.assertEquals("joe31 age=21 job=baker", read(t1, "joe31"));
        transactor.commit(t2);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe31 age=21 job=null", read(t4, "joe31"));

        transactor.commit(t3);

        TransactionId t5 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe31 age=21 job=baker", read(t5, "joe31"));

        Assert.assertEquals("joe31 age=21 job=baker", read(t1, "joe31"));
        transactor.commit(t1);

        TransactionId t6 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe31 age=21 job=baker", read(t6, "joe31"));
    }

    @Test
    public void childIndependentTransactionWriteRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe29", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        insertAge(t2, "joe29", 21);
        Assert.assertEquals("joe29 age=21 job=null", read(t1, "joe29"));
        transactor.commit(t2);

        TransactionId t3 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe29 age=21 job=null", read(t3, "joe29"));

        Assert.assertEquals("joe29 age=21 job=null", read(t1, "joe29"));
        transactor.commit(t1);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe29 age=21 job=null", read(t4, "joe29"));
    }

    @Test
    public void childIndependentTransactionWriteRollbackParentRead() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe30", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        insertAge(t2, "joe30", 21);
        transactor.commit(t2);
        transactor.rollback(t1);

        TransactionId t4 = transactor.beginTransaction(false, false, false);
        Assert.assertEquals("joe30 age=21 job=null", read(t4, "joe30"));
    }

    @Test
    public void commitParentOfCommittedDependent() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe32", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, true, true, null, null);
        insertAge(t2, "joe32", 21);
        transactor.commit(t2);
        final Transaction transactionStatusA = transactorSetup.transactionStore.getTransactionStatus(t2);
        Assert.assertNull("committing a dependent child does not set a commit timestamp", transactionStatusA.commitTimestamp);
        transactor.commit(t1);
        final Transaction transactionStatusB = transactorSetup.transactionStore.getTransactionStatus(t2);
        Assert.assertNotNull("committing parent of dependent transaction should set the commit time of the child",
                transactionStatusB.commitTimestamp);
    }

    @Test
    public void commitParentOfCommittedIndependent() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe49", 20);
        TransactionId t2 = transactor.beginChildTransaction(t1, false, true, null, null);
        insertAge(t2, "joe49", 21);
        transactor.commit(t2);
        final Transaction transactionStatusA = transactorSetup.transactionStore.getTransactionStatus(t2);
        transactor.commit(t1);
        final Transaction transactionStatusB = transactorSetup.transactionStore.getTransactionStatus(t2);
        Assert.assertEquals("committing parent of independent transaction should not change the commit time of the child",
                transactionStatusA.commitTimestamp, transactionStatusB.commitTimestamp);
    }

    @Test
    public void independentWriteOverlapWithReadCommittedWriter() throws IOException {
        TransactionId parent = transactor.beginTransaction(true, false, false);

        TransactionId other = transactor.beginTransaction(true, false, true);

        TransactionId child = transactor.beginChildTransaction(parent, false, true, null, null);
        insertAge(child, "joe33", 22);
        transactor.commit(child);

        try {
            // TODO: make this test pass, writes should be allowed on top of committed transactions in READ_COMMITTED mode
            insertAge(other, "joe33", 21);
            Assert.fail();
        } catch (RuntimeException e) {
            // TODO: expected write/write conflict
            //DoNotRetryIOException dnrio = (DoNotRetryIOException) e.getCause();
            //Assert.assertTrue(dnrio.getMessage().indexOf("write/write conflict") >= 0);
        }
    }

    @Test
    public void dependentWriteFollowedByReadCommittedWriter() throws IOException {
        TransactionId parent = transactor.beginTransaction(true, false, false);

        TransactionId child = transactor.beginChildTransaction(parent, false, true, null, null);
        insertAge(child, "joe34", 22);
        transactor.commit(child);

        TransactionId other = transactor.beginTransaction(true, false, true);
        insertAge(other, "joe34", 21);
        transactor.commit(other);
    }

    @Test
    public void independentWriteFollowedByReadCommittedWriter() throws IOException {
        TransactionId parent = transactor.beginTransaction(true, false, false);

        TransactionId child = transactor.beginChildTransaction(parent, false, true, null, null);
        insertAge(child, "joe35", 22);
        transactor.commit(child);

        TransactionId other = transactor.beginTransaction(true, false, true);
        insertAge(other, "joe35", 21);
        transactor.commit(other);
    }

    @Test
    public void dependentWriteOverlapWithReadCommittedWriter() throws IOException {
        TransactionId parent = transactor.beginTransaction(true, false, false);

        TransactionId other = transactor.beginTransaction(true, false, true);

        TransactionId child = transactor.beginChildTransaction(parent, false, true, null, null);
        insertAge(child, "joe36", 22);
        transactor.commit(child);

        try {
            insertAge(other, "joe36", 21);
            Assert.fail();
        } catch (RuntimeException e) {
            // TODO: expected write/write conflict
            //DoNotRetryIOException dnrio = (DoNotRetryIOException) e.getCause();
            //Assert.assertTrue(dnrio.getMessage().indexOf("write/write conflict") >= 0);
        }
    }

    @Test
    public void rollbackUpdate() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe43", 20);
        transactor.commit(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        insertAge(t2, "joe43", 21);
        transactor.rollback(t2);

        TransactionId t3 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe43 age=20 job=null", read(t3, "joe43"));
    }

    @Test
    public void rollbackInsert() throws IOException {
        TransactionId t1 = transactor.beginTransaction(true, false, false);
        insertAge(t1, "joe44", 20);
        transactor.rollback(t1);

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        Assert.assertEquals("joe44 age=null job=null", read(t2, "joe44"));
    }

    @Test
    public void readWriteMechanics() throws Exception {
        final SDataLib dataLib = storeSetup.getDataLib();
        final STableReader reader = storeSetup.getReader();

        final Object testKey = dataLib.newRowKey(new Object[]{"jim"});
        Object put = dataLib.newPut(testKey);
        Object family = dataLib.encode("attributes");
        Object ageQualifier = dataLib.encode("age");
        dataLib.addKeyValueToPut(put, family, ageQualifier, null, dataLib.encode(25));
        TransactionId t = transactor.beginTransaction(true, false, false);
        transactorSetup.clientTransactor.initializePut(t.getTransactionIdString(), put);
        Object put2 = dataLib.newPut(testKey);
        dataLib.addKeyValueToPut(put2, family, ageQualifier, null, dataLib.encode(27));
        transactorSetup.clientTransactor.initializePut(
                transactorSetup.clientTransactor.getTransactionIdFromPut(put).getTransactionIdString(),
                put2);
        Assert.assertTrue(dataLib.valuesEqual(dataLib.encode(true), dataLib.getAttribute(put2, "si-needed")));
        STable testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            Assert.assertTrue(transactor.processPut(testSTable, put));
            Assert.assertTrue(transactor.processPut(testSTable, put2));
            SGet get1 = dataLib.newGet(testKey, null, null, null);
            transactorSetup.clientTransactor.initializeGet(t.getTransactionIdString(), get1);
            Object result = reader.get(testSTable, get1);
            result = filterResult(storeSetup, transactorSetup, transactor.newFilterState(testSTable, t), result);
            final int ageRead = (Integer) dataLib.decode(dataLib.getResultValue(result, family, ageQualifier), Integer.class);
            Assert.assertEquals(27, ageRead);
        } finally {
            reader.close(testSTable);
        }

        TransactionId t2 = transactor.beginTransaction(true, false, false);
        SGet get = dataLib.newGet(testKey, null, null, null);
        testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            final Object resultTuple = reader.get(testSTable, get);
            for (Object keyValue : dataLib.listResult(resultTuple)) {
                //System.out.println(((SiTransactor) transactor).shouldKeep(keyValue, t2));
            }
            final FilterState filterState = transactor.newFilterState(testSTable, t2);
            filterResult(storeSetup, transactorSetup, filterState, resultTuple);
        } finally {
            reader.close(testSTable);
        }

        transactor.commit(t);

        t = transactor.beginTransaction(true, false, false);

        dataLib.addKeyValueToPut(put, family, ageQualifier, null, dataLib.encode(35));
        transactorSetup.clientTransactor.initializePut(t.getTransactionIdString(), put);
        testSTable = reader.open(storeSetup.getPersonTableName());
        try {
            Assert.assertTrue(transactor.processPut(testSTable, put));
        } finally {
            reader.close(testSTable);
        }

        //System.out.println("store2 = " + store);
    }
}
