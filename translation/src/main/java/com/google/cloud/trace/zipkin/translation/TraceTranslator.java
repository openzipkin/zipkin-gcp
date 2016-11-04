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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import zipkin.Span;

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
    List<Span> groupedSpans = new ArrayList<>(zipkinSpans);
    groupSpansByTraceId(groupedSpans);

    Trace.Builder currentTrace = null;

    Collection<Trace> translatedTraces = new ArrayList<>();
    for (Span zipkinSpan : groupedSpans) {
      String traceId = convertTraceId(zipkinSpan.traceId);
      if (currentTrace == null || !traceId.equals(currentTrace.getTraceId())) {
        finishTrace(currentTrace, translatedTraces);

        currentTrace = Trace.newBuilder();
        currentTrace.setProjectId(this.projectId);
        currentTrace.setTraceId(traceId);
      }
      appendSpan(currentTrace, zipkinSpan);
    }
    finishTrace(currentTrace, translatedTraces);
    return translatedTraces;
  }

  private void groupSpansByTraceId(List<Span> spans) {
    Collections.sort(spans, new Comparator<Span>() {
      @Override
      public int compare(Span o1, Span o2) {
        return Long.compare(o1.traceId, o2.traceId);
      }
    });
  }

  private void appendSpan(Trace.Builder builder, Span zipkinSpan) {
    TraceSpan span = translator.translate(zipkinSpan);
    builder.addSpans(span);
  }

  private void finishTrace(Trace.Builder traceBuilder, Collection<Trace> convertedTraces) {
    if (traceBuilder != null) {
      convertedTraces.add(traceBuilder.build());
    }
  }

  private String convertTraceId(long zipkinTraceId) {
    // Stackdriver trace ID's are 128 bits = 16 bytes * 8
    ByteBuffer idBuffer = ByteBuffer.allocate(16);
    idBuffer.putLong(0);
    idBuffer.putLong(zipkinTraceId);
    StringBuilder idBuilder = new StringBuilder();
    for (byte b : idBuffer.array()) {
      idBuilder.append(String.format("%02x", b));
    }
    return idBuilder.toString();
  }
}
