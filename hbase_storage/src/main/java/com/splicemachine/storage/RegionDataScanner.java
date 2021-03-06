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

package com.splicemachine.storage;

import org.spark_project.guava.base.Function;
import org.spark_project.guava.collect.Lists;
import com.splicemachine.metrics.Counter;
import com.splicemachine.metrics.MetricFactory;
import com.splicemachine.metrics.TimeView;
import com.splicemachine.metrics.Timer;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Scott Fines
 *         Date: 12/14/15
 */
public class RegionDataScanner implements DataScanner{
    private final Timer readTimer;
    private final Counter outputBytesCounter;
    private final Counter filteredRowCounter;
    private final Counter visitedRowCounter;
    private final RegionScanner delegate;
    private final Partition partition;

    private List<Cell> internalList;

    //private final HCell cell = new HCell();

    private final Function<Cell,DataCell> transform = new Function<Cell, DataCell>(){
        @Override
        public DataCell apply(Cell input){
            HCell cell = new HCell();
            cell.set(input);
            return cell;
        }
    };

    public RegionDataScanner(Partition source,RegionScanner delegate,MetricFactory metricFactory){
        this.delegate=delegate;
        this.readTimer = metricFactory.newTimer();
        this.partition = source;
        this.outputBytesCounter = metricFactory.newCounter();
        this.filteredRowCounter = metricFactory.newCounter();
        this.visitedRowCounter = metricFactory.newCounter();
    }

    @Override
    public Partition getPartition(){
        return partition;
    }

    @Override
    public @Nonnull List<DataCell> next(int limit) throws IOException{
        if(internalList==null)
            internalList = new ArrayList<>(limit>0?limit:10);
        internalList.clear();
        readTimer.startTiming();
        delegate.next(internalList);
        if(internalList.size()>0){
            readTimer.tick(1);
            collectMetrics(internalList);
        }else
            readTimer.stopTiming();

        return Lists.transform(internalList,transform);
    }


    @Override public TimeView getReadTime(){ return readTimer.getTime(); }
    @Override public long getBytesOutput(){ return outputBytesCounter.getTotal(); }
    @Override public long getRowsFiltered(){ return filteredRowCounter.getTotal(); }
    @Override public long getRowsVisited(){ return readTimer.getNumEvents(); }

    @Override public void close() throws IOException{ delegate.close(); }


    /* *********************************************************************************************************/
    /*private helper methods*/
    private void collectMetrics(List<Cell> internalList){
        if(outputBytesCounter.isActive()){
            for(int i=0;i<internalList.size();i++){
                Cell c = internalList.get(i);
                outputBytesCounter.add(c.getRowLength()+c.getQualifierLength()+c.getFamilyLength()+c.getValueLength());
            }
        }
    }
}
