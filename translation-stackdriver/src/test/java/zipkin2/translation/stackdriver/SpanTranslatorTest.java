/*
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

import com.google.protobuf.Timestamp;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.translation.stackdriver.AttributesExtractor.toAttributeValue;
import static zipkin2.translation.stackdriver.SpanTranslator.createTimestamp;
import static zipkin2.translation.stackdriver.SpanUtil.toTruncatableStringProto;

public class SpanTranslatorTest {
  /** This test is intentionally sensitive, so changing other parts makes obvious impact here */
  @Test
  public void translate_clientSpan() {
    Span zipkinSpan =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .parentId("6b221d5bc9e6496c")
            .id("5b4185666d50f68b")
            .name("get")
            .kind(Span.Kind.CLIENT)
            .localEndpoint(Endpoint.newBuilder().serviceName("frontend").build())
            .remoteEndpoint(
                Endpoint.newBuilder()
                    .serviceName("backend")
                    .ip("192.168.99.101")
                    .port(9000)
                    .build())
            .timestamp(1_000_000L) // 1 second after epoch
            .duration(123_456L)
            .addAnnotation(1_123_000L, "foo")
            .putTag("http.path", "/api")
            .putTag("clnt/finagle.version", "6.45.0")
            .build();

    com.google.devtools.cloudtrace.v2.Span
        translated = SpanTranslator.translate(
            com.google.devtools.cloudtrace.v2.Span.newBuilder(), zipkinSpan).build();

    assertThat(translated)
        .isEqualTo(
            com.google.devtools.cloudtrace.v2.Span.newBuilder()
                .setSpanId(zipkinSpan.id())
                .setParentSpanId(zipkinSpan.parentId())
                .setDisplayName(toTruncatableStringProto("get"))
                .setStartTime(Timestamp.newBuilder().setSeconds(1).build())
                .setEndTime(Timestamp.newBuilder().setSeconds(1).setNanos(123_456_000).build())
                .setAttributes(com.google.devtools.cloudtrace.v2.Span.Attributes.newBuilder()
                    .putAttributeMap("clnt/finagle.version", toAttributeValue("6.45.0"))
                    .putAttributeMap("http.path", toAttributeValue("/api"))
                    .putAttributeMap("/kind", toAttributeValue("client"))
                    .putAttributeMap("/component", toAttributeValue("frontend"))
                    .build())
                .setTimeEvents(com.google.devtools.cloudtrace.v2.Span.TimeEvents.newBuilder()
                    .addTimeEvent(com.google.devtools.cloudtrace.v2.Span.TimeEvent.newBuilder()
                        .setTime(createTimestamp(1_123_000L))
                        .setAnnotation(
                            com.google.devtools.cloudtrace.v2.Span.TimeEvent.Annotation.newBuilder()
                                .setDescription(toTruncatableStringProto("foo"))
                                .build())
                        .build())
                    .build())
                .build());
  }

  @Test
  public void translate_missingName() {
    Span zipkinSpan = Span.newBuilder().traceId("3").id("2").build();
    com.google.devtools.cloudtrace.v2.Span translated = SpanTranslator.translate(
        com.google.devtools.cloudtrace.v2.Span.newBuilder(), zipkinSpan).build();

    assertThat(translated.getDisplayName().getValue()).isEmpty();
  }
}
