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

package com.splicemachine.stream;

import com.splicemachine.metrics.Stats;

/**
 * @author Scott Fines
 *         Date: 2/12/15
 */
public class MeasuredStreams {


    static class TransformingMeasuredStream<E,R,V extends Stats> extends AbstractMeasuredStream<R,V>{
        private final MeasuredStream<E,V> delegate;
        private final Transformer<E, R> transformer;

        public TransformingMeasuredStream(MeasuredStream<E, V> delegate, Transformer<E, R> transformer) {
            this.delegate = delegate;
            this.transformer = transformer;
        }

        @Override
        public <K> MeasuredStream<K,V> transform(Transformer<R, K> transformer) {
            return new TransformingMeasuredStream<>(this,transformer);
        }

        @Override
        public R next() throws StreamException {
            E n = delegate.next();
            if(n==null) return null;
            return transformer.transform(n);
        }

        @Override public void close() throws StreamException { delegate.close(); }

        @Override
        public V getStats() {
            return delegate.getStats();
        }
    }

    static final class FilteredMeasuredStream<T,V extends Stats> extends ForwardingMeasuredStream<T,V>{
        private final Predicate<T> predicate;

        public FilteredMeasuredStream(MeasuredStream<T,V> delegate, Predicate<T> predicate) {
            super(delegate);
            this.predicate = predicate;
        }

        @Override
        public T next() throws StreamException {
            T n;
            while((n = delegate.next())!=null){
                if(predicate.apply(n)) return n;
            }
            return null;
        }
    }

    static final class LimitedStream<T,V extends Stats> extends ForwardingMeasuredStream<T,V> {
        private final long maxSize;
        private long numReturned;

        public LimitedStream(MeasuredStream<T,V> stream, long maxSize) {
            super(stream);
            this.maxSize = maxSize;
        }

        @Override
        public T next() throws StreamException {
            if(numReturned>=maxSize) return null;
            T n = delegate.next();
            if(n==null)
                numReturned = maxSize+1; //prevent extraneous calls to the underlying stream
            numReturned++;
            return n;
        }
    }

}
