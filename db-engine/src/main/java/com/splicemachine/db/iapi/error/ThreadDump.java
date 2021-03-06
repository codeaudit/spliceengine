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

package com.splicemachine.db.iapi.error;

/* Until DERBY-289 related issue settle for shared code
 * Engine have similar code as client code even though some of 
 * code is potentially sharable. If you fix a bug in ThreadDump for engine, 
 * please also change the code in 
 * java/shared/com/splicemachine/db/shared/common/sanity/ThreadDump.java for
 * client if necessary.
 */

import java.util.Map;

public class ThreadDump {

    /**
     * 
     * @return A string representation of a full thread dump
     */
    public static String getStackDumpString() {
        StringBuffer sb = new StringBuffer();
        Map<Thread, StackTraceElement[]> st = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> e : st.entrySet()) {
            StackTraceElement[] lines = e.getValue();
            Thread t = e.getKey();
            sb.append("Thread name=" + t.getName() + " id=" + t.getId()
                    + " priority=" + t.getPriority() + " state=" + t.getState()
                    + " isdaemon=" + t.isDaemon() + "\n");
            for (int i = 0; i < lines.length; i++) {
                sb.append("\t" + lines[i] + "\n");

            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
