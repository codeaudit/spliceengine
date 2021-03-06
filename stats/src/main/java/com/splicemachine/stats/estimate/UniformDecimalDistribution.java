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

package com.splicemachine.stats.estimate;

import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.frequency.FrequencyEstimate;
import com.splicemachine.utils.ComparableComparator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 3/5/15
 */
public class UniformDecimalDistribution extends UniformDistribution<BigDecimal> {
    private final BigDecimal a;

    public UniformDecimalDistribution(ColumnStatistics<BigDecimal> columnStats) {
        super(columnStats, ComparableComparator.<BigDecimal>newComparator());

        if(columnStats.nonNullCount()==0){
            /*
             * We have an empty distribution. Make sure that our linear interpolation
             * is 0
             */
            this.a = BigDecimal.ZERO;
        }else if(columnStats.maxValue().compareTo(columnStats.minValue())==0){
            /*
             * There is only one record in the distribution, so treat the line as a constant
             * function
             */
            this.a = BigDecimal.ZERO;
        }else{
            /*
             * Create a linear function to interpolate between the min and max values.
             */
            BigDecimal at=BigDecimal.valueOf(getAdjustedRowCount()-columnStats.minCount());
            at=at.divide(columnStats.maxValue().subtract(columnStats.minValue()),MathContext.DECIMAL64);

            this.a=at;
        }
    }

    @Override
    protected long estimateRange(BigDecimal start, BigDecimal stop, boolean includeStart, boolean includeStop, boolean isMin) {
        BigDecimal baseE = a.multiply(stop.subtract(start));
        /*
         * This is safe, because the linear function we used has a max of maxValue on the range [minValue,maxValue),
         * with a maximum value of rowCount (we built the linear function to do this). Since rowCount is a long,
         * the value of baseE *MUST* fit within a long (and therefore, within a double).
         */
        double baseEstimate = baseE.doubleValue();


        //if we are the min value, don't include the start key in frequent elements
        boolean includeMinFreq = includeStart &&!isMin;
        //now adjust for the frequent elements in the set
        Set<? extends FrequencyEstimate<BigDecimal>> fe = columnStats.topK().frequentElementsBetween(start,stop,includeMinFreq,includeStop);
        long l=uniformRangeCount(includeMinFreq,includeStop,baseEstimate,fe);
        if(isMin&&includeStart)
            l+=minCount();
        return l;
    }

    public long cardinality(BigDecimal start,BigDecimal stop,boolean includeStart,boolean includeStop){
        if(start==null){
            start= columnStats.minValue();
            includeStart = true;
        }
        if(stop==null){
            stop = columnStats.maxValue();
            includeStop = true;
        }
        if(stop.compareTo(start)<0) return 0l;
        if(stop.compareTo(start)==0){
            if(includeStart && includeStop) return 1l;
            return 0l;
        }

        BigDecimal dist = stop.subtract(start);
        if(includeStop)dist = dist.add(BigDecimal.ONE);
        if(!includeStart) dist = dist.subtract(BigDecimal.ONE);
        return dist.longValue();
    }

    @Override public BigDecimal minValue(){ return columnStats.minValue(); }
    @Override public long minCount(){ return columnStats.minCount(); }
    @Override public BigDecimal maxValue(){ return columnStats.maxValue(); }
    @Override public long totalCount(){ return columnStats.nonNullCount(); }
    public long cardinality(){ return columnStats.cardinality(); }
}
