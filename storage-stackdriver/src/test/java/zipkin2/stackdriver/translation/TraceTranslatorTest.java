/**
 * Copyright 2016-2017 The OpenZipkin Authors
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
package zipkin2.stackdriver.translation;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TraceTranslatorTest {
  private static Map<String, TraceSpan> getSpansByName(Trace trace) {
    Map<String, TraceSpan> spansByName = new HashMap<>();
    for (TraceSpan span : trace.getSpansList()) {
      spansByName.put(span.getName(), span);
    }
    return spansByName;
  }

  private static void assertDistinctSpanIds(Trace trace) {
    Set<Long> spanIds = new HashSet<>();
    for (TraceSpan span : trace.getSpansList()) {
      spanIds.add(span.getSpanId());
    }
    assertEquals(
        "Trace does not have enough distinct span IDs", trace.getSpansCount(), spanIds.size());
  }

  @Test
  public void testTranslateTrace() {
    Span span1 =
        Span.newBuilder().id("1").traceId("1").name("/a").timestamp(1L).duration(1L).build();
    Span span2 =
        Span.newBuilder().id("2").traceId("2").name("/b").timestamp(2L).duration(1L).build();
    Span span3 =
        Span.newBuilder().id("3").traceId("1").name("/c").timestamp(3L).duration(1L).build();

    List<Span> spans = Arrays.asList(span1, span2, span3);
    List<Trace> traces = new ArrayList<>(TraceTranslator.translateSpans("test-project", spans));

    assertEquals(2, traces.size());
    Trace trace1 = traces.get(0);
    Trace trace2 = traces.get(1);
    Map<String, Trace> traceMap =
        ImmutableMap.of(
            trace1.getTraceId(), trace1,
            trace2.getTraceId(), trace2);
    String key1 = "00000000000000000000000000000001";
    String key2 = "00000000000000000000000000000002";
    assertTrue(traceMap.containsKey(key1));
    assertTrue(traceMap.containsKey(key2));
    assertEquals(2, traceMap.get(key1).getSpansCount());
    assertEquals(1, traceMap.get(key2).getSpansCount());

    assertEquals("test-project", traceMap.get(key1).getProjectId());
    assertEquals("test-project", traceMap.get(key2).getProjectId());
  }

  @Test
  public void testMultihostServerRootSpan() {
    Span span1 =
        Span.newBuilder()
            .traceId("1")
            .id("1")
            .name("/a")
            .timestamp(1474488796000000L) // This is set because the server owns the span
            .duration(5000000L)
            .build();
    Span span2 =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?client")
            .timestamp(1474488797000000L) // This is set because the client owns the span.
            .duration(1500000L)
            .build();
    Span span3 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?server")
            // timestamp is not set because the server does not own this span.
            .build();
    Span span4 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("custom-span")
            .timestamp(1474488797600000L)
            .duration(200000L)
            .build();

    Collection<Trace> traces =
        TraceTranslator.translateSpans("test-project", Arrays.asList(span1, span2, span3, span4));
    assertEquals(1, traces.size());
    Trace trace = traces.iterator().next();
    Map<String, TraceSpan> spansByName = getSpansByName(trace);
    assertThat(spansByName).containsOnlyKeys("/a", "/b?client", "/b?server", "custom-span");
    assertDistinctSpanIds(trace);
    assertThat(spansByName.get("custom-span").getParentSpanId())
        .isEqualTo(spansByName.get("/b?server").getSpanId());
    assertThat(spansByName.get("/b?server").getParentSpanId())
        .isEqualTo(spansByName.get("/b?client").getSpanId());
    assertThat(spansByName.get("/b?client").getParentSpanId())
        .isEqualTo(spansByName.get("/a").getSpanId());
    assertThat(spansByName.get("/a").getParentSpanId()).isEqualTo(0);
  }

  @Test
  public void testMultihostServerRootSpan_noTimestamp() {
    Span span1 = Span.newBuilder().kind(Kind.SERVER).traceId("1").id("1").name("/a").build();
    Span span2 =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?client")
            .build();
    Span span3 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?server")
            .build();
    Span span4 = Span.newBuilder().traceId("1").parentId("2").id("3").name("custom-span").build();

    Collection<Trace> traces =
        TraceTranslator.translateSpans("test-project", Arrays.asList(span1, span2, span3, span4));
    assertEquals(1, traces.size());
    Trace trace = traces.iterator().next();
    Map<String, TraceSpan> spansByName = getSpansByName(trace);
    assertThat(spansByName).containsOnlyKeys("/a", "/b?client", "/b?server", "custom-span");
    assertDistinctSpanIds(trace);
    assertThat(spansByName.get("custom-span").getParentSpanId())
        .isEqualTo(spansByName.get("/b?server").getSpanId());
    assertThat(spansByName.get("/b?server").getParentSpanId())
        .isEqualTo(spansByName.get("/b?client").getSpanId());
    assertThat(spansByName.get("/b?client").getParentSpanId())
        .isEqualTo(spansByName.get("/a").getSpanId());
    // Without the timestamp field, it's not possible to correctly set the root span's parentSpanId
    // to 0 because we didn't have enough information to conclude that it had no parent.
  }

  @Test
  public void testMultihostClientRootSpan() {
    Span span1 =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("1")
            .id("1")
            .name("/a?client")
            .timestamp(1474488796000000L) // This is set because the client owns the span
            .duration(5000000L)
            .build();
    Span span2 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .id("1")
            .name("/a?server")
            // timestamp is not set because the server does not own this span.
            .build();
    Span span3 =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?client")
            .timestamp(1474488797000000L) // This is set because the client owns the span.
            .duration(1500000L)
            .build();
    Span span4 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?server")
            // timestamp is not set because the server does not own this span.
            .build();
    Span span5 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("custom-span")
            .timestamp(1474488797600000L)
            .duration(200000L)
            .build();

    Collection<Trace> traces =
        TraceTranslator.translateSpans("test-project",
            Arrays.asList(span1, span2, span3, span4, span5));
    assertEquals(1, traces.size());
    Trace trace = traces.iterator().next();
    Map<String, TraceSpan> spansByName = getSpansByName(trace);
    assertThat(spansByName)
        .containsOnlyKeys("/a?client", "/a?server", "/b?client", "/b?server", "custom-span");
    assertDistinctSpanIds(trace);
    assertThat(spansByName.get("custom-span").getParentSpanId())
        .isEqualTo(spansByName.get("/b?server").getSpanId());
    assertThat(spansByName.get("/b?server").getParentSpanId())
        .isEqualTo(spansByName.get("/b?client").getSpanId());
    assertThat(spansByName.get("/b?client").getParentSpanId())
        .isEqualTo(spansByName.get("/a?server").getSpanId());
    assertThat(spansByName.get("/a?server").getParentSpanId())
        .isEqualTo(spansByName.get("/a?client").getSpanId());
    assertThat(spansByName.get("/a?client").getParentSpanId()).isEqualTo(0);
  }

  @Test
  public void testMultihostClientRootSpan_noTimestamp() {
    Span span1 = Span.newBuilder().kind(Kind.CLIENT).traceId("1").id("1").name("/a?client").build();
    Span span2 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .id("1")
            .name("/a?server")
            .build();
    Span span3 =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?client")
            .build();
    Span span4 =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .shared(true)
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/b?server")
            .build();
    Span span5 = Span.newBuilder().traceId("1").parentId("2").id("3").name("custom-span").build();

    Collection<Trace> traces =
        TraceTranslator.translateSpans("test-project",
            Arrays.asList(span1, span2, span3, span4, span5));
    assertEquals(1, traces.size());
    Trace trace = traces.iterator().next();
    Map<String, TraceSpan> spansByName = getSpansByName(trace);
    assertThat(spansByName)
        .containsOnlyKeys("/a?client", "/a?server", "/b?client", "/b?server", "custom-span");
    assertDistinctSpanIds(trace);
    assertThat(spansByName.get("custom-span").getParentSpanId())
        .isEqualTo(spansByName.get("/b?server").getSpanId());
    assertThat(spansByName.get("/b?server").getParentSpanId())
        .isEqualTo(spansByName.get("/b?client").getSpanId());
    assertThat(spansByName.get("/b?client").getParentSpanId())
        .isEqualTo(spansByName.get("/a?server").getSpanId());
    assertThat(spansByName.get("/a?server").getParentSpanId())
        .isEqualTo(spansByName.get("/a?client").getSpanId());
    assertThat(spansByName.get("/a?client").getParentSpanId()).isEqualTo(0);
  }

  @Test
  public void testSinglehostClientRootSpan() {
    Span span1 =
        Span.newBuilder()
            .traceId("1")
            .id("1")
            .name("/a?client")
            .timestamp(1474488796000000L) // This is set because the client owns the span
            .duration(5000000L)
            .build();
    Span span2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("/a?server")
            .timestamp(1474488796000000L)
            .duration(5000000L)
            .build();
    Span span3 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("/b?client")
            .timestamp(1474488797000000L) // This is set because the client owns the span.
            .duration(1500000L)
            .build();
    Span span4 =
        Span.newBuilder()
            .traceId("1")
            .parentId("3")
            .id("4")
            .name("/b?server")
            // timestamp is not set because the server does not own this span.
            .timestamp(1474488797500000L)
            .duration(800000L)
            .build();
    Span span5 =
        Span.newBuilder()
            .traceId("1")
            .parentId("4")
            .id("5")
            .name("custom-span")
            .timestamp(1474488797600000L)
            .duration(200000L)
            .build();

    Collection<Trace> traces =
        TraceTranslator.translateSpans("test-project",
            Arrays.asList(span1, span2, span3, span4, span5));
    assertEquals(1, traces.size());
    Trace trace = traces.iterator().next();
    Map<String, TraceSpan> spansByName = getSpansByName(trace);
    assertThat(spansByName)
        .containsOnlyKeys("/a?client", "/a?server", "/b?client", "/b?server", "custom-span");
    assertDistinctSpanIds(trace);
    assertThat(spansByName.get("custom-span").getParentSpanId())
        .isEqualTo(spansByName.get("/b?server").getSpanId());
    assertThat(spansByName.get("/b?server").getParentSpanId())
        .isEqualTo(spansByName.get("/b?client").getSpanId());
    assertThat(spansByName.get("/b?client").getParentSpanId())
        .isEqualTo(spansByName.get("/a?server").getSpanId());
    assertThat(spansByName.get("/a?server").getParentSpanId())
        .isEqualTo(spansByName.get("/a?client").getSpanId());
    assertThat(spansByName.get("/a?client").getParentSpanId()).isEqualTo(0);
  }
}
