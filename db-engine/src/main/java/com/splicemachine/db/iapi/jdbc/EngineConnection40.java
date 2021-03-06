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
package com.splicemachine.db.iapi.jdbc;

import java.sql.SQLException;
import java.util.concurrent.Executor;

/**
 * <p>
 * Additional methods exposed on the Connection object for interaction between
 * EmbedConnection and BrokeredConnection. These methods should generally go
 * into the EngineConnection interface, but those that have signatures that are
 * not compatible with all platforms on which EngineConnection needs to work,
 * could go into this interface.
 * </p>
 *
 * <p>
 * For example, the JDBC 4.1 methods that take a java.util.concurrent.Executor
 * argument, cannot be included in an interface that should be loadable on CDC,
 * and they must go here.
 * </p>
 */
public interface EngineConnection40 extends EngineConnection {
    // JDBC 4.1 methods that take an Executor argument
    void abort(Executor executor) throws SQLException;
    void setNetworkTimeout(Executor executor, int millis) throws SQLException;
    int getNetworkTimeout() throws SQLException;
}
