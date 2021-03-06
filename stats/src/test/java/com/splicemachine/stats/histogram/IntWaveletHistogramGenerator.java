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

package com.splicemachine.stats.histogram;

import com.carrotsearch.hppc.IntLongOpenHashMap;
import com.carrotsearch.hppc.cursors.IntLongCursor;
import com.splicemachine.stats.random.GaussianGenerator;
import com.splicemachine.stats.random.RandomGenerator;
import com.splicemachine.stats.random.UniformGenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Random;


/**
 * Generates csv-files that can be used to create histogram graphics using some
 * tool like R.
 *
 * This is the only way that I can really think to test whether the histograms
 * that are generated are accurate.
 *
 * @author Scott Fines
 *         Date: 10/23/14
 */
public class IntWaveletHistogramGenerator {

    public static void main(String...args)throws Exception{
        int numRecords = 10000;
        IntLongOpenHashMap actualData = IntLongOpenHashMap.newInstance();
        RandomGenerator random = new GaussianGenerator(new UniformGenerator(new Random(0)));

        IntGroupedCountBuilder builder = IntGroupedCountBuilder.build(0.1f,512);
        for(int i=0;i<numRecords;i++){
            int next = (int)(random.nextDouble()*100);
            actualData.addTo(next,1);
            builder.update(next);
        }

        IntRangeQuerySolver querySolver = builder.build(0.2d);
        BufferedWriter actualDataWriter = new BufferedWriter(new FileWriter("/Users/scottfines/workspace/temp/data.csv"));
        try{
            for(IntLongCursor cursor:actualData){
                int val = cursor.key;
                long actualCount = cursor.value;
                long estimate = querySolver.equal(val);
                actualDataWriter.write(val+","+actualCount+","+estimate);
                actualDataWriter.newLine();
            }
            actualDataWriter.flush();
        }finally{
            actualDataWriter.close();
        }
    }

}
