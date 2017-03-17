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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.Endpoint;
import zipkin.Span;

public class LabelExtractorTest {
  @Test
  public void testPrefixIsApplied() {
    String prefix = "test.prefix";
    LabelExtractor extractor = new LabelExtractor(Collections.<String, String>emptyMap(), prefix);
    Annotation annotation = Annotation.create(1, "annotation.key.1", null);
    BinaryAnnotation binaryAnnotation = BinaryAnnotation.create("binary.annotation.key.1", "value", null);
    Span zipkinSpan = Span.builder()
        .traceId(4)
        .name("test-span")
        .id(5)
        .addAnnotation(annotation)
        .addBinaryAnnotation(binaryAnnotation)
        .build();
    Map<String, String> labels = extractor.extract(zipkinSpan);
    assertTrue(labels.containsKey(prefix + "annotation.key.1"));
    assertTrue(labels.containsKey(prefix + "binary.annotation.key.1"));
  }

  @Test
  public void testLabelIsRenamed() {
    String prefix = "test.prefix";
    Map<String, String> knownLabels = ImmutableMap.of(
        "known.1", "renamed.1",
        "known.2", "renamed.2");
    LabelExtractor extractor = new LabelExtractor(knownLabels, prefix);
    Annotation unknownAnnotation = Annotation.create(1, "annotation.key.1", null);
    Annotation knownAnnotation = Annotation.create(13, "known.1", null);
    BinaryAnnotation unknownBinary = BinaryAnnotation.create("binary.annotation.key.1", "value", null);
    BinaryAnnotation knownBinary = BinaryAnnotation.create("known.2", "known.value", null);
    Span zipkinSpan = Span.builder()
        .traceId(4)
        .name("test-span")
        .id(5)
        .addAnnotation(unknownAnnotation)
        .addAnnotation(knownAnnotation)
        .addBinaryAnnotation(unknownBinary)
        .addBinaryAnnotation(knownBinary)
        .build();
    Map<String, String> labels = extractor.extract(zipkinSpan);
    assertFalse(labels.containsKey("known.1"));
    assertTrue(labels.containsKey("renamed.1"));
    assertFalse(labels.containsKey("known.2"));
    assertTrue(labels.containsKey("renamed.2"));
  }

  @Test
  public void testAgentLabelIsSet() {
    LabelExtractor extractor = new LabelExtractor(Collections.<String, String>emptyMap(), "test.prefix");
    Span rootSpan = Span.builder()
        .traceId(4)
        .name("test-span")
        .id(5)
        .build();
    Span nonRootSpan = Span.builder()
        .traceId(4)
        .name("child-span")
        .id(6)
        .parentId(5L)
        .build();

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
  public void testComponentLabelIsSet() {
    LabelExtractor extractor = new LabelExtractor(Collections.<String, String>emptyMap(), "test.prefix");
    Span clientSpan = Span.builder()
        .traceId(4)
        .name("test-span")
        .id(5)
        .addAnnotation(Annotation.create(1, "cs", Endpoint.create("service1", 0)))
        .addAnnotation(Annotation.create(2, "cr", Endpoint.create("service1", 0)))
        .build();
    Span serverSpan = Span.builder()
        .traceId(4)
        .name("child-span")
        .id(6)
        .parentId(5L)
        .addAnnotation(Annotation.create(1, "sr", Endpoint.create("service2", 0)))
        .addAnnotation(Annotation.create(2, "ss", Endpoint.create("service2", 0)))
        .build();
    Map<String, String> clientLabels = extractor.extract(clientSpan);
    assertEquals("service1", clientLabels.get("/component"));
    Map<String, String> serverLabels = extractor.extract(serverSpan);
    assertEquals("service2", serverLabels.get("/component"));
  }

  @Test
  public void testReadBinaryAnnotation() {
    byte[] boolBuffer = ByteBuffer.allocate(1).put((byte) 1).order(ByteOrder.BIG_ENDIAN).array();
    byte[] shortBuffer = ByteBuffer.allocate(2).putShort((short) 20).order(ByteOrder.BIG_ENDIAN).array();
    byte[] intBuffer = ByteBuffer.allocate(4).putInt(32800).order(ByteOrder.BIG_ENDIAN).array();
    byte[] longBuffer = ByteBuffer.allocate(8).putLong(2147483700L).order(ByteOrder.BIG_ENDIAN).array();
    byte[] doubleBuffer = ByteBuffer.allocate(8).putDouble(3.1415).order(ByteOrder.BIG_ENDIAN).array();
    Span zipkinSpan = Span.builder()
        .traceId(1)
        .name("test")
        .id(2)
        .addBinaryAnnotation(BinaryAnnotation.create("bool", boolBuffer, Type.BOOL, null))
        .addBinaryAnnotation(BinaryAnnotation.create("short", shortBuffer, Type.I16, null))
        .addBinaryAnnotation(BinaryAnnotation.create("int", intBuffer, Type.I32, null))
        .addBinaryAnnotation(BinaryAnnotation.create("long", longBuffer, Type.I64, null))
        .addBinaryAnnotation(BinaryAnnotation.create("double", doubleBuffer, Type.DOUBLE, null))
        .build();

    LabelExtractor extractor = new LabelExtractor(Collections.<String, String>emptyMap(), "");
    Map<String, String> labels = extractor.extract(zipkinSpan);
    assertEquals("true", labels.get("bool"));
    assertEquals("20", labels.get("short"));
    assertEquals("32800", labels.get("int"));
    assertEquals("2147483700", labels.get("long"));
    assertEquals("3.1415", labels.get("double"));
  }
}
