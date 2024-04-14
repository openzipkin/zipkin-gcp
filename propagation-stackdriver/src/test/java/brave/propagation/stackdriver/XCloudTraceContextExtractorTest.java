/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.stackdriver;

import brave.propagation.B3Propagation;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class XCloudTraceContextExtractorTest {

  @Test void testExtractXCloudTraceContext_traceTrue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=1";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(4981115762139876185L);
    assertThat(context.context().sampled()).isTrue();
  }

  @Test void testExtractXCloudTraceContext_spanIdZero() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/0;o=1";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.traceIdContext().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.traceIdContext().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.traceIdContext().sampled()).isTrue();
  }

  @Test void testExtractXCloudTraceContext_traceFalse() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=0";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().sampled()).isFalse();
  }

  @Test void testExtractXCloudTraceContext_missingTraceTrueValue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185;o=";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(4981115762139876185L);
    assertThat(context.context().sampled()).isNull();
  }

  @Test void testExtractXCloudTraceContext_noTraceTrue() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/4981115762139876185";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(4981115762139876185L);
    assertThat(context.context().sampled()).isNull();
  }

  @Test void testExtractXCloudTraceContext_noSpanId() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317";
    XCloudTraceContextExtractor extractor =
        new XCloudTraceContextExtractor<>(
            B3Propagation.FACTORY.get(),
            (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.traceIdContext().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.traceIdContext().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.traceIdContext().sampled()).isNull();
  }

  @Test void testExtractXCloudTraceContext_unsignedLong() {
    String xCloudTraceContext = "8fd836bcfe241ee19a057679a77ba317/13804021222261907717";
    XCloudTraceContextExtractor extractor = new XCloudTraceContextExtractor<>(
                B3Propagation.FACTORY.get(), (request, key) -> xCloudTraceContext);

    TraceContextOrSamplingFlags context = extractor.extract(new Object());
    assertThat(context.context().traceId()).isEqualTo(-7348336952112078057L);
    assertThat(context.context().traceIdHigh()).isEqualTo(-8081649345970823455L);
    assertThat(context.context().spanId()).isEqualTo(-4642722851447643899L);
    assertThat(context.context().sampled()).isNull();
  }

  @Test void parseUnsignedLong() {
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
