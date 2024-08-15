/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Span;
import brave.Tags;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import com.google.devtools.cloudtrace.v2.AttributeValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin2.reporter.stackdriver.brave.AttributesExtractor.toAttributeValue;

class AttributesExtractorTest {
  @Test void testLabel() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan braveSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    braveSpan.name("test-span");
    braveSpan.annotate(1, "annotation.key.1");
    braveSpan.tag("tag.key.1", "value");

    Map<String, AttributeValue> labels = extractor.extract(braveSpan).getAttributeMapMap();
    assertThat(labels).contains(entry("tag.key.1", toAttributeValue("value")));
  }

  @Test void testLabelIsRenamed() {
    Map<String, String> knownLabels = new LinkedHashMap<>();
    knownLabels.put("known.1", "renamed.1");
    knownLabels.put("known.2", "renamed.2");
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, knownLabels);

    MutableSpan braveSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    braveSpan.name("test-span");
    braveSpan.annotate(1, "annotation.key.1");
    braveSpan.annotate(13, "known.1");
    braveSpan.tag("tag.key.1", "value");
    braveSpan.tag("known.2", "known.value");

    Map<String, AttributeValue> labels = extractor.extract(braveSpan).getAttributeMapMap();
    assertThat(labels).contains(entry("renamed.2", toAttributeValue("known.value")));
  }

  @Test void testAgentLabelIsSet() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan rootSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    rootSpan.name("test-span");

    MutableSpan nonRootSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).parentId(5).spanId(6).build(), null);
    rootSpan.name("child-span");

    Map<String, AttributeValue> rootLabels = extractor.extract(rootSpan).getAttributeMapMap();
    assertThat(rootLabels).containsEntry("/agent", toAttributeValue("zipkin-java"));
    Map<String, AttributeValue> nonRootLabels = extractor.extract(nonRootSpan).getAttributeMapMap();
    assertThat(nonRootLabels).doesNotContainKey("/agent");

    System.setProperty("stackdriver.trace.zipkin.agent", "zipkin-test");
    rootLabels = extractor.extract(rootSpan).getAttributeMapMap();
    assertThat(rootLabels).containsEntry("/agent", toAttributeValue("zipkin-test"));
    System.clearProperty("stackdriver.trace.zipkin.agent");
  }

  @Test void testEndpointIsSetIpv4() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan serverSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    serverSpan.name("test-span");
    serverSpan.kind(Span.Kind.SERVER);
    serverSpan.localServiceName("service1");
    serverSpan.localIp("10.0.0.1");
    serverSpan.localPort(80);

    MutableSpan clientSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).parentId(5).spanId(6).build(), null);
    clientSpan.name("test-span");
    clientSpan.kind(Span.Kind.CLIENT);
    clientSpan.localServiceName("service1");
    clientSpan.localIp("10.0.0.1");
    clientSpan.localPort(80);

    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).containsEntry("endpoint.ipv4", toAttributeValue("10.0.0.1"));
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv6");
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).doesNotContainKeys("endpoint.ipv4", "endpoint.ipv6");
  }

  @Test void testEndpointIsSetIpv6() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan serverSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    serverSpan.name("test-span");
    serverSpan.kind(Span.Kind.SERVER);
    serverSpan.localServiceName("service1");
    serverSpan.localIp("::1");
    serverSpan.localPort(80);

    MutableSpan clientSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).parentId(5).spanId(6).build(), null);
    clientSpan.name("test-span");
    clientSpan.kind(Span.Kind.CLIENT);
    clientSpan.localServiceName("service1");
    clientSpan.localIp("::1");
    clientSpan.localPort(80);

    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv4");
    assertThat(serverLabels).containsEntry("endpoint.ipv6", toAttributeValue("0:0:0:0:0:0:0:1"));
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).doesNotContainKeys("endpoint.ipv4", "endpoint.ipv6");
  }

  @Test void testEndpointIsNotSetForNullLocalIp() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan serverSpan =
            new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    serverSpan.name("test-span");
    serverSpan.kind(Span.Kind.SERVER);
    serverSpan.localServiceName("service1");
    serverSpan.localIp(null);
    serverSpan.localPort(80);

    MutableSpan clientSpan =
            new MutableSpan(TraceContext.newBuilder().traceId(4).parentId(5).spanId(6).build(), null);
    clientSpan.name("test-span");
    clientSpan.kind(Span.Kind.CLIENT);
    clientSpan.localServiceName("service1");
    clientSpan.localIp("::1");
    clientSpan.localPort(80);

    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv4");
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv6");
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).doesNotContainKeys("endpoint.ipv4", "endpoint.ipv6");
  }

  @Test void testErrorTag() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan serverSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    serverSpan.name("test-span");
    serverSpan.error(new RuntimeException("this cake is a lie"));

    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).containsEntry("error", toAttributeValue("this cake is a lie"));
  }

  @Test void testEndpointWithNullServiceName() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan serverSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    serverSpan.name("test-span");
    serverSpan.kind(Span.Kind.SERVER);
    serverSpan.localPort(80);

    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).doesNotContainKey("endpoint.serviceName");
  }

  @Test void testComponentLabelIsSet() {
    AttributesExtractor extractor = new AttributesExtractor(Tags.ERROR, Collections.emptyMap());

    MutableSpan clientSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).spanId(5).build(), null);
    clientSpan.name("test-span");
    clientSpan.localServiceName("service1");

    MutableSpan serverSpan =
        new MutableSpan(TraceContext.newBuilder().traceId(4).parentId(5).spanId(6).build(), null);
    serverSpan.name("child-span");
    serverSpan.localServiceName("service2");

    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).containsEntry("/component", toAttributeValue("service1"));
    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).containsEntry("/component", toAttributeValue("service2"));
  }
}
