/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.iapi.sql.execute.*;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.utils.*;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableArrayHolder;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.services.io.FormatableIntHolder;
import com.splicemachine.db.iapi.services.loader.GeneratedMethod;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.store.access.StaticCompiledOpenConglomInfo;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * A Table Scan that asks for a distinct scan of arguments
 *
 * select distinct (col1) from foo;
 *
 * or a statement where there is a single scan of the only group by column.
 *
 * select col1 from foo group by col1.
 *
 * The optimizer will change the latter to a distinct scan.
 *
 * @author Scott Fines
 * Created on: 5/23/13
 */
public class DistinctScanOperation extends ScanOperation {
    private static final long serialVersionUID = 3l;
	protected static final String NAME = DistinctScanOperation.class.getSimpleName().replaceAll("Operation","");

	@Override
	public String getName() {
        return NAME;
    }

	@SuppressWarnings("UnusedDeclaration")
	public DistinctScanOperation() { }

    private int hashKeyItem;
    private String tableName;
    private String indexName;
    private int[] keyColumns;

    /**
     *
     * Constructor for distinct scan.
     *
     * @param conglomId
     * @param scoci
     * @param activation
     * @param resultRowAllocator
     * @param resultSetNumber
     * @param hashKeyItem
     * @param tableName
     * @param userSuppliedOptimizerOverrides
     * @param indexName
     * @param isConstraint
     * @param colRefItem
     * @param lockMode
     * @param tableLocked
     * @param isolationLevel
     * @param optimizerEstimatedRowCount
     * @param optimizerEstimatedCost
     * @param tableVersion
     * @throws StandardException
     */
    @SuppressWarnings("UnusedParameters")
    public DistinctScanOperation(long conglomId,
                                 StaticCompiledOpenConglomInfo scoci, Activation activation,
                                 GeneratedMethod resultRowAllocator,
                                 int resultSetNumber,
                                 int hashKeyItem,
                                 String tableName,
                                 String userSuppliedOptimizerOverrides,
                                 String indexName,
                                 boolean isConstraint,
                                 int colRefItem,
                                 int lockMode,
                                 boolean tableLocked,
                                 int isolationLevel,
                                 double optimizerEstimatedRowCount,
                                 double optimizerEstimatedCost,
                                 String tableVersion) throws StandardException {
        super(conglomId,
                activation,
                resultSetNumber,
                null,
                -1,
                null,
                -1,
                true,
                false,
                null,
                resultRowAllocator,
                lockMode,
                tableLocked,
                isolationLevel,
                colRefItem,
                -1,
                false,
                optimizerEstimatedRowCount,
                optimizerEstimatedCost,
                tableVersion);
        this.hashKeyItem = hashKeyItem;
        this.tableName = Long.toString(scanInformation.getConglomerateId());
        this.tableDisplayName = tableName;
        this.indexName = indexName;
        init();
    }

    /**
     *
     * Serde
     *
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        tableName = in.readUTF();
        if(in.readBoolean())
            indexName = in.readUTF();
        hashKeyItem = in.readInt();
    }

    /**
     *
     * Serde
     *
     * @param out
     * @throws IOException
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeUTF(tableName);
        out.writeBoolean(indexName!=null);
        if(indexName!=null)
            out.writeUTF(indexName);
        out.writeInt(hashKeyItem);
    }

    /**
     *
     * Initialization after creation or serialization.
     *
     * @param context
     * @throws StandardException
     * @throws IOException
     */
    @Override
    public void init(SpliceOperationContext context) throws StandardException, IOException {
        super.init(context);
        FormatableArrayHolder fah = (FormatableArrayHolder)activation.getPreparedStatement().getSavedObject(hashKeyItem);
        FormatableIntHolder[] fihArray = (FormatableIntHolder[])fah.getArray(FormatableIntHolder.class);

        keyColumns = new int[fihArray.length];
        
        for(int index=0;index<fihArray.length;index++){
            keyColumns[index] = FormatableBitSetUtils.currentRowPositionFromBaseRow(scanInformation.getAccessedColumns(),fihArray[index].getInt());
        }
    }

    /**
     *
     * Sub Operations underneath the current operation.  Empty
     * for a distinct scan.
     *
     * @return
     */
    @Override
    public List<SpliceOperation> getSubOperations() {
        return Collections.emptyList();
    }

    /**
     *
     * The current rows definition (Type, number of columns, and position)
     *
     * @return
     * @throws StandardException
     */
    @Override
    public ExecRow getExecRowDefinition() throws StandardException {
        return currentRow;
    }

    /**
     *
     * Print the operation in a pretty format.
     *
     * @param indentLevel
     * @return
     */
    @Override
    public String prettyPrint(int indentLevel) {
        return "Distinct"+super.prettyPrint(indentLevel);
    }

    /**
     *
     * Retrieve the dataset abstraction for the distinct scan.
     *
     * @param dsp
     * @return
     * @throws StandardException
     */
    public DataSet<LocatedRow> getDataSet(DataSetProcessor dsp) throws StandardException {
        assert currentTemplate != null: "Current Template Cannot Be Null";
        int[] execRowTypeFormatIds = new int[currentTemplate.nColumns()];
        for (int i = 0; i< currentTemplate.nColumns(); i++) {
            execRowTypeFormatIds[i] = currentTemplate.getColumn(i+1).getTypeFormatId();
        }
        FormatableBitSet cols = scanInformation.getAccessedColumns();
        int[] colMap;
        if(cols!=null){
            colMap = new int[cols.getLength()];
            Arrays.fill(colMap,-1);
            for(int i=cols.anySetBit(),pos=0;i>=0;i=cols.anySetBit(i),pos++){
                colMap[i] = pos;
            }
        } else {
            colMap = keyColumns;
        }
        return dsp.<DistinctScanOperation,LocatedRow>newScanSet(this,tableName)
                .tableDisplayName(this.tableDisplayName)
                .activation(activation)
                .transaction(getCurrentTransaction())
                .scan(getNonSIScan())
                .template(currentRow)
                .tableVersion(tableVersion)
                .indexName(indexName)
                .reuseRowLocation(false)
                .keyColumnEncodingOrder(scanInformation.getColumnOrdering())
                .keyColumnSortOrder(scanInformation.getConglomerate().getAscDescInfo())
                .keyColumnTypes(getKeyFormatIds())
                .execRowTypeFormatIds(execRowTypeFormatIds)
                .accessedKeyColumns(scanInformation.getAccessedPkColumns())
                .keyDecodingMap(getKeyDecodingMap())
                .rowDecodingMap(colMap)
                .buildDataSet(this)
                .distinct(OperationContext.Scope.DISTINCT.displayName(), true, operationContext, true, OperationContext.Scope.DISTINCT.displayName());
    }

}
