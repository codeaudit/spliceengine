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
package com.splicemachine.dbTesting.junit;

import java.net.URL;

/**
 * Derby related utility methods for the JUnit tests.
 * The class assumes the tests are either being run
 * from the build classes folder or from the standard
 * jar files (or a subset of the standard jars).
 * <BR>
 * If the tests are being run from the classes then
 * it is assumed all the functionality is available,
 * otherwise the functionality will be driven from which
 * jar files are on the classpath. E.g. if only
 * db.jar is on the classpath then the hasXXX() methods
 * will return false except hasEmbedded().
 */
public class Derby {
    
    /**
     * Returns true if the embedded engine is available to the tests.
     */
    public static boolean hasEmbedded()
    {
        // classes folder - assume all is available.
        if (!SecurityManagerSetup.isJars)
            return true;

        return hasCorrectJar("/db.jar",
               "com.splicemachine.db.authentication.UserAuthenticator");
    }
    /**
     * Returns true if the network server is available to the tests.
     */
    public static boolean hasServer()
    {
        // classes folder - assume all is available.
        if (!SecurityManagerSetup.isJars)
            return true;
        
        return hasCorrectJar("/derbynet.jar",
                             "com.splicemachine.db.drda.NetworkServerControl");
    }
    /**
     * Returns true if the tools are available to the tests.
     */
    public static boolean hasTools()
    {
        // classes folder - assume all is available.
        if (!SecurityManagerSetup.isJars)
            return true;
            
        return hasCorrectJar("/derbytools.jar",
                "com.splicemachine.db.tools.ij");
    }
    /**
     * Returns true if the db client is available to the tests.
     */
    public static boolean hasClient()
    {
        // classes folder - assume all is available.
        if (!SecurityManagerSetup.isJars)
            return true;

        // if we attempt to check on availability of the ClientDataSource with 
        // JSR169, attempts will be made to load classes not supported in
        // that environment, such as javax.naming.Referenceable. See DERBY-2269.
        if (!JDBC.vmSupportsJSR169()) {
            return hasCorrectJar("/derbyclient.jar",
                "com.splicemachine.db.jdbc.ClientDataSource");
        }
        else
            return false;
    }
    
    private static boolean hasCorrectJar(String jarName, String className)
    {
        URL url = SecurityManagerSetup.getURL(className);
        if (url == null)
            return false;
        
        return url.toExternalForm().endsWith(jarName);
    }
}
