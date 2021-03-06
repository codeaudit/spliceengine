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

import java.util.Properties;
import java.sql.SQLException;

/**
 *
 * The AuthenticationService provides a mechanism for authenticating
 * users willing to access JBMS.
 * <p>
 * There can be different and user defined authentication schemes, as long
 * the expected interface here below is implementing and registered
 * as a module when JBMS starts-up.
 * <p>
 */
public interface AuthenticationService 
{

	public static final String MODULE =
								"com.splicemachine.db.iapi.jdbc.AuthenticationService";
	/**
	 * Authenticate a User inside Derby.
	 *
	 * @param info			Connection properties info.
	 * failure.
	 */
	public boolean authenticate(String databaseName, Properties info)
	  throws SQLException;

    /**
     * <p>
     * Get the name of the credentials database used to authenticate system-wide operations.
     * This returns null for all implementations except NATIVE authentication.
     * </p>
     */
    public  String  getSystemCredentialsDatabaseName();

}
