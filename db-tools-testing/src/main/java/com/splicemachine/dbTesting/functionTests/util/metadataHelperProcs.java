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

package com.splicemachine.dbTesting.functionTests.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class metadataHelperProcs {
	
   
	public static ResultSet rs;

	// for use in test getbestrowidentifier.sql
	public static void getBestRowId(String schema, String tableName, int scope, String nullable, ResultSet[] rs) throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		boolean tf = true;
		if (nullable.equals("false"))
			tf = false;
		rs[0] = conn.getMetaData().getBestRowIdentifier(null,schema.trim(),tableName.trim(),scope,tf);
	}

}
