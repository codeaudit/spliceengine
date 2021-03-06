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

package com.splicemachine.derby.utils.marshall;

import com.splicemachine.SpliceKryoRegistry;
import com.splicemachine.derby.utils.DerbyBytesUtil;
import com.splicemachine.derby.utils.marshall.dvd.DescriptorSerializer;
import com.splicemachine.storage.EntryEncoder;
import com.splicemachine.utils.kryo.KryoPool;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import java.io.IOException;
import com.carrotsearch.hppc.BitSet;

/**
 * @author Scott Fines
 * Date: 11/15/13
 */
public class EntryDataHash extends BareKeyHash implements DataHash<ExecRow>{
		protected EntryEncoder entryEncoder;
		protected ExecRow currentRow;
		protected DataValueDescriptor dvds;
		protected KryoPool kryoPool;

		public EntryDataHash(int[] keyColumns, boolean[] keySortOrder,DescriptorSerializer[] serializers) {
				this(keyColumns, keySortOrder, SpliceKryoRegistry.getInstance(),serializers);
		}

		public EntryDataHash(int[] keyColumns, boolean[] keySortOrder,KryoPool kryoPool,DescriptorSerializer[] serializers) {
				super(keyColumns, keySortOrder,true,kryoPool,serializers);
				this.kryoPool = kryoPool;
		}

		@Override
		public void setRow(ExecRow rowToEncode) {
				this.currentRow = rowToEncode;
		}

		@Override
		public byte[] encode() throws StandardException, IOException {
				if(entryEncoder==null)
						entryEncoder = buildEntryEncoder();

				int nCols = currentRow.nColumns();
				BitSet notNullFields = new BitSet(nCols);
				entryEncoder.reset(getNotNullFields(currentRow,notNullFields));

				pack(entryEncoder.getEntryEncoder(),currentRow);
				return entryEncoder.encode();
		}

		protected EntryEncoder buildEntryEncoder() {
				int nCols = currentRow.nColumns();
				BitSet notNullFields = getNotNullFields(currentRow,new BitSet(nCols));
				DataValueDescriptor[] fields = currentRow.getRowArray();
				BitSet scalarFields = new BitSet(nCols);
				BitSet floatFields = new BitSet(nCols);
				BitSet doubleFields = new BitSet(nCols);
				if(keyColumns!=null){
						for( int pos:keyColumns){
								if(pos<0) continue;
								DataValueDescriptor field = fields[pos];
								if(field==null) continue;
								DescriptorSerializer serializer = serializers[pos];
								if(serializer.isScalarType())
										scalarFields.set(pos);
								else if(serializer.isFloatType())
										floatFields.set(pos);
								else if(serializer.isDoubleType())
										doubleFields.set(pos);
						}
				}else{
						int i=0;
						for(DataValueDescriptor field:fields){
								if(field==null) continue;
								if(DerbyBytesUtil.isScalarType(field, null))
										scalarFields.set(i);
								else if(DerbyBytesUtil.isFloatType(field))
										floatFields.set(i);
								else if(DerbyBytesUtil.isDoubleType(field))
										doubleFields.set(i);
								i++;
						}
				}
				return EntryEncoder.create(kryoPool,nCols,notNullFields,scalarFields,floatFields,doubleFields);
		}

		protected BitSet getNotNullFields(ExecRow row,BitSet notNullFields) {
				notNullFields.clear();
				if(keyColumns!=null){
						DataValueDescriptor[] fields = row.getRowArray();
						for(int keyColumn:keyColumns){
								if(keyColumn<0) continue;
								DataValueDescriptor dvd = fields[keyColumn];
								if(dvd!=null &&!dvd.isNull())
										notNullFields.set(keyColumn);
						}
				}else{
						int i=0;
						for(DataValueDescriptor dvd:row.getRowArray()){
								if(dvd!=null &&!dvd.isNull()){
										notNullFields.set(i);
								}
								i++;
						}
				}
				return notNullFields;
		}

		@Override
		public KeyHashDecoder getDecoder() {
				return new EntryDataDecoder(keyColumns,keySortOrder,serializers);
		}

		public void close() throws IOException {
				if(entryEncoder!=null)
						entryEncoder.close();
				super.close();
		}
}
