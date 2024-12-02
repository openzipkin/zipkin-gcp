/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Tag;
import brave.handler.MutableSpan;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.Span.TimeEvent;
import com.google.devtools.cloudtrace.v2.Span.TimeEvents;
import com.google.protobuf.Timestamp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static zipkin2.reporter.stackdriver.brave.AttributesExtractor.toAttributeValue;
import static zipkin2.reporter.stackdriver.brave.SpanUtil.toTruncatableString;

/** SpanTranslator converts a Zipkin Span to a Stackdriver Trace Span. */
final class SpanTranslator {
  private static final Logger LOG = Logger.getLogger(SpanTranslator.class.getName());

  private static final Map<String, String> RENAMED_LABELS;

  static {
    RENAMED_LABELS = new LinkedHashMap<>();
    RENAMED_LABELS.put("http.host", "/http/host");
    RENAMED_LABELS.put("http.method", "/http/method");
    RENAMED_LABELS.put("http.status_code", "/http/status_code");
    RENAMED_LABELS.put("http.request.size", "/request/size");
    RENAMED_LABELS.put("http.response.size", "/response/size");
    RENAMED_LABELS.put("http.url", "/http/url");
  }

  private static final Map<String, String> SPRING6_RENAMED_HTTP_LABELS;

  static {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("status", "/http/status_code");
    map.put("method", "/http/method");
    SPRING6_RENAMED_HTTP_LABELS = Collections.unmodifiableMap(map);
  }

  private final AttributesExtractor attributesExtractor;

  SpanTranslator(Tag<Throwable> errorTag) {
    this.attributesExtractor = new AttributesExtractor(errorTag, RENAMED_LABELS);
  }

  /**
   * Converts a Zipkin Span into a Stackdriver Trace Span.
   *
   * <p>Ex.
   *
   * <pre>{@code
   * traceSpan = SpanTranslator.translate(TraceSpan.newBuilder(), braveSpan).build();
   * }</pre>
   *
   * <p>Note: the result does not set {@link Span.Builder#setName(String)}
   * and it is up to callers to make sure to fill it using the project ID and trace ID.
   *
   * @param spanBuilder the builder (to facilitate re-use)
   * @param braveSpan   The Zipkin Span.
   * @return A Stackdriver Trace Span.
   */
  Span.Builder translate(Span.Builder spanBuilder, MutableSpan braveSpan) {
    boolean logTranslation = LOG.isLoggable(FINE);
    if (logTranslation) LOG.log(FINE, ">> translating zipkin span: {0}", braveSpan);

    spanBuilder.setSpanId(braveSpan.id());
    if (braveSpan.parentId() != null) {
      spanBuilder.setParentSpanId(braveSpan.parentId());
    }

    // NOTE: opencensus prefixes Send. and Recv. based on Kind. For now we reproduce our V1 behavior
    // of using the span name as the display name as is.
    spanBuilder.setDisplayName(
        toTruncatableString(
            (braveSpan.name() != null && !braveSpan.name().isEmpty()) ? braveSpan.name()
                : "unknown"));

    if (braveSpan.startTimestamp() != 0L) {
      spanBuilder.setStartTime(createTimestamp(braveSpan.startTimestamp()));
      if (braveSpan.finishTimestamp() != 0L) {
        spanBuilder.setEndTime(createTimestamp(braveSpan.finishTimestamp()));
      }
    }
    spanBuilder.setAttributes(attributesExtractor.extract(braveSpan));

    // Spring 6 HTTP spans need mapping to Stackdriver conventional attribute names
    if (braveSpan.name() != null && braveSpan.name().contains("http")) {
      braveSpan.tags().forEach((key, value) -> {
        if (SPRING6_RENAMED_HTTP_LABELS.containsKey(key)) {
          spanBuilder.getAttributesBuilder()
                  .putAttributeMap(SPRING6_RENAMED_HTTP_LABELS.get(key), toAttributeValue(value));
        }
      });
    }

    if (braveSpan.annotationCount() > 0) {
      TimeEvents.Builder events = TimeEvents.newBuilder();
      braveSpan.forEachAnnotation(SpanTranslator::addAnnotation, events);
      spanBuilder.setTimeEvents(events);
    }
    if (logTranslation) LOG.log(FINE, "<< translated to stackdriver span: {0}", spanBuilder);
    return spanBuilder;
  }

  static void addAnnotation(TimeEvents.Builder target, long timestamp, String value) {
    target.addTimeEvent(TimeEvent.newBuilder()
        .setTime(createTimestamp(timestamp))
        .setAnnotation(TimeEvent.Annotation.newBuilder()
            .setDescription(toTruncatableString(value))));
  }

  static Timestamp createTimestamp(long microseconds) {
    long seconds = (microseconds / 1000000);
    int remainderMicros = (int) (microseconds % 1000000);
    int remainderNanos = remainderMicros * 1000;

    return Timestamp.newBuilder().setSeconds(seconds).setNanos(remainderNanos).build();
  }
}
