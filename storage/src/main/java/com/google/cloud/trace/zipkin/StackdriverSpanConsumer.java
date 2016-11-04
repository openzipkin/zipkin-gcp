/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin;

import com.google.cloud.trace.v1.sink.TraceSink;
import com.google.cloud.trace.zipkin.translation.TraceTranslator;
import com.google.devtools.cloudtrace.v1.Trace;
import java.util.Collection;
import java.util.List;
import zipkin.Span;
import zipkin.storage.StorageAdapters.SpanConsumer;

/**
 * Consumes Zipkin spans, translates them to Stackdriver spans using a provided TraceTranslator, and
 * writes them a provided TraceSink.
 */
public class StackdriverSpanConsumer implements SpanConsumer {

  private final TraceTranslator translator;
  private final TraceSink sink;

  public StackdriverSpanConsumer(TraceTranslator translator, TraceSink sink) {
    this.translator = translator;
    this.sink = sink;
  }

  @Override
  public void accept(List<Span> spans) {
    Collection<Trace> traces = translator.translateSpans(spans);
    for (Trace t : traces) {
      sink.receive(t);
    }
  }
}
