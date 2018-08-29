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
package zipkin2.translation.stackdriver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LabelExtractorTest {
  @Test
  public void testLabel() {
    LabelExtractor extractor = new LabelExtractor(Collections.emptyMap());
    Span zipkinSpan =
        Span.newBuilder()
            .traceId("4")
            .name("test-span")
            .id("5")
            .addAnnotation(1, "annotation.key.1")
            .putTag("tag.key.1", "value")
            .putTag("long.tag", new String(new char[10000]).replace("\0", "a"))
            .build();
    Map<String, String> labels = extractor.extract(zipkinSpan);
    assertTrue(labels.containsKey("annotation.key.1"));
    assertTrue(labels.containsKey("tag.key.1"));
    assertTrue(labels.get("tag.key.1").equals("value"));
    assertTrue(labels.get("long.tag").equals(
        new String(new char[LabelExtractor.LABEL_LENGTH_MAX]).replace("\0", "a")));
  }

  @Test
  public void testLabelIsRenamed() {
    Map<String, String> knownLabels = new LinkedHashMap<>();
    knownLabels.put("known.1", "renamed.1");
    knownLabels.put("known.2", "renamed.2");
    LabelExtractor extractor = new LabelExtractor(knownLabels);
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
    Map<String, String> labels = extractor.extract(zipkinSpan);
    assertFalse(labels.containsKey("known.1"));
    assertTrue(labels.containsKey("renamed.1"));
    assertFalse(labels.containsKey("known.2"));
    assertTrue(labels.containsKey("renamed.2"));
  }

  @Test
  public void testAgentLabelIsSet() {
    LabelExtractor extractor = new LabelExtractor(Collections.emptyMap());
    Span rootSpan = Span.newBuilder().traceId("4").name("test-span").id("5").build();
    Span nonRootSpan =
        Span.newBuilder().traceId("4").name("child-span").id("6").parentId("5").build();

    Map<String, String> rootLabels = extractor.extract(rootSpan);
    assertEquals("zipkin-java", rootLabels.get("/agent"));
    Map<String, String> nonRootLabels = extractor.extract(nonRootSpan);
    assertNull(nonRootLabels.get("/agent"));

    System.setProperty("stackdriver.trace.zipkin.agent", "zipkin-test");
    rootLabels = extractor.extract(rootSpan);
    assertEquals("zipkin-test", rootLabels.get("/agent"));
    System.clearProperty("stackdriver.trace.zipkin.agent");
  }

  @Test
  public void testEndpointIsSetIpv4() {
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

    LabelExtractor extractor = new LabelExtractor(Collections.emptyMap());
    Map<String, String> serverLabels = extractor.extract(serverSpan);
    assertEquals("10.0.0.1", serverLabels.get("endpoint.ipv4"));
    assertNull(serverLabels.get("endpoint.ipv6"));
    Map<String, String> clientLabels = extractor.extract(clientSpan);
    assertNull(clientLabels.get("endpoint.ipv4"));
    assertNull(clientLabels.get("endpoint.ipv6"));
  }

  @Test
  public void testEndpointIsSetIpv6() {
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

    LabelExtractor extractor = new LabelExtractor(Collections.emptyMap());
    Map<String, String> serverLabels = extractor.extract(serverSpan);
    assertNull(serverLabels.get("endpoint.ipv4"));
    assertEquals("::1", serverLabels.get("endpoint.ipv6"));
    Map<String, String> clientLabels = extractor.extract(clientSpan);
    assertNull(clientLabels.get("endpoint.ipv4"));
    assertNull(clientLabels.get("endpoint.ipv6"));
  }

  @Test
  public void testComponentLabelIsSet() {
    LabelExtractor extractor = new LabelExtractor(Collections.emptyMap());
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
    Map<String, String> clientLabels = extractor.extract(clientSpan);
    assertEquals("service1", clientLabels.get("/component"));
    Map<String, String> serverLabels = extractor.extract(serverSpan);
    assertEquals("service2", serverLabels.get("/component"));
  }
}
