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

package com.splicemachine.storage.index;

import java.util.Arrays;
import com.carrotsearch.hppc.BitSet;


/**
 * Lazy implementation of an Uncompressed, Dense BitIndex.
 * @author Scott Fines
 * Created on: 7/8/13
 */
class UncompressedLazyBitIndex extends LazyBitIndex{

    int lastSetBit = -1;

    protected UncompressedLazyBitIndex(byte[] encodedBitMap,
                                       int offset, int length) {
        super(encodedBitMap, offset, length,5);
    }

    @Override
    protected int decodeNext() {
        if(!bitReader.hasNext()) return -1;
        int unsetCount = bitReader.nextSetBit();
        if(unsetCount<0) return -1;
        int pos = lastSetBit+unsetCount+1;
        /*
         * We need the next two bits to determine the type information,
         * which goes as
         *
         * Untyped: 00
         * Double: 01
         * Float: 10
         * Scalar:11
         *
         * A truncated setup for Float or untyped values is not likely (the encoding
         * implementation always sets those bits at the moment), but later improvements may
         * include truncating extra zero bits. Thus, we assume that is possible.
         */
        if(!bitReader.hasNext()){
            //no type bits set, so this must be untyped
            return pos;
        }
        if(bitReader.next()!=0){
            //either float or scalar type
            if(!bitReader.hasNext()){
                //must be 10 = float type
                setFloatField(pos);
                return pos;
            }
            if(bitReader.next()!=0)
                setScalarField(pos);
            else
                setFloatField(pos);
        }else{
            //either a double or untyped
            if(!bitReader.hasNext())
                return pos; //untyped

            if(bitReader.next()!=0)
                setDoubleField(pos);
        }

        lastSetBit=pos;
        return pos;
    }



    public static void main(String... args) throws Exception{
        BitSet bitSet = new BitSet(11);
        bitSet.set(0);
        bitSet.set(1);
//        bitSet.set(3);
//        bitSet.set(4);

        BitSet lengthDelimited = new BitSet();
        lengthDelimited.set(0);

        BitIndex bits = UncompressedBitIndex.create(bitSet,lengthDelimited,null,null);

        byte[] data =bits.encode();
        System.out.println(Arrays.toString(data));

        BitIndex decoded = BitIndexing.uncompressedBitMap(data,0,data.length);
        for(int i=decoded.nextSetBit(0);i>=0;i=decoded.nextSetBit(i+1));

        System.out.println(decoded);
    }

	@Override
	public boolean isCompressed() {
		return false;
	}
}
