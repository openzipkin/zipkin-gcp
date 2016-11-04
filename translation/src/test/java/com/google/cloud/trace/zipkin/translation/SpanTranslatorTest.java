/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin.translation;

import static org.junit.Assert.assertEquals;

import com.google.devtools.cloudtrace.v1.TraceSpan;
import org.junit.Test;
import zipkin.Span;

public class SpanTranslatorTest {
  @Test
  public void testTranslateSpan() {
    Span zipkinSpan = Span.builder()
        .id(2)
        .name("/foo")
        .traceId(3)
        .parentId(5L)
        .timestamp(3000001L) // 3.000001 seconds after the unix epoch.
        .duration(8000001L) // 8.000001 seconds;
        .build();
    SpanTranslator translator = new SpanTranslator();

    TraceSpan traceSpan = translator.translate(zipkinSpan);

    assertEquals(2, traceSpan.getSpanId());
    assertEquals("/foo", traceSpan.getName());
    assertEquals(5, traceSpan.getParentSpanId());

    assertEquals(3, traceSpan.getStartTime().getSeconds());
    assertEquals(1000, traceSpan.getStartTime().getNanos());

    assertEquals(3 + 8, traceSpan.getEndTime().getSeconds());
    assertEquals(2000, traceSpan.getEndTime().getNanos());

  }
}
