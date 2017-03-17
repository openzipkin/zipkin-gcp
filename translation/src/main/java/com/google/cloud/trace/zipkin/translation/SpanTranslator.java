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

import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;

import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.devtools.cloudtrace.v1.TraceSpan.SpanKind;
import com.google.protobuf.Timestamp;
import java.util.HashMap;
import java.util.Map;
import zipkin.Annotation;
import zipkin.Span;

/**
 * SpanTranslator converts a Zipkin Span to a Stackdriver Trace Span.
 *
 * It will rewrite span IDs so that multi-host Zipkin spans are converted to single-host
 * Stackdriver spans. Zipkin Spans with both server-side and client-side information will be split
 * into two Stackdriver Trace Spans where the client-side span is a parent of the server-side span.
 * Other parent-child relationships will be preserved.
 */
class SpanTranslator {

  private final LabelExtractor labelExtractor;

  /**
   * Create a SpanTranslator.
   */
  public SpanTranslator() {
    Map<String, String> renamedLabels = new HashMap<>();
    renamedLabels.put("http.host", "/http/host");
    renamedLabels.put("http.method", "/http/method");
    renamedLabels.put("http.status_code", "/http/status_code");
    renamedLabels.put("http.request.size", "/request/size");
    renamedLabels.put("http.response.size", "/response/size");
    renamedLabels.put("http.url", "/http/url");
    this.labelExtractor = new LabelExtractor(renamedLabels);
  }

  /**
   * Converts a Zipkin Span into a Stackdriver Trace Span.
   * @param zipkinSpan The Zipkin Span.
   * @return A Stackdriver Trace Span.
   */
  public TraceSpan translate(Span zipkinSpan) {
    Map<String, Annotation> annotations = getAnnotations(zipkinSpan);
    TraceSpan.Builder spanBuilder = TraceSpan.newBuilder();

    spanBuilder.setName(zipkinSpan.name);
    SpanKind kind = getSpanKind(annotations);
    spanBuilder.setKind(kind);
    rewriteIds(zipkinSpan, spanBuilder, kind);
    writeBestTimestamp(zipkinSpan, spanBuilder, annotations);
    spanBuilder.putAllLabels(labelExtractor.extract(zipkinSpan));
    return spanBuilder.build();
  }

  private void writeBestTimestamp(Span zipkinSpan, TraceSpan.Builder spanBuilder, Map<String, Annotation> annotations) {
    if (zipkinSpan.timestamp != null) {
      // Span.timestamp is the authoritative value if it's present.
      spanBuilder.setStartTime(createTimestamp(zipkinSpan.timestamp));
      spanBuilder.setEndTime(createTimestamp(zipkinSpan.timestamp + zipkinSpan.duration));
    } else if (annotations.containsKey(CLIENT_SEND) && annotations.containsKey(CLIENT_RECV)) {
      // Client timestamps are more authoritative than server timestamps.
      spanBuilder.setStartTime(
          createTimestamp(annotations.get(CLIENT_SEND).timestamp)
      );
      spanBuilder.setEndTime(
          createTimestamp(annotations.get(CLIENT_RECV).timestamp)
      );
    } else if (annotations.containsKey(SERVER_RECV) && annotations.containsKey(SERVER_SEND)) {
      spanBuilder.setStartTime(
          createTimestamp(annotations.get(SERVER_RECV).timestamp)
      );
      spanBuilder.setEndTime(
          createTimestamp(annotations.get(SERVER_SEND).timestamp)
      );
    }
  }

  /**
   * Rewrite Span IDs to split multi-host Zipkin spans into multiple single-host Stackdriver spans.
   */
  private void rewriteIds(Span zipkinSpan, TraceSpan.Builder builder, SpanKind kind) {
    // Change the spanId of RPC_CLIENT spans.
    if (kind == SpanKind.RPC_CLIENT) {
      builder.setSpanId(rewriteId(zipkinSpan.id));
    } else {
      builder.setSpanId(zipkinSpan.id);
    }

    // Change the parentSpanId of RPC_SERVER spans to use the rewritten spanId of the RPC_CLIENT spans.
    if (kind == SpanKind.RPC_SERVER) {
      if (zipkinSpan.timestamp != null ) {
        // The timestamp field should only be written by instrumentation whenever it "owns" a span.
        // Because this field is here, we know that the server "owns" this span which implies this
        // is a single-host span.
        // This means the parent RPC_CLIENT span was a separate span with id=zipkinSpan.parentId. When
        // that span fragment was converted, it would have had id=rewriteId(zipkinSpan.parentId)
        builder.setParentSpanId(rewriteId(zipkinSpan.parentId));
      } else {
        // This is a multi-host span.
        // This means the parent client-side span has the same id as this span. When that fragment of
        // the span was converted, it would have had id rewriteId(zipkinSpan.id)
        builder.setParentSpanId(rewriteId(zipkinSpan.id));
      }
    } else {
      long parentId = zipkinSpan.parentId == null ? 0 : zipkinSpan.parentId;
      builder.setParentSpanId(parentId);
    }
  }

  private long rewriteId(Long id) {
    if (id == null) {
      return 0;
    }
    // To deterministically rewrite the ID, xor it with a random 64-bit constant.
    final long pad = 0x3f6a2ec3c810c2abL;
    return id ^ pad;
  }

  private SpanKind getSpanKind(Map<String, Annotation> annotations) {
    if (annotations.containsKey(CLIENT_SEND) || annotations.containsKey(CLIENT_RECV)) {
      return SpanKind.RPC_CLIENT;
    }
    if (annotations.containsKey(SERVER_SEND) || annotations.containsKey(SERVER_RECV)) {
      return SpanKind.RPC_SERVER;
    }
    return SpanKind.SPAN_KIND_UNSPECIFIED;
  }

  private Map<String, Annotation> getAnnotations(Span zipkinSpan) {
    Map<String, Annotation> annotations = new HashMap<>();
    for (Annotation annotation : zipkinSpan.annotations) {
      annotations.put(annotation.value, annotation);
    }
    return annotations;
  }

  private Timestamp createTimestamp(long microseconds) {
    long seconds = (microseconds / 1000000);
    int remainderMicros = (int)(microseconds % 1000000);
    int remainderNanos = remainderMicros * 1000;

    return Timestamp.newBuilder().setSeconds(seconds).setNanos(remainderNanos).build();
  }
}
