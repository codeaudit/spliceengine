package com.splicemachine.derby.impl.sql.execute.altertable;

import java.io.IOException;
import java.util.List;

import com.google.common.io.Closeables;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.utils.marshall.EntryDataDecoder;
import com.splicemachine.derby.utils.marshall.KeyHashDecoder;
import com.splicemachine.derby.utils.marshall.PairEncoder;
import com.splicemachine.hbase.KVPair;
import com.splicemachine.pipeline.api.RowTransformer;

/**
 * Used by alter table write interceptors to map rows written in src table to new
 * target table.
 * <p/>
 * This class is driven by its exec row definitions and its encoder/decoder.<br/>
 * These are created for a specific alter table action in TransformingDDLDescriptors
 * specializations.
 */
public class AlterTableRowTransformer implements RowTransformer {
    private final ExecRow srcRow;
    private final ExecRow templateRow;
    private final EntryDataDecoder rowDecoder;
    private final KeyHashDecoder keyDecoder;
    private final PairEncoder entryEncoder;
    private final int[] columnMapping;

    public AlterTableRowTransformer(ExecRow srcRow,
                                    int[] columnMapping,
                                    ExecRow templateRow,
                                    KeyHashDecoder keyDecoder,
                                    EntryDataDecoder rowDecoder,
                                    PairEncoder entryEncoder) {
        this.srcRow = srcRow;
        this.columnMapping = columnMapping;
        this.templateRow = templateRow;
        this.rowDecoder = rowDecoder;
        this.keyDecoder = keyDecoder;
        this.entryEncoder = entryEncoder;
    }

    public KVPair transform(KVPair kvPair) throws StandardException, IOException {
        // Decode a row
        ExecRow mergedRow = templateRow.getClone();
        srcRow.resetRowArray();
        if (srcRow.nColumns() > 0) {
            keyDecoder.set(kvPair.getRowKey(), 0, kvPair.getRowKey().length);
            keyDecoder.decode(srcRow);

            rowDecoder.set(kvPair.getValue(), 0, kvPair.getValue().length);
            rowDecoder.decode(srcRow);
        }

        int i = 1;
        for (DataValueDescriptor dvd : srcRow.getRowArray()) {
            mergedRow.setColumn(i++, dvd);
        }
        // encode the result
        KVPair newPair = entryEncoder.encode(mergedRow);
        return newPair;
    }

    @Override
    public KVPair transform(List<KVPair> kvPairs) throws StandardException, IOException {
        // merge the columns - the template row will have default values, if there are any
        // (i.e., when adding column)
        ExecRow mergedRow = templateRow.getClone();
        for (KVPair kvPair : kvPairs) {
            srcRow.resetRowArray();
            if (srcRow.nColumns() > 0) {
                keyDecoder.set(kvPair.getRowKey(), 0, kvPair.getRowKey().length);
                keyDecoder.decode(srcRow);

                rowDecoder.set(kvPair.getValue(), 0, kvPair.getValue().length);
                rowDecoder.decode(srcRow);
            }
            for (int i = 0; i < columnMapping.length; i++) {
                int targetIndex = columnMapping[i];
                if (targetIndex != 0) {
                    DataValueDescriptor clone = srcRow.cloneColumn(i+1);
                    if (! clone.isNull()) {
                        // if the src row col val is null, then it's an updated row - don't
                        // set null col value
                        mergedRow.setColumn(targetIndex, clone);
                    }
                }
            }
        }
        // encode the result
        KVPair newPair = entryEncoder.encode(mergedRow);
        return newPair;
    }

    @Override
    public void close() {
        Closeables.closeQuietly(keyDecoder);
        Closeables.closeQuietly(rowDecoder);
        Closeables.closeQuietly(entryEncoder);
    }
}
