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
package zipkin2.reporter.stackdriver;

import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;

public class StackdriverEncoderTest {
  StackdriverEncoder encoder = StackdriverEncoder.V1;
  Span zipkinSpan = TestObjects.CLIENT_SPAN;

  @Test public void sizeInBytes() {
    assertThat(encoder.sizeInBytes(zipkinSpan))
        .isEqualTo(encoder.encode(zipkinSpan).length);
  }

  @Test public void encode_makesSingleEntryTraceWithoutProjectId() throws Exception {
    byte[] serialized = encoder.encode(zipkinSpan);
    Trace deserialized = Trace.parseFrom(serialized);

    assertThat(deserialized.getTraceId())
        .isEqualTo(zipkinSpan.traceId());

    assertThat(deserialized.getProjectId())
        .isEqualTo(Trace.getDefaultInstance().getProjectId());

    // only testing the essence here, as other tests are in span translator
    assertThat(deserialized.getSpansList())
        .hasSize(1)
        .extracting(TraceSpan::getName)
        .containsExactly(zipkinSpan.name());
  }

  @Test public void sizeInBytes_64BitTraceId() {
    String traceId = "216a2aea45d08fc9";
    zipkinSpan = zipkinSpan.toBuilder().traceId(traceId).build();

    assertThat(encoder.sizeInBytes(zipkinSpan))
        .isEqualTo(encoder.encode(zipkinSpan).length);
  }

  @Test public void encode_64BitTraceId() throws Exception {
    String traceId = "216a2aea45d08fc9";
    zipkinSpan = zipkinSpan.toBuilder().traceId(traceId).build();

    byte[] serialized = encoder.encode(zipkinSpan);
    Trace deserialized = Trace.parseFrom(serialized);

    assertThat(deserialized.getTraceId())
        .hasSize(32)
        .startsWith("0000000000000000")
        .endsWith(traceId);
  }
}
