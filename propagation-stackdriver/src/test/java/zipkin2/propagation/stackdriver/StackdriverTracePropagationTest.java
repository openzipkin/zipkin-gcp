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
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StackdriverTracePropagationTest {
  private static final String XCLOUD_VALUE = "c108dc108dc108dc108dc108dc108d00";
  private static final String B3_HEADER = "b3";
  private static final String B3_TRACE_ID = "b3b3b3b3b3b34da6a3ce929d0e0e4736";
  private static final String B3_VALUE = String.format("%s-00f067aa0ba902b7-1", B3_TRACE_ID);

  private Propagation<String> propagation =
      StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING);

  @Test
  public void b3TakesPrecedenceOverXCloud() {
    TraceContext.Extractor<String> extractor =
        propagation.extractor(new FakeGetter(XCLOUD_VALUE, B3_VALUE));
    TraceContextOrSamplingFlags ctx = extractor.extract("unused object");
    assertThat(ctx.context().traceIdString()).isEqualTo(B3_TRACE_ID);
  }

  @Test
  public void xCloudReturnedWhenB3Missing() {
    TraceContext.Extractor<String> extractor =
        propagation.extractor(new FakeGetter(XCLOUD_VALUE, null));
    TraceContextOrSamplingFlags ctx = extractor.extract("unused object");
    assertThat(ctx.context().traceIdString()).isEqualTo(XCLOUD_VALUE);
  }

  @Test
  public void b3ReturnedWhenXCloudMissing() {
    TraceContext.Extractor<String> extractor =
        propagation.extractor(new FakeGetter(null, B3_VALUE));
    TraceContextOrSamplingFlags ctx = extractor.extract("unused object");
    assertThat(ctx.context().traceIdString()).isEqualTo(B3_TRACE_ID);
  }

  private static class FakeGetter implements Propagation.Getter<String, String> {
    private Map<String, String> values = new HashMap<>();

    FakeGetter(String xCloudValue, String b3Value) {
      this.values.put(StackdriverTracePropagation.TRACE_ID_NAME, xCloudValue);
      this.values.put(B3_HEADER, b3Value);
    }

    @Override
    public String get(String carrier, String key) {
      return values.get(key);
    }
  }
}
