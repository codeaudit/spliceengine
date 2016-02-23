package com.splicemachine.management;

import com.google.common.collect.Maps;
import com.splicemachine.derby.test.framework.SpliceNetConnection;
import com.splicemachine.derby.test.framework.SpliceXPlainTrace;
import com.splicemachine.derby.test.framework.TestConnection;
import org.junit.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.SortedMap;

/**
 * @author Scott Fines
 *         Date: 10/16/14
 */
public abstract class BaseXplainIT {
    protected SpliceXPlainTrace xPlainTrace = new SpliceXPlainTrace();
    protected static TestConnection baseConnection;
    protected long txnId;
    @BeforeClass
    public static void setUpClass() throws Exception {
        baseConnection = new TestConnection(SpliceNetConnection.getConnectionAs(SpliceNetConnection.DEFAULT_USER,SpliceNetConnection.DEFAULT_USER_PASSWORD));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if(baseConnection!=null) //put here in case there's a problem setting up the initial connection
            baseConnection.close();
    }

    @Before
    public void setUp() throws Exception {
        if(baseConnection==null || baseConnection.isClosed())
            baseConnection = getNewConnection();

        baseConnection.setAutoCommit(false);
        txnId = baseConnection.getCurrentTransactionId();
        xPlainTrace.setConnection(baseConnection);
    }

    @After
    public void tearDown() throws Exception {
        baseConnection.rollback();
    }

    protected abstract TestConnection getNewConnection() throws Exception;

    protected ResultSet getStatementsForTxn() throws SQLException {
        return baseConnection.query("select * from SYS.SYSSTATEMENTHISTORY where transactionid >= "+txnId);
    }

    protected long getLastStatementId() throws SQLException {
        //get the last statement id
        ResultSet statementLine = getStatementsForTxn();
        SortedMap<Long,Long> txnIdToStatement = Maps.newTreeMap();
        while(statementLine.next()){
            long statementId = statementLine.getLong("STATEMENTID");
            Assert.assertFalse("No statement id found!",statementLine.wasNull());
            long tId = statementLine.getLong("TRANSACTIONID");
            Assert.assertFalse("No transaction id found!",statementLine.wasNull());
            txnIdToStatement.put(tId,statementId);
        }
        Assert.assertFalse("Did not find a statementId for transaction "+ txnId,txnIdToStatement.isEmpty());
        return txnIdToStatement.get(txnIdToStatement.lastKey());
    }
}