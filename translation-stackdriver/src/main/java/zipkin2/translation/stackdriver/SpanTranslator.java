/**
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin2.translation.stackdriver;

import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.devtools.cloudtrace.v1.TraceSpan.SpanKind;
import com.google.protobuf.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin2.Span;

/**
 * SpanTranslator converts a Zipkin Span to a Stackdriver Trace Span.
 *
 * <p>It will rewrite span IDs so that multi-host Zipkin spans are converted to single-host
 * Stackdriver spans. Zipkin Spans with both server-side and client-side information will be
 * split into two Stackdriver Trace Spans where the client-side span is a parent of the
 * server-side span. Other parent-child relationships will be preserved.
 */
public final class SpanTranslator {
  private static final Logger LOG = Logger.getLogger(SpanTranslator.class.getName());

  static final LabelExtractor labelExtractor;

  static {
    Map<String, String> renamedLabels = new LinkedHashMap<>();
    renamedLabels.put("http.host", "/http/host");
    renamedLabels.put("http.method", "/http/method");
    renamedLabels.put("http.status_code", "/http/status_code");
    renamedLabels.put("http.request.size", "/request/size");
    renamedLabels.put("http.response.size", "/response/size");
    renamedLabels.put("http.url", "/http/url");
    labelExtractor = new LabelExtractor(renamedLabels);
  }

  /**
   * Converts a Zipkin Span into a Stackdriver Trace Span.
   *
   * Ex.
   * <pre>{@code
   * traceSpan = SpanTranslator.translate(TraceSpan.newBuilder(), zipkinSpan).build();
   * }</pre>
   *
   * <p>Note: the result does not include the trace ID from the input.
   *
   * @param spanBuilder the builder (to facilitate re-use)
   * @param zipkinSpan The Zipkin Span.
   * @return A Stackdriver Trace Span.
   */
  public static TraceSpan.Builder translate(TraceSpan.Builder spanBuilder, Span zipkinSpan) {
    boolean logTranslation = LOG.isLoggable(Level.FINE);
    if (logTranslation) LOG.fine(">> translating zipkin span: " + zipkinSpan);
    spanBuilder.setName(zipkinSpan.name() != null ? zipkinSpan.name() : "");
    SpanKind kind = getSpanKind(zipkinSpan.kind());
    spanBuilder.setKind(kind);
    rewriteIds(zipkinSpan, spanBuilder, kind);
    if (zipkinSpan.timestampAsLong() != 0L) {
      spanBuilder.setStartTime(createTimestamp(zipkinSpan.timestampAsLong()));
      if (zipkinSpan.durationAsLong() != 0L) {
        Timestamp endTime = createTimestamp(
            zipkinSpan.timestampAsLong() + zipkinSpan.durationAsLong()
        );
        spanBuilder.setEndTime(endTime);
      }
    }
    spanBuilder.putAllLabels(labelExtractor.extract(zipkinSpan));
    if (logTranslation) LOG.fine("<< translated to stackdriver span: " + spanBuilder);
    return spanBuilder;
  }

  /**
   * Rewrite Span IDs to split multi-host Zipkin spans into multiple single-host Stackdriver Trace
   * spans.
   */
  private static void rewriteIds(Span zipkinSpan, TraceSpan.Builder builder, SpanKind kind) {
    long id = parseUnsignedLong(zipkinSpan.id());
    long parentId = parseUnsignedLong(zipkinSpan.parentId());
    if (kind == SpanKind.RPC_CLIENT) {
      builder.setSpanId(rewriteId(id));
    } else {
      builder.setSpanId(id);
    }

    // Change the parentSpanId of RPC_SERVER spans to use the rewritten spanId of the RPC_CLIENT
    // spans.
    if (kind == SpanKind.RPC_SERVER) {
      if (Boolean.TRUE.equals(zipkinSpan.shared())) {
        // This is a multi-host span.
        // This means the parent client-side span has the same id as this span. When that fragment
        // of
        // the span was converted, it would have had id rewriteId(zipkinSpan.id)
        builder.setParentSpanId(rewriteId(id));
      } else {
        // This span isn't shared: the server "owns" this span and it is a single-host span.
        // This means the parent RPC_CLIENT span was a separate span with id=zipkinSpan.parentId.
        // When
        // that span fragment was converted, it would have had id=rewriteId(zipkinSpan.parentId)
        builder.setParentSpanId(rewriteId(parentId));
      }
    } else {
      builder.setParentSpanId(parentId);
    }
  }

  private static long parseUnsignedLong(String lowerHex) {
    if (lowerHex == null) return 0;
    long result = 0;
    for (int i = 0; i < 16; i++){
      char c = lowerHex.charAt(i);
      result <<= 4;
      if (c >= '0' && c <= '9') {
        result |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        result |= c - 'a' + 10;
      } else {
        return 0;
      }
    }
    return result;
  }

  private static long rewriteId(long id) {
    if (id == 0L) return 0;
    // To deterministically rewrite the ID, xor it with a random 64-bit constant.
    final long pad = 0x3f6a2ec3c810c2abL;
    return id ^ pad;
  }

  private static SpanKind getSpanKind(/* Nullable */ Span.Kind zipkinKind) {
    if (zipkinKind == null) return SpanKind.SPAN_KIND_UNSPECIFIED;
    if (zipkinKind == Span.Kind.CLIENT) {
      return SpanKind.RPC_CLIENT;
    }
    if (zipkinKind == Span.Kind.SERVER) {
      return SpanKind.RPC_SERVER;
    }
    return SpanKind.UNRECOGNIZED;
  }

  private static Timestamp createTimestamp(long microseconds) {
    long seconds = (microseconds / 1000000);
    int remainderMicros = (int) (microseconds % 1000000);
    int remainderNanos = remainderMicros * 1000;

    return Timestamp.newBuilder().setSeconds(seconds).setNanos(remainderNanos).build();
  }
}
