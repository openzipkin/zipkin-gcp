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
package zipkin2.reporter.stackdriver.zipkin;

import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.translation.stackdriver.SpanTranslator;

import static org.assertj.core.api.Assertions.assertThat;

public class StackdriverEncoderTest {
  StackdriverEncoder encoder = StackdriverEncoder.V2;
  Span zipkinSpan = TestObjects.CLIENT_SPAN;

  @Test void sizeInBytes() {
    assertThat(encoder.sizeInBytes(zipkinSpan)).isEqualTo(encoder.encode(zipkinSpan).length);
  }

  @Test void sizeInBytes_64BitTraceId() {
    String traceId = "216a2aea45d08fc9";
    zipkinSpan = zipkinSpan.toBuilder().traceId(traceId).build();

    assertThat(encoder.sizeInBytes(zipkinSpan)).isEqualTo(encoder.encode(zipkinSpan).length);
  }

  @Test void encode_writesTraceIdPrefixedSpan() throws Exception {
    assertTraceIdPrefixedSpan(encoder.encode(zipkinSpan), zipkinSpan.traceId());
  }

  @Test void encode_writesPaddedTraceIdPrefixedSpan() throws Exception {
    String traceId = "216a2aea45d08fc9";
    zipkinSpan = zipkinSpan.toBuilder().traceId(traceId).build();

    assertTraceIdPrefixedSpan(encoder.encode(zipkinSpan), "0000000000000000216a2aea45d08fc9");
  }

  void assertTraceIdPrefixedSpan(byte[] serialized, String expectedTraceId) throws Exception {
    char[] traceId = new char[32];
    for (int i = 0; i < 32; i++) traceId[i] = (char) serialized[i];

    assertThat(new String(traceId)).isEqualTo(expectedTraceId);

    com.google.devtools.cloudtrace.v2.Span
        deserialized = com.google.devtools.cloudtrace.v2.Span.parser()
        .parseFrom(serialized, 32, serialized.length - 32);

    assertThat(deserialized)
        .isEqualTo(SpanTranslator.translate(
            com.google.devtools.cloudtrace.v2.Span.newBuilder(), zipkinSpan).build());
  }
}
