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
package zipkin2.propagation.stackdriver;

import brave.propagation.Propagation;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class XCloudTraceContextExtractorTest {

  @Test
  public void testExtractXCloudTraceContext_traceTrue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=1";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            (StackdriverTracePropagation)
                StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
            (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(4981115762139876185L);
  }

  @Test
  public void testExtractXCloudTraceContext_traceFalse() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=0";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            (StackdriverTracePropagation)
                StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
            (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context).isEqualTo(TraceContextOrSamplingFlags.EMPTY);
  }

  @Test
  public void testExtractXCloudTraceContext_invalidTraceTrue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            (StackdriverTracePropagation)
                StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
            (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context).isEqualTo(TraceContextOrSamplingFlags.EMPTY);
  }

  @Test
  public void testExtractXCloudTraceContext_noTraceTrue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            (StackdriverTracePropagation)
                StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
            (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(4981115762139876185L);
  }

  @Test
  public void testExtractXCloudTraceContext_noSpanId() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            (StackdriverTracePropagation)
                StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
            (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(1L);
  }

  @Test
  public void testExtractXCloudTraceContext_unsignedLong() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/13804021222261907717";
    XCloudTraceContextExtractor extractor =
            new XCloudTraceContextExtractor<>(
                    (StackdriverTracePropagation)
                            StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING),
                    (carrier, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(-4642722851447643899L);
  }

  @Test
  public void parseUnsignedLong() {
    // max int64
    assertThat(XCloudTraceContextExtractor.parseUnsignedLong("9223372036854775807"))
            .isEqualTo(Long.parseUnsignedLong("9223372036854775807"));

    // max int64 + 1
    assertThat(XCloudTraceContextExtractor.parseUnsignedLong("9223372036854775808"))
            .isEqualTo(Long.parseUnsignedLong("9223372036854775808"));

    // max uint64
    assertThat(XCloudTraceContextExtractor.parseUnsignedLong("18446744073709551615"))
            .isEqualTo(Long.parseUnsignedLong("18446744073709551615"));

    // max uint64 + 1
    try {
      XCloudTraceContextExtractor.parseUnsignedLong("18446744073709551616");
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }
  }
}
