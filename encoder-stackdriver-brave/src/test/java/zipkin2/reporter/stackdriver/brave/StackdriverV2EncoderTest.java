/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Tags;
import brave.handler.MutableSpan;
import com.google.devtools.cloudtrace.v2.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackdriverV2EncoderTest {
  StackdriverV2Encoder encoder = new StackdriverV2Encoder(Tags.ERROR);
  MutableSpan braveSpan = TestObjects.clientSpan();

  @Test void sizeInBytes() {
    assertThat(encoder.sizeInBytes(braveSpan)).isEqualTo(encoder.encode(braveSpan).length);
  }

  @Test void sizeInBytes_64BitTraceId() {
    braveSpan.traceId("216a2aea45d08fc9");

    assertThat(encoder.sizeInBytes(braveSpan)).isEqualTo(encoder.encode(braveSpan).length);
  }

  @Test void encode_writesTraceIdPrefixedSpan() throws Exception {
    assertTraceIdPrefixedSpan(encoder.encode(braveSpan), braveSpan.traceId());
  }

  @Test void encode_writesPaddedTraceIdPrefixedSpan() throws Exception {
    braveSpan.traceId("216a2aea45d08fc9");
    assertTraceIdPrefixedSpan(encoder.encode(braveSpan), "0000000000000000216a2aea45d08fc9");
  }

  void assertTraceIdPrefixedSpan(byte[] serialized, String expectedTraceId) throws Exception {
    char[] traceId = new char[32];
    for (int i = 0; i < 32; i++) traceId[i] = (char) serialized[i];

    assertThat(new String(traceId)).isEqualTo(expectedTraceId);

    Span deserialized = Span.parser()
        .parseFrom(serialized, 32, serialized.length - 32);

    assertThat(deserialized)
        .isEqualTo(encoder.translate(braveSpan));
  }
}
