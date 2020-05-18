/*
 * Copyright 2016-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.propagation.stackdriver;

import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.List;

import static brave.propagation.stackdriver.StackdriverTracePropagation.TRACE_ID_NAME;

/**
 * @deprecated use {@link brave.propagation.stackdriver.StackdriverTracePropagation}
 */
@Deprecated
public final class StackdriverTracePropagation<K> implements Propagation<K> {
  static final Propagation.Factory DELEGATE =
      brave.propagation.stackdriver.StackdriverTracePropagation.newFactory(B3Propagation.FACTORY);

  public static final Propagation.Factory FACTORY =
      new Propagation.Factory() {

        @Override public Propagation<String> get() {
          return new StackdriverTracePropagation<>(DELEGATE.get(), KeyFactory.STRING);
        }

        @Override
        public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
          return new StackdriverTracePropagation<>(DELEGATE.create(keyFactory), keyFactory);
        }

        @Override
        public boolean supportsJoin() {
          return DELEGATE.supportsJoin();
        }

        @Override
        public boolean requires128BitTraceId() {
          return DELEGATE.requires128BitTraceId();
        }

        @Override public TraceContext decorate(TraceContext context) {
          return DELEGATE.decorate(context);
        }

        @Override
        public String toString() {
          return "StackdriverTracePropagationFactory";
        }
      };

  final Propagation<K> delegate;
  final K traceIdKey;

  StackdriverTracePropagation(Propagation<K> delegate, KeyFactory<K> keyFactory) {
    this.delegate = delegate;
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
  }

  /**
   * @deprecated Use {@link brave.propagation.stackdriver.StackdriverTracePropagation#TRACE_ID_NAME}
   */
  @Deprecated public K getTraceIdKey() {
    return traceIdKey;
  }

  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return delegate.injector(setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return delegate.extractor(getter);
  }
}
