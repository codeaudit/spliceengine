/*
 
   Derby - Class com.splicemachine.db.client.net.NetDatabaseMetaData40
 
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at
 
      http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
 */

package com.splicemachine.db.client.net;

import java.sql.SQLException;

public class NetDatabaseMetaData40 extends com.splicemachine.db.client.net.NetDatabaseMetaData {
    
    
    public NetDatabaseMetaData40(NetAgent netAgent, NetConnection netConnection) {
        super(netAgent,netConnection);
    }

    /**
     * Retrieves the major JDBC version number for this driver.
     * @return JDBC version major number
     * @exception SQLException if the connection is closed
     */
    public int getJDBCMajorVersion() throws SQLException {
        checkForClosedConnection();
        return 4;
    }

    /**
     * Retrieves the minor JDBC version number for this driver.
     * @return JDBC version minor number
     * @exception SQLException if the connection is closed
     */
    public int getJDBCMinorVersion() throws SQLException {
        checkForClosedConnection();
        return 1;
    }

}