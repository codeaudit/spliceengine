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

/**
 * Additional methods the embedded engine exposes on all of its large object
 * (LOB) implementations.
 * <p>
 * An internal API only, mainly for the network server.
 * <p>
 * <b>Implementation note</b>: If a new method is needed, that only applies to
 * one specific large object type (for instance a Blob), one should consider
 * creating a new interface that extends from this one.
 */
public interface EngineLOB {

    /**
     * Returns LOB locator key.
     * <p>
     * The key can be used with
     * {@link com.splicemachine.db.impl.jdbc.EmbedConnection#getLOBMapping} to
     * retrieve this LOB at a later time.
     *
     * @return Locator key for this LOB
     */
    public int getLocator();

    /**
     * Frees all resources assoicated with this LOB.
     *
     * @throws SQLException if an error occurs during cleanup
     */
    public void free() throws SQLException;
}
