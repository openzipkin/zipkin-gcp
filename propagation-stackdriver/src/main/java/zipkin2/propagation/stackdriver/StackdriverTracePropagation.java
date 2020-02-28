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
import java.util.Collections;
import java.util.List;

/**
 * Stackdriver Trace propagation.
 *
 * <p>Tries to extract a trace ID and span ID using the B3 key set, such as {@code X-B3-TraceId}, {@code X-B3-SpanId},
 * etc. If not present, tries the {@code x-cloud-trace-context} key.
 *
 * <p>Uses {@link B3Propagation} injection, to inject the tracing context using B3 headers.
 */
public final class StackdriverTracePropagation<K> implements Propagation<K> {

  public static final Propagation.Factory FACTORY =
      new Propagation.Factory() {
        @Override
        public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
          return new StackdriverTracePropagation<>(keyFactory);
        }

        @Override
        public boolean supportsJoin() {
          return false;
        }

        @Override
        public boolean requires128BitTraceId() {
          return true;
        }

        @Override
        public String toString() {
          return "StackdriverTracePropagationFactory";
        }
      };

  /** 128 trace ID lower-hex encoded into 32 characters (required) */
  static final String TRACE_ID_NAME = "x-cloud-trace-context";

  final Propagation<K> b3Propagation;
  final K traceIdKey;
  final List<K> fields;

  StackdriverTracePropagation(KeyFactory<K> keyFactory) {
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.fields = Collections.singletonList(traceIdKey);
    this.b3Propagation = B3Propagation.FACTORY.create(keyFactory);
  }

  /**
   * Return the "x-cloud-trace-context" key.
   */
  public K getTraceIdKey() {
    return traceIdKey;
  }

  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
    return b3Propagation.injector(setter);
  }

  @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return CompositeExtractor.create(
            b3Propagation.extractor(getter), new XCloudTraceContextExtractor<>(this, getter));
  }
}
