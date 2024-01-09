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

import brave.Tags;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.reporter.stackdriver.brave.AttributesExtractor.toAttributeValue;
import static zipkin2.reporter.stackdriver.brave.SpanTranslator.createTimestamp;
import static zipkin2.reporter.stackdriver.brave.SpanUtil.toTruncatableString;
import static zipkin2.reporter.stackdriver.brave.TestObjects.clientSpan;

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
}
