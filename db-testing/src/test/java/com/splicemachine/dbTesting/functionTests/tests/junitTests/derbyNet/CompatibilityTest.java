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

/**
 * <p>
 * Run the compatibility tests against the embedded server.
 * </p>
 *
 */
package com.splicemachine.dbTesting.functionTests.tests.junitTests.derbyNet;

import java.sql.*;

import com.splicemachine.db.tools.ij;
import com.splicemachine.dbTesting.functionTests.tests.junitTests.compatibility.CompatibilitySuite;

public	class	CompatibilityTest
{
	/////////////////////////////////////////////////////////////
	//
	//	CONSTANTS
	//
	/////////////////////////////////////////////////////////////

	public	static	final	String	DATABASE_NAME = "wombat";
	public	static	final	String	NETWORK_CLIENT_NAME = "com.splicemachine.db.jdbc.ClientDriver";
	
	/////////////////////////////////////////////////////////////
	//
	//	STATE
	//
	/////////////////////////////////////////////////////////////

	/////////////////////////////////////////////////////////////
	//
	//	CONSTRUCTOR
	//
	/////////////////////////////////////////////////////////////
	
	/////////////////////////////////////////////////////////////
	//
	//	ENTRY POINT
	//
	/////////////////////////////////////////////////////////////

	public	static	final	void	main( String[] args )
		throws Exception
	{
		// create database
		ij.getPropertyArg( args );
		Connection conn = ij.startJBMS();

		CompatibilitySuite.main( new String[] { DATABASE_NAME, NETWORK_CLIENT_NAME } );
	}
	
	/////////////////////////////////////////////////////////////
	//
	//	MINIONS
	//
	/////////////////////////////////////////////////////////////

}
