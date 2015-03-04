package com.splicemachine.derby.impl.stats;

import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.LongColumnStatistics;
import com.splicemachine.stats.collector.LongColumnStatsCollector;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.types.DataValueDescriptor;

/**
 * @author Scott Fines
 *         Date: 2/27/15
 */
public class BigintStatsCollector extends DvdStatsCollector {
    private LongColumnStatsCollector baseCollector;

    public BigintStatsCollector(LongColumnStatsCollector baseCollector) {
        super(baseCollector);
        this.baseCollector = baseCollector;
    }

    @Override
    protected void doUpdate(DataValueDescriptor dataValueDescriptor, long count) throws StandardException {
        baseCollector.update(dataValueDescriptor.getLong(),count);
    }

    @Override
    protected ColumnStatistics<DataValueDescriptor> newStats(ColumnStatistics build) {
        return new BigintStats((LongColumnStatistics)build);
    }
}