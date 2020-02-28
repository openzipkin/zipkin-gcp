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

import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

/**
 * Composite extractor that tries several extractors, in order, to retrieve the tracing context from
 * a source.
 */
final class CompositeExtractor<C> implements TraceContext.Extractor<C> {
  static <C> CompositeExtractor<C> create(TraceContext.Extractor<C>... extractors) {
    if (extractors == null) throw new NullPointerException("extractors == null");
    if (extractors.length == 0) throw new NullPointerException("extractors are empty ");
    TraceContext.Extractor<C>[] extractorsCopy = new TraceContext.Extractor[extractors.length];
    System.arraycopy(extractors, 0, extractorsCopy, 0, extractors.length);
    return new CompositeExtractor<>(extractorsCopy);
  }

  final TraceContext.Extractor<C>[] extractors;

  CompositeExtractor(TraceContext.Extractor<C>... extractors) {
    this.extractors = extractors;
  }

  @Override public TraceContextOrSamplingFlags extract(C carrier) {
    TraceContextOrSamplingFlags context = TraceContextOrSamplingFlags.EMPTY;

    int currentExtractor = 0;
    while (context == TraceContextOrSamplingFlags.EMPTY && currentExtractor < extractors.length) {
      context = extractors[currentExtractor++].extract(carrier);
    }

    return context;
  }
}
