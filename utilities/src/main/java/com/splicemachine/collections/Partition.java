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

package com.splicemachine.collections;

import java.util.*;

/**
 * @author P Trolard
 *         Date: 25/10/2013
 */
public class Partition {

    private static int needsPadding(int seqLength, int partitionSize, int stepSize){
        return (seqLength % stepSize) + (partitionSize - stepSize);
    }

    /**
     * Partition coll into sub-collection of given size, advancing by given number of steps
     * in coll between partitions and optionally padding any empty space with null. (Specifying
     * a step size smaller than the partition size allows overlapping partitions.)
     */
    public static <T> Iterable<List<T>> partition(Collection<T> coll,
                                                  final int size,
                                                  final int steps,
                                                  final boolean pad){
        final List<T> c = new ArrayList<T>(coll);
        final int collSize = c.size();

        int padding = Math.max(needsPadding(collSize, size, steps), 0);
        if (pad && padding > 0) {
            for (int i = 0; i < padding; i++){
                c.add(null);
            }
        }

        return new Iterable<List<T>>() {
            @Override
            public Iterator<List<T>> iterator() {
                return new Iterator<List<T>>() {
                    private int cursor = 0;
                    @Override
                    public boolean hasNext() {
                        return cursor < collSize;
                    }

                    @Override
                    public List<T> next() {
                        if (!hasNext()){
                            throw new NoSuchElementException();
                        }
                        List<T> partition = c.subList(cursor, cursor + size < c.size() ?
                                                                cursor + size : c.size());
                        cursor = cursor + steps;
                        return Collections.unmodifiableList(partition);
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public static <T> Iterable<List<T>> partition(List<T> coll, int size, int steps){
        return partition(coll, size, steps, false);
    }

    public static <T> Iterable<List<T>> partition(List<T> coll, int size){
        return partition(coll, size, size, false);
    }

    public static <T> Iterable<List<T>> partition(List<T> coll, int size, boolean pad){
        return partition(coll, size, size, pad);
    }
}
