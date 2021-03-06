/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.splicemachine.dbTesting.functionTests.tests.derbynet;

import java.sql.*;
import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestSuite;
import com.splicemachine.dbTesting.junit.BaseJDBCTestCase;
import com.splicemachine.dbTesting.junit.TestConfiguration;


/**
 *	This tests some bad attempts at a client connection:
 *		- non-existant database
 *		- lack of user / password attributes
 *		- bad values for valid connection attributes
 */

public class BadConnectionTest extends BaseJDBCTestCase
{
    public void setUp() throws SQLException
    {
        // get the default connection so the driver is loaded.
        getConnection().close();
    }

    /**
     * Try to connect without a username or password.
     * Should fail with SQLState 08004.
     */
    public void testNoUserOrPassword()
    {
        try {
            Connection c = DriverManager.getConnection(
                    "jdbc:splice://" + getTestConfiguration().getHostName()
                            + ":" + getTestConfiguration().getPort() + "/testbase");
            fail("Connection with no user or password succeeded");
        } catch (SQLException e) {
            assertSQLState("08004", e);
            assertEquals(40000, e.getErrorCode());
        }
    }

    /**
     * Try to connect to a non-existent database without create=true.
     * Should fail with SQLState 08004.
     */
    public void testDatabaseNotFound()
    {
        try {
            Properties p = new Properties();
            p.put("user", "admin");
            p.put("password", "admin");
            Connection c = DriverManager.getConnection(
                    "jdbc:splice://" + getTestConfiguration().getHostName()
                            + ":" + getTestConfiguration().getPort() + "/testbase", p);
            fail("Connection with no database succeeded");
        } catch (SQLException e)
        {
            assertSQLState("08004", e);
            assertEquals(40000, e.getErrorCode());
        }
    }

    /**
     * Check that only valid values for connection attributes are accepted.
     * For this check, we attempt to connect using the upgrade attribute
     * with an invalid value.
     *
     * Should fail with SQLState XJ05B.
     */
    public void testBadConnectionAttribute()
    {
        try {
            Connection c = DriverManager.getConnection(
                    "jdbc:splice://" + getTestConfiguration().getHostName()
                            + ":" + getTestConfiguration().getPort() + "/badAttribute;upgrade=notValidValue");
            fail("Connection with bad atttributes succeeded");
        } catch (SQLException e)
        {
            assertSQLState("XJ05B", e);
            assertEquals(-1, e.getErrorCode());
        }
    }

    public BadConnectionTest(String name)
    {
        super(name);
    }

    public static Test suite()
    {
        return TestConfiguration.clientServerSuite(BadConnectionTest.class);
    }
}
