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

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;

/**
 * LabelExtractor extracts the set of Stackdriver Span labels equivalent to the annotations in a given Zipkin Span.
 *
 * Zipkin annotations are converted to Stackdriver Span labels by using annotation.value as the key and annotation.timestamp as the value.
 *
 * Zipkin binary annotations are converted to Stackdriver Span labels by using annotation.key as the key and the String value of annotation.value as the value.
 *
 * Zipkin annotations with equivalent Stackdriver labels will be renamed to the Stackdriver name.
 * Any Zipkin annotations without a Stackdriver label equivalent are renamed to zipkin.io/[key_name]
 */
public class LabelExtractor {

  private final String prefix;
  private final Map<String, String> renamedLabels;
  private static final String kAgentLabelKey = "/agent";
  private static final String kComponentLabelKey = "/component";

  public LabelExtractor(Map<String, String> renamedLabels) {
    this(ImmutableMap.copyOf(renamedLabels), "zipkin.io/");
  }

  LabelExtractor(Map<String, String> renamedLabels, String prefix) {
    this.prefix = prefix;
    this.renamedLabels = renamedLabels;
  }

  /**
   * Extracts the Stackdriver span labels that are equivalent to the Zipkin Span annotations.
   * @param zipkinSpan The Zipkin Span
   * @return A map of the Stackdriver span labels equivalent to the Zipkin annotations.
   */
  public Map<String, String> extract(Span zipkinSpan) {
    Map<String, String> labels = new HashMap<>();
    for (BinaryAnnotation annotation : zipkinSpan.binaryAnnotations) {
      labels.put(getLabelName(annotation.key), readBinaryAnnotation(annotation));
    }

    for (Annotation annotation : zipkinSpan.annotations) {
      labels.put(getLabelName(annotation.value), formatTimestamp(annotation.timestamp));
      if ("cs".equals(annotation.value) || "sr".equals(annotation.value)) {
        // Consistently grab the serviceName from a specific annotation.
        if (annotation.endpoint != null && annotation.endpoint.serviceName != null) {
          labels.put(kComponentLabelKey, annotation.endpoint.serviceName);
        }
        // Only use server receive trace to extract endpoint data as Zipkin spans
        // will be rewritten into multiple single-host Stackdriver spans. A client send
        // trace might not show the final destination.
        if (annotation.endpoint != null && "sr".equals(annotation.value)) {
          // TODO: add support for extracting IPv6 endpoints.
          extractEndpoint(labels, annotation.endpoint);
        }
      }
    }

    if (zipkinSpan.parentId == null) {
        String agentName = System.getProperty("stackdriver.trace.zipkin.agent", "zipkin-java");
        labels.put(kAgentLabelKey, agentName);
    }

    return labels;
  }

  private String getLabelName(String zipkinName) {
    if (renamedLabels.containsKey(zipkinName)) {
      return renamedLabels.get(zipkinName);
    } else {
      return prefix + zipkinName;
    }
  }

  private String readBinaryAnnotation(BinaryAnnotation annotation) {
    // The value of a BinaryAnnotation is encoded in big endian order.
    ByteBuffer buffer = ByteBuffer.wrap(annotation.value).order(ByteOrder.BIG_ENDIAN);
    switch (annotation.type) {
      case BOOL:
        return annotation.value[0] == 1 ? "true" : "false";
      case I16:
        return Short.toString(buffer.getShort());
      case I32:
        return Integer.toString(buffer.getInt());
      case I64:
        return Long.toString(buffer.getLong());
      case DOUBLE:
        return Double.toString(buffer.getDouble());
      case STRING:
      default:
        return new String(annotation.value, Charset.forName("UTF-8"));
    }
  }

  private String formatTimestamp(long microseconds) {
    long milliseconds = microseconds / 1000;
    Date date = new Date(milliseconds);
    return new SimpleDateFormat("yyyy-MM-dd (HH:mm:ss.SSS)").format(date);
  }

  private void extractEndpoint(Map<String, String> labels, Endpoint endpoint) {
    if (endpoint.ipv4 != 0) {
      StringBuilder sb = new StringBuilder()
          .append(endpoint.ipv4 >> 24 & 0xff).append(".")
          .append(endpoint.ipv4 >> 16 & 0xff).append(".")
          .append(endpoint.ipv4 >> 8 & 0xff).append(".")
          .append(endpoint.ipv4 & 0xff);

      if (isValidIp(sb.toString())) {
        if (endpoint.port != null && endpoint.port != 0) {
          sb.append(":").append(endpoint.port & 0xffff);
        }
        labels.put(getLabelName("endpoint.ipv4"), sb.toString());
      }
    }
  }

  private boolean isValidIp(String ipString) {
    try {
      InetAddresses.forString(ipString);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return true;
  }
}
