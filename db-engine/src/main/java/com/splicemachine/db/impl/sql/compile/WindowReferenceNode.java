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

package com.splicemachine.db.impl.sql.compile;

import java.util.List;

import com.splicemachine.db.iapi.error.StandardException;

/**
 * Represents a reference to an explicitly defined window
 */
public final class WindowReferenceNode extends WindowNode
{
    /**
     * Initializer
     *
     * @param arg1 The window name referenced
     *
     * @exception StandardException
     */
    public void init(Object arg1)
        throws StandardException
    {
        super.init(arg1);
    }

    @Override
    public List<OrderedColumn> getPartition() {
        return null;
    }

    @Override
    public WindowFrameDefinition getFrameExtent() {
        return null;
    }

    @Override
    public List<OrderedColumn> getOrderByList() {
        return null;
    }

    @Override
    public List<WindowFunctionNode> getWindowFunctions() {
        return null;
    }

    @Override
    public void addWindowFunction(WindowFunctionNode functionNode) {

    }

    @Override
    public void bind(SelectNode selectNode) {
    }

    @Override
    public List<OrderedColumn> getOverColumns() {
        return null;
    }

    @Override
    public String toString() {
        return "referenced window: " + getName() + "\n" +
            super.toString();
    }

}
