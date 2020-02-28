/*
 * Copyright 2016-2020 The OpenZipkin Authors
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

import java.util.HashMap;
import java.util.Map;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StackdriverTracePropagationTest {
  static final String XCLOUD_TRACE_ID = "c108dc108dc108dc108dc108dc108d00";
  static final String XCLOUD_VALUE = XCLOUD_TRACE_ID + "/1234";

  static final String B3_HEADER = "b3";
  static final String B3_TRACE_ID = "b3b3b3b3b3b34da6a3ce929d0e0e4736";
  static final String B3_VALUE =  B3_TRACE_ID + "-00f067aa0ba902b7-1";

  Propagation<String> propagation =
      StackdriverTracePropagation.FACTORY.create(Propagation.KeyFactory.STRING);
  TraceContext.Extractor<Map<String,String>> extractor = propagation.extractor(Map::get);

  @Test
  public void b3TakesPrecedenceOverXCloud() {

    Map<String, String> headers = new HashMap<>();
    headers.put(StackdriverTracePropagation.TRACE_ID_NAME, XCLOUD_VALUE);
    headers.put(B3_HEADER, B3_VALUE);

    TraceContextOrSamplingFlags ctx = extractor.extract(headers);

    assertThat(ctx.context().traceIdString()).isEqualTo(B3_TRACE_ID);
  }

  @Test
  public void xCloudReturnedWhenB3Missing() {
    Map<String, String> headers = new HashMap<>();
    headers.put(StackdriverTracePropagation.TRACE_ID_NAME, XCLOUD_VALUE);

    TraceContextOrSamplingFlags ctx = extractor.extract(headers);

    assertThat(ctx.context().traceIdString()).isEqualTo(XCLOUD_TRACE_ID);
  }

  @Test
  public void b3ReturnedWhenXCloudMissing() {
    Map<String, String> headers = new HashMap<>();
    headers.put(B3_HEADER, B3_VALUE);

    TraceContextOrSamplingFlags ctx = extractor.extract(headers);

    assertThat(ctx.context().traceIdString()).isEqualTo(B3_TRACE_ID);
  }

  @Test
  public void emptyContextReturnedWhenNoHeadersPresent() {
    TraceContextOrSamplingFlags ctx = extractor.extract(new HashMap<>());

    assertThat(ctx).isSameAs(TraceContextOrSamplingFlags.EMPTY);
  }
}
