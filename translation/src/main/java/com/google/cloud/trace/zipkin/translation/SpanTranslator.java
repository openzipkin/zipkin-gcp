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

import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.devtools.cloudtrace.v1.TraceSpan.SpanKind;
import com.google.protobuf.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import zipkin.Span;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;

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
    TraceSpan.Builder builder = TraceSpan.newBuilder();
    for (Span2 span : Span2Converter.fromSpan(zipkinSpan)) {
      translate(builder, span);
    }
    return builder.build();
  }

  TraceSpan.Builder translate(TraceSpan.Builder spanBuilder, Span2 zipkinSpan) {
    spanBuilder.setName(zipkinSpan.name());
    SpanKind kind = getSpanKind(zipkinSpan.kind());
    spanBuilder.setKind(kind);
    rewriteIds(zipkinSpan, spanBuilder, kind);
    if (zipkinSpan.timestamp() != null) {
      spanBuilder.setStartTime(createTimestamp(zipkinSpan.timestamp()));
      if (zipkinSpan.duration() != null) {
        Timestamp endTime = createTimestamp(zipkinSpan.timestamp() + zipkinSpan.duration());
        spanBuilder.setEndTime(endTime);
      }
    }
    spanBuilder.putAllLabels(labelExtractor.extract(zipkinSpan));
    return spanBuilder;
  }

  /**
   * Rewrite Span IDs to split multi-host Zipkin spans into multiple single-host Stackdriver spans.
   */
  private void rewriteIds(Span2 zipkinSpan, TraceSpan.Builder builder, SpanKind kind) {
    // Change the spanId of RPC_CLIENT spans.
    if (kind == SpanKind.RPC_CLIENT) {
      builder.setSpanId(rewriteId(zipkinSpan.id()));
    } else {
      builder.setSpanId(zipkinSpan.id());
    }

    // Change the parentSpanId of RPC_SERVER spans to use the rewritten spanId of the RPC_CLIENT spans.
    if (kind == SpanKind.RPC_SERVER) {
      if (Boolean.TRUE.equals(zipkinSpan.shared())) {
        // This is a multi-host span.
        // This means the parent client-side span has the same id as this span. When that fragment of
        // the span was converted, it would have had id rewriteId(zipkinSpan.id)
        builder.setParentSpanId(rewriteId(zipkinSpan.id()));
      } else {
        // This span isn't shared: the server "owns" this span and it is a single-host span.
        // This means the parent RPC_CLIENT span was a separate span with id=zipkinSpan.parentId. When
        // that span fragment was converted, it would have had id=rewriteId(zipkinSpan.parentId)
        builder.setParentSpanId(rewriteId(zipkinSpan.parentId()));
      }
    } else {
      long parentId = zipkinSpan.parentId() == null ? 0 : zipkinSpan.parentId();
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

  private SpanKind getSpanKind(@Nullable Span2.Kind zipkinKind) {
    if (zipkinKind == null) return SpanKind.SPAN_KIND_UNSPECIFIED;
    if (zipkinKind == Span2.Kind.CLIENT) {
      return SpanKind.RPC_CLIENT;
    }
    if (zipkinKind == Span2.Kind.SERVER) {
      return SpanKind.RPC_SERVER;
    }
    return SpanKind.UNRECOGNIZED;
  }

  private Timestamp createTimestamp(long microseconds) {
    long seconds = (microseconds / 1000000);
    int remainderMicros = (int)(microseconds % 1000000);
    int remainderNanos = remainderMicros * 1000;

    return Timestamp.newBuilder().setSeconds(seconds).setNanos(remainderNanos).build();
  }
}
