/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
package zipkin2.reporter.stackdriver.brave;

import brave.Tag;
import brave.handler.MutableSpan;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.Span.TimeEvent;
import com.google.devtools.cloudtrace.v2.Span.TimeEvents;
import com.google.protobuf.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
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
