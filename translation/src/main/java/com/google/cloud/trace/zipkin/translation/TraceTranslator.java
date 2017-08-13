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

package com.google.cloud.trace.zipkin.translation;

import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import zipkin.Span;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;

/**
 * TraceTranslator converts a collection of Zipkin Spans into a Collection of Stackdriver Trace Spans.
 */
public class TraceTranslator {
  private final SpanTranslator translator;
  private final String projectId;

  /**
   * Create a TraceTranslator.
   * @param projectId The Google Cloud Platform projectId that should be used for Stackdriver Traces.
   */
  public TraceTranslator(String projectId) {
    this.translator = new SpanTranslator();
    this.projectId = projectId;
  }

  /**
   * Convert a Collection of Zipkin Spans into a Collection of Stackdriver Trace Spans.
   * @param zipkinSpans The Collection of Zipkin Spans.
   * @return A Collection of Stackdriver Trace Spans.
   */
  public Collection<Trace> translateSpans(Collection<Span> zipkinSpans) {
    List<Span> sortedByTraceAndSpanId = sortByTraceAndSpanId(zipkinSpans);
    Trace.Builder currentTrace = null;

    Collection<Trace> translatedTraces = new ArrayList<>();
    for (int i = 0, length = sortedByTraceAndSpanId.size(); i < length; i++) {
      Span currentSpan = sortedByTraceAndSpanId.get(i);

      // Zipkin trace ID is conditionally 16 or 32 characters, but Stackdriver needs 32
      String traceId = currentSpan.traceIdString();
      if (traceId.length() == 16) traceId = "0000000000000000" + traceId;

      if (currentTrace == null || !traceId.equals(currentTrace.getTraceId())) {
        finishTrace(currentTrace, translatedTraces);

        currentTrace = Trace.newBuilder();
        currentTrace.setProjectId(this.projectId);
        currentTrace.setTraceId(traceId);
      }

      // Clients and servers may report against the same span ID. Collect all fragments together.
      List<Span2> sameSpan = new ArrayList<>(Span2Converter.fromSpan(currentSpan));
      while (i + 1 < length) {
        Span peekedSpan = sortedByTraceAndSpanId.get(i + 1);
        if (currentSpan.traceId != peekedSpan.traceId || currentSpan.id != peekedSpan.id) break;
        sameSpan.addAll(Span2Converter.fromSpan(peekedSpan));
        i++;
      }

      for (Span2 span2 : sameSpan) {
        appendSpan(currentTrace, span2);
      }
    }
    finishTrace(currentTrace, translatedTraces);
    return translatedTraces;
  }

  private List<Span> sortByTraceAndSpanId(Collection<Span> input) {
    List<Span> result = new ArrayList<>(input);
    Collections.sort(result, new Comparator<Span>() {
      @Override
      public int compare(Span o1, Span o2) {
        int result = Long.compare(o1.traceId, o2.traceId);
        if (result != 0) return result;
        return Long.compare(o1.id, o2.id);
      }
    });
    return result;
  }

  private void appendSpan(Trace.Builder builder, Span2 zipkinSpan) {
    TraceSpan span = translator.translate(TraceSpan.newBuilder(), zipkinSpan).build();
    builder.addSpans(span);
  }

  private void finishTrace(Trace.Builder traceBuilder, Collection<Trace> convertedTraces) {
    if (traceBuilder != null) {
      convertedTraces.add(traceBuilder.build());
    }
  }
}
