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

package com.splicemachine.derby.stream;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.BooleanDataValue;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.RowLocation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.store.access.hbase.HBaseRowLocation;
import com.splicemachine.derby.stream.BaseStreamTest;
import com.splicemachine.derby.stream.function.SpliceFunction;
import com.splicemachine.derby.stream.function.SpliceFunction2;
import com.splicemachine.derby.stream.function.SplicePairFunction;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.PairDataSet;
import com.splicemachine.derby.utils.test.TestingDataType;
import com.splicemachine.primitives.Bytes;
import org.junit.Assert;
import org.junit.Test;
import scala.Tuple2;

import java.io.Serializable;
import java.util.Iterator;

/**
 * Created by jleach on 4/15/15.
 */
public abstract class AbstractDataSetTest extends BaseStreamTest implements Serializable{

    public AbstractDataSetTest() {

    }

    protected abstract DataSet<ExecRow> getTenRowsTwoDuplicateRecordsDataSet();

    /*
    @Test
    public void testDistinct() {
        DataSet<ExecRow> ds = getTenRowsTwoDuplicateRecordsDataSet();
        Assert.assertEquals("Duplicate Rows Not Filtered", 10, ds.distinct().collect().size());
    }
    */

    @Test
    public void testMap() throws StandardException {
        DataSet<ExecRow> ds = getTenRowsTwoDuplicateRecordsDataSet();
        DataSet<ExecRow> mapppedDataSet = ds.map(new MapFunction());
        Iterator < ExecRow > it = mapppedDataSet.toLocalIterator();
        while (it.hasNext())
            Assert.assertTrue("transform did not replace value",it.next().getColumn(1) instanceof BooleanDataValue);
    }

    @Test
    public void testLocalIterator() {
        int numElements = 0;
        DataSet<ExecRow> ds = getTenRowsTwoDuplicateRecordsDataSet();
        Iterator<ExecRow> it = ds.toLocalIterator();
        while (it.hasNext() && it.next() !=null)
            numElements++;
        Assert.assertEquals("iterator loses records",BaseStreamTest.tenRowsTwoDuplicateRecords.size(),numElements);
    }

    @Test
    public void testIndex() throws Exception {
        DataSet<ExecRow> ds = getTenRowsTwoDuplicateRecordsDataSet();
        PairDataSet<RowLocation,ExecRow> pairDataSet = ds.index(new IndexPairFunction());
        Iterator < ExecRow > it = pairDataSet.values().toLocalIterator();
        int i = 0;
        while (it.hasNext()) {
            i++;
            ExecRow execRow = it.next();
            Assert.assertEquals("Key not set",1,execRow.getColumn(1).getInt());
        }
        Assert.assertEquals("Number of rows incorrect",11,i);
    }

    public static class MapFunction extends SpliceFunction<SpliceOperation,ExecRow,ExecRow> {

        public MapFunction() {
            super();
        }

        @Override
        public ExecRow call(ExecRow execRow) throws Exception {
            ExecRow clone = execRow.getClone();
            clone.setColumn(1,TestingDataType.BOOLEAN.getDataValueDescriptor());
            return clone;
        }
    }

    public static class IndexPairFunction extends SplicePairFunction<SpliceOperation,ExecRow, RowLocation,ExecRow> {

        public IndexPairFunction() {

        }

        @Override
        public RowLocation genKey(ExecRow execRow) {
            try {
                ExecRow clone = execRow.getClone();
                DataValueDescriptor dvd = TestingDataType.INTEGER.getDataValueDescriptor();
                dvd.setValue(1);
                clone.setColumn(1, dvd);
                return new HBaseRowLocation(Bytes.toBytes(execRow.getColumn(1).getInt()));
            } catch (Exception e) {
                throw new RuntimeException("Error");
            }
        }

        @Override
        public ExecRow genValue(ExecRow execRow) {
            try {
                ExecRow clone = execRow.getClone();
                DataValueDescriptor dvd = TestingDataType.INTEGER.getDataValueDescriptor();
                dvd.setValue(1);
                clone.setColumn(1, dvd);
                return clone;
            } catch (Exception e) {
                throw new RuntimeException("Error");
            }
        }

        @Override
        public Tuple2<RowLocation, ExecRow> call(ExecRow execRow) throws Exception {
            return new Tuple2(genKey(execRow),genValue(execRow));
        }
    }

}
