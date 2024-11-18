/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Tags;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.reporter.stackdriver.brave.AttributesExtractor.toAttributeValue;
import static zipkin2.reporter.stackdriver.brave.SpanUtil.toTruncatableString;
import static zipkin2.reporter.stackdriver.brave.TestObjects.clientSpan;
import static zipkin2.reporter.stackdriver.brave.TestObjects.spring6ServerSpan;

class SpanTranslatorTest {
  SpanTranslator spanTranslator = new SpanTranslator(Tags.ERROR);

  /** This test is intentionally sensitive, so changing other parts makes obvious impact here */
  @Test void translate_clientSpan() {
    MutableSpan braveSpan = clientSpan();
    Span translated = spanTranslator.translate(Span.newBuilder(), braveSpan).build();

    assertThat(translated)
        .isEqualTo(
            Span.newBuilder()
                .setSpanId(braveSpan.id())
                .setParentSpanId(braveSpan.parentId())
                .setDisplayName(toTruncatableString("get"))
                .setStartTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(199_000_000).build())
                .setEndTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(406_000_000).build())
                .setAttributes(Span.Attributes.newBuilder()
                    .putAttributeMap("clnt/finagle.version", toAttributeValue("6.45.0"))
                    .putAttributeMap("http.path", toAttributeValue("/api"))
                    .putAttributeMap("/kind", toAttributeValue("client"))
                    .putAttributeMap("/component", toAttributeValue("frontend"))
                    .build())
                .setTimeEvents(Span.TimeEvents.newBuilder()
                    .addTimeEvent(Span.TimeEvent.newBuilder()
                        .setTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(238_000_000).build())
                        .setAnnotation(
                            Span.TimeEvent.Annotation.newBuilder()
                                .setDescription(toTruncatableString("foo"))
                                .build())
                        .build())
                    .addTimeEvent(Span.TimeEvent.newBuilder()
                        .setTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(403_000_000).build())
                        .setAnnotation(
                            Span.TimeEvent.Annotation.newBuilder()
                                .setDescription(toTruncatableString("bar"))
                                .build())
                        .build())
                    .build())
                .build());
  }

  @Test void translate_missingName() {
    MutableSpan braveSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(3).spanId(2).build(), null);
    Span translated = spanTranslator.translate(Span.newBuilder(), braveSpan).build();

    assertThat(translated.getDisplayName().getValue()).isEqualTo("unknown");
  }

  @Test void translate_emptyName() {
    MutableSpan braveSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(3).spanId(2).build(), null);
    braveSpan.name("");
    Span translated = spanTranslator.translate(Span.newBuilder(), braveSpan).build();

    assertThat(translated.getDisplayName().getValue()).isEqualTo("unknown");
  }

  @Test void translate_spring6ServerSpan() {
    MutableSpan braveSpan = spring6ServerSpan();
    Span translated = spanTranslator.translate(Span.newBuilder(), braveSpan).build();

    assertThat(translated)
            .isEqualTo(
                    Span.newBuilder()
                            .setSpanId(braveSpan.id())
                            .setDisplayName(toTruncatableString("http get /test"))
                            .setStartTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(199_000_000).build())
                            .setEndTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(406_000_000).build())
                            .setAttributes(Span.Attributes.newBuilder()
                                    .putAttributeMap("/agent", toAttributeValue("zipkin-java"))
                                    .putAttributeMap("exception", toAttributeValue("none"))
                                    .putAttributeMap("/http/url", toAttributeValue("/test"))
                                    .putAttributeMap("/http/method", toAttributeValue("GET"))
                                    .putAttributeMap("outcome", toAttributeValue("SUCCESS"))
                                    .putAttributeMap("/http/status_code", toAttributeValue("200"))
                                    .putAttributeMap("uri", toAttributeValue("/test"))
                                    .putAttributeMap("method", toAttributeValue("GET"))
                                    .putAttributeMap("status", toAttributeValue("200"))
                                    .putAttributeMap("/kind", toAttributeValue("server"))
                                    .putAttributeMap("/component", toAttributeValue("backend"))
                                    .putAttributeMap("endpoint.ipv4", toAttributeValue("127.0.0.1"))
                                    .build())
                            .setTimeEvents(Span.TimeEvents.newBuilder()
                                    .addTimeEvent(Span.TimeEvent.newBuilder()
                                            .setTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(238_000_000).build())
                                            .setAnnotation(
                                                    Span.TimeEvent.Annotation.newBuilder()
                                                            .setDescription(toTruncatableString("foo"))
                                                            .build())
                                            .build())
                                    .addTimeEvent(Span.TimeEvent.newBuilder()
                                            .setTime(Timestamp.newBuilder().setSeconds(1472470996).setNanos(403_000_000).build())
                                            .setAnnotation(
                                                    Span.TimeEvent.Annotation.newBuilder()
                                                            .setDescription(toTruncatableString("bar"))
                                                            .build())
                                            .build())
                                    .build())
                            .build());
  }
}
