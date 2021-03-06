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

package com.splicemachine.si.impl.readresolve;

import com.splicemachine.annotations.ThreadSafe;
import com.splicemachine.si.api.readresolve.KeyedReadResolver;
import com.splicemachine.si.api.readresolve.ReadResolver;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnSupplier;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.si.impl.rollforward.RollForwardStatus;
import com.splicemachine.storage.Partition;
import com.splicemachine.storage.RegionPartition;
import com.splicemachine.utils.ByteSlice;
import com.splicemachine.utils.TrafficControl;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.RegionTooBusyException;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Read-Resolver which resolves elements synchronously on the calling thread.
 *
 * @author Scott Fines
 *         Date: 7/2/14
 */
@ThreadSafe
public class SynchronousReadResolver implements KeyedReadResolver{
    private static final Logger LOG=Logger.getLogger(SynchronousReadResolver.class);

    //don't instantiate me, I'm a singleton!
    private SynchronousReadResolver(){
    }

    public static final SynchronousReadResolver INSTANCE=new SynchronousReadResolver();
    public static volatile boolean DISABLED_ROLLFORWARD=false;

    /**
     * @param region      the region for the relevant read resolver
     * @param txnSupplier a Transaction Supplier for fetching transaction information
     * @return a ReadResolver which uses Synchronous Read Resolution under the hood.
     */
    @ThreadSafe
    public static ReadResolver getResolver(final Partition region,final TxnSupplier txnSupplier,final RollForwardStatus status){
        return getResolver(region,txnSupplier,status,null,false);
    }

    @ThreadSafe
    public static ReadResolver getResolver(final Partition region,
                             final TxnSupplier txnSupplier,
                             final RollForwardStatus status,
                             final TrafficControl trafficControl,
                             final boolean failOnError){
        return new ReadResolver(){
            @Override
            public void resolve(ByteSlice rowKey,long txnId){
                SynchronousReadResolver.INSTANCE.resolve(region,rowKey,txnId,txnSupplier,status,failOnError,trafficControl);
            }
        };
    }

    public boolean resolve(Partition region,ByteSlice rowKey,long txnId,TxnSupplier supplier,RollForwardStatus status,boolean failOnError,TrafficControl trafficControl){
        try{
            TxnView transaction=supplier.getTransaction(txnId);
            boolean resolved=false;
            if(transaction.getEffectiveState()==Txn.State.ROLLEDBACK){
                trafficControl.acquire(1);
                try{
                    SynchronousReadResolver.INSTANCE.resolveRolledback(region,rowKey,txnId,failOnError);
                    resolved=true;
                }finally{
                    trafficControl.release(1);
                }
            }else{
                TxnView t=transaction;
                while(t.getState()==Txn.State.COMMITTED){
                    t=t.getParentTxnView();
                }
                if(t==Txn.ROOT_TRANSACTION){
                    trafficControl.acquire(1);
                    try{
                        SynchronousReadResolver.INSTANCE.resolveCommitted(region,rowKey,txnId,transaction.getEffectiveCommitTimestamp(),failOnError);
                        resolved=true;
                    }finally{
                        trafficControl.release(1);
                    }
                }
            }
            status.rowResolved();
            return resolved;
        }catch(IOException e){
            LOG.info("Unable to fetch transaction for id "+txnId+", will not resolve",e);
            if(failOnError)
                throw new RuntimeException(e);
            return false;
        }catch(InterruptedException e){
            LOG.debug("Interrupted which performing read resolution, will not resolve");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /******************************************************************************************************************/
    /*private helper methods */
    private void resolveCommitted(Partition region,ByteSlice rowKey,long txnId,long commitTimestamp,boolean failOnError){
        assert region instanceof RegionPartition: "Not on a region!";
        /*
         * Resolve the row as committed directly.
         *
         * This does a Put to the row, bypassing SI and the WAL, so it should be pretty low impact
         */
        if(DISABLED_ROLLFORWARD || region.isClosed() || region.isClosing())
            return; //do nothing if we are closing or rollforward is disabled

        Put put=new Put(rowKey.getByteCopy());
        put.add(SIConstants.DEFAULT_FAMILY_BYTES,
                SIConstants.SNAPSHOT_ISOLATION_COMMIT_TIMESTAMP_COLUMN_BYTES,txnId,
                Bytes.toBytes(commitTimestamp));
        put.setAttribute(SIConstants.SI_EXEMPT,SIConstants.TRUE_BYTES);
        put.setAttribute(SIConstants.SUPPRESS_INDEXING_ATTRIBUTE_NAME,SIConstants.SUPPRESS_INDEXING_ATTRIBUTE_VALUE);
        put.setDurability(Durability.SKIP_WAL);
        try{
            ((RegionPartition)region).unwrapDelegate().put(put);
        }catch(IOException e){
            if(!(e instanceof RegionTooBusyException) && !(e instanceof NotServingRegionException)){
                LOG.info("Exception encountered when attempting to resolve a row as committed",e);
                if(failOnError)
                    throw new RuntimeException(e);
            }
        }
    }

    private void resolveRolledback(Partition region,ByteSlice rowKey,long txnId,boolean failOnError){
        assert region instanceof RegionPartition: "Not on a region!";
        /*
         * Resolve the row as rolled back directly.
         *
         * This does a Delete to the row, bypassing SI and the WAL, so it should be pretty low impact
         */
        if(DISABLED_ROLLFORWARD || region.isClosed() || region.isClosing())
            return; //do nothing if we are closing

        Delete delete=new Delete(rowKey.getByteCopy(),txnId)
                .deleteColumn(SIConstants.DEFAULT_FAMILY_BYTES,SIConstants.PACKED_COLUMN_BYTES,txnId) //delete all the columns for our family only
                .deleteColumn(SIConstants.DEFAULT_FAMILY_BYTES,SIConstants.SNAPSHOT_ISOLATION_TOMBSTONE_COLUMN_BYTES,txnId) //delete all the columns for our family only
                .deleteColumn(SIConstants.DEFAULT_FAMILY_BYTES,SIConstants.SNAPSHOT_ISOLATION_ANTI_TOMBSTONE_VALUE_BYTES,txnId); //delete all the columns for our family only
        delete.setDurability(Durability.SKIP_WAL);
        delete.setAttribute(SIConstants.SUPPRESS_INDEXING_ATTRIBUTE_NAME,SIConstants.SUPPRESS_INDEXING_ATTRIBUTE_VALUE);
        try{
            ((RegionPartition)region).unwrapDelegate().delete(delete);
        }catch(IOException ioe){
            LOG.info("Exception encountered when attempting to resolve a row as rolled back",ioe);
            if(failOnError)
                throw new RuntimeException(ioe);
        }
    }
}
