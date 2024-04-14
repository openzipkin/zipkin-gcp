/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.translation.stackdriver;

import com.google.devtools.cloudtrace.v2.AttributeValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin2.translation.stackdriver.AttributesExtractor.toAttributeValue;

public class AttributesExtractorTest {
  @Test void testLabel() {
    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Span zipkinSpan =
        Span.newBuilder()
            .traceId("4")
            .name("test-span")
            .id("5")
            .addAnnotation(1, "annotation.key.1")
            .putTag("tag.key.1", "value")
            .build();
    Map<String, AttributeValue> labels = extractor.extract(zipkinSpan).getAttributeMapMap();
    assertThat(labels).contains(entry("tag.key.1", toAttributeValue("value")));
  }

  @Test void testLabelIsRenamed() {
    Map<String, String> knownLabels = new LinkedHashMap<>();
    knownLabels.put("known.1", "renamed.1");
    knownLabels.put("known.2", "renamed.2");
    AttributesExtractor extractor = new AttributesExtractor(knownLabels);
    Span zipkinSpan =
        Span.newBuilder()
            .traceId("4")
            .name("test-span")
            .id("5")
            .addAnnotation(1, "annotation.key.1")
            .addAnnotation(13, "known.1")
            .putTag("tag.key.1", "value")
            .putTag("known.2", "known.value")
            .build();
    Map<String, AttributeValue> labels = extractor.extract(zipkinSpan).getAttributeMapMap();
    assertThat(labels).contains(entry("renamed.2", toAttributeValue("known.value")));
  }

  @Test void testAgentLabelIsSet() {
    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Span rootSpan = Span.newBuilder().traceId("4").name("test-span").id("5").build();
    Span nonRootSpan =
        Span.newBuilder().traceId("4").name("child-span").id("6").parentId("5").build();

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
    Endpoint.Builder serverEndpointBuilder = Endpoint.newBuilder().serviceName("service1").port(80);
    serverEndpointBuilder.parseIp("10.0.0.1");
    Endpoint serverEndpoint = serverEndpointBuilder.build();
    Endpoint.Builder clientEndpointBuilder = Endpoint.newBuilder().serviceName("service2").port(80);
    clientEndpointBuilder.parseIp("10.0.0.1");
    Endpoint clientEndpoint = clientEndpointBuilder.build();
    Span serverSpan =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .traceId("4")
            .name("test-span")
            .id("5")
            .localEndpoint(serverEndpoint)
            .build();
    Span clientSpan =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("4")
            .name("test-span")
            .id("6")
            .parentId("5")
            .localEndpoint(clientEndpoint)
            .build();

    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).containsEntry("endpoint.ipv4", toAttributeValue("10.0.0.1"));
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv6");
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).doesNotContainKeys("endpoint.ipv4", "endpoint.ipv6");
  }

  @Test void testEndpointIsSetIpv6() {
    Endpoint.Builder serverEndpointBuilder = Endpoint.newBuilder().serviceName("service1").port(80);
    serverEndpointBuilder.parseIp("::1");
    Endpoint serverEndpoint = serverEndpointBuilder.build();
    Endpoint.Builder clientEndpointBuilder = Endpoint.newBuilder().serviceName("service2").port(80);
    clientEndpointBuilder.parseIp("::1");
    Endpoint clientEndpoint = clientEndpointBuilder.build();
    Span serverSpan =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .traceId("4")
            .name("test-span")
            .id("5")
            .localEndpoint(serverEndpoint)
            .build();
    Span clientSpan =
        Span.newBuilder()
            .kind(Kind.CLIENT)
            .traceId("4")
            .name("test-span")
            .id("6")
            .parentId("5")
            .localEndpoint(clientEndpoint)
            .build();

    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).doesNotContainKey("endpoint.ipv4");
    assertThat(serverLabels).containsEntry("endpoint.ipv6", toAttributeValue("::1"));
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).doesNotContainKeys("endpoint.ipv4", "endpoint.ipv6");
  }

  @Test void testEndpointWithNullServiceName() {
    Endpoint.Builder serverEndpointBuilder = Endpoint.newBuilder().port(80);
    Endpoint serverEndpoint = serverEndpointBuilder.build();
    Span serverSpan =
        Span.newBuilder()
            .kind(Kind.SERVER)
            .traceId("4")
            .name("test-span")
            .id("5")
            .localEndpoint(serverEndpoint)
            .build();

    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).doesNotContainKey("endpoint.serviceName");
  }

  @Test void testComponentLabelIsSet() {
    AttributesExtractor extractor = new AttributesExtractor(Collections.emptyMap());
    Span clientSpan =
        Span.newBuilder()
            .traceId("4")
            .name("test-span")
            .id("5")
            .localEndpoint(Endpoint.newBuilder().serviceName("service1").build())
            .build();
    Span serverSpan =
        Span.newBuilder()
            .traceId("4")
            .name("child-span")
            .id("6")
            .localEndpoint(Endpoint.newBuilder().serviceName("service2").build())
            .parentId("5")
            .build();
    Map<String, AttributeValue> clientLabels = extractor.extract(clientSpan).getAttributeMapMap();
    assertThat(clientLabels).containsEntry("/component", toAttributeValue("service1"));
    Map<String, AttributeValue> serverLabels = extractor.extract(serverSpan).getAttributeMapMap();
    assertThat(serverLabels).containsEntry("/component", toAttributeValue("service2"));
  }
}
