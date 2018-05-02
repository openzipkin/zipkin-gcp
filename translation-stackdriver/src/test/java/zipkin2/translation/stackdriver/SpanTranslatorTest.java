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
import com.google.protobuf.Timestamp;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanTranslatorTest {
  /** This test is intentionally sensitive, so changing other parts makes obvious impact here */
  @Test public void translate_clientSpan() {
    Span zipkinSpan = Span.newBuilder()
        .traceId("7180c278b62e8f6a216a2aea45d08fc9")
        .parentId("6b221d5bc9e6496c")
        .id("5b4185666d50f68b")
        .name("get")
        .kind(Span.Kind.CLIENT)
        .localEndpoint(Endpoint.newBuilder().serviceName("frontend").build())
        .remoteEndpoint(
            Endpoint.newBuilder().serviceName("backend").ip("192.168.99.101").port(9000).build()
        )
        .timestamp(1_000_000L) // 1 second after epoch
        .duration(123_456L)
        .addAnnotation(1_123_000L, "foo")
        .putTag("http.path", "/api")
        .putTag("clnt/finagle.version", "6.45.0")
        .build();

    TraceSpan translated = SpanTranslator.translate(TraceSpan.newBuilder(), zipkinSpan).build();

    assertThat(translated).isEqualTo(
        TraceSpan.newBuilder()
            // client spans have their id rewritten with a consistent function
            .setSpanId(Long.parseUnsignedLong(zipkinSpan.id(), 16) ^ 0x3f6a2ec3c810c2abL)
            .setParentSpanId(Long.parseUnsignedLong(zipkinSpan.parentId(), 16))
            .setKind(TraceSpan.SpanKind.RPC_CLIENT)
            .setName("get")
            .setStartTime(Timestamp.newBuilder().setSeconds(1).build())
            .setEndTime(Timestamp.newBuilder().setSeconds(1).setNanos(123_456_000).build())
            .putLabels("zipkin.io/clnt/finagle.version", "6.45.0")
            .putLabels("zipkin.io/http.path", "/api")
            .putLabels("/component", "frontend")
            // annotations are written with UTC datestamps
            .putLabels("zipkin.io/foo", "1970-01-01 (00:00:01.123)")
            .build()
    );
  }

  @Test public void translate_missingName() {
    Span zipkinSpan = Span.newBuilder().traceId("3").id("2").build();
    TraceSpan translated = SpanTranslator.translate(TraceSpan.newBuilder(), zipkinSpan).build();

    assertThat(translated.getName())
        .isEqualTo(TraceSpan.getDefaultInstance().getName())
        .isEmpty();
  }

  @Test public void translate_consumerProducerSpan() {
    assertThat(SpanTranslator.translate(TraceSpan.newBuilder(),
            Span.newBuilder().traceId("2").id("3").kind(Span.Kind.CONSUMER).build())
            .build().getKind())
            .isEqualTo(TraceSpan.SpanKind.SPAN_KIND_UNSPECIFIED);
    assertThat(SpanTranslator.translate(TraceSpan.newBuilder(),
            Span.newBuilder().traceId("2").id("3").kind(Span.Kind.PRODUCER).build())
            .build().getKind())
            .isEqualTo(TraceSpan.SpanKind.SPAN_KIND_UNSPECIFIED);
  }
}
