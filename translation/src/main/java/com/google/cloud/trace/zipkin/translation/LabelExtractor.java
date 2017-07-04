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
import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import zipkin.Annotation;
import zipkin.Span;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;

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

  private static class EndpointAddress {
    String ipv4 = "";
    String ipv6 = "";
  }
  
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
    Map<String, String> result = new LinkedHashMap<>();
    for (Span2 span2 : Span2Converter.fromSpan(zipkinSpan)) {
      result.putAll(extract(span2));
    }
    return result;
  }

  Map<String, String> extract(Span2 zipkinSpan) { // not exposed until Span2 is a formal type
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> tag : zipkinSpan.tags().entrySet()) {
      result.put(getLabelName(tag.getKey()), tag.getValue());
    }

    // Only use server receive spans to extract endpoint data as spans
    // will be rewritten into multiple single-host Stackdriver spans. A client send
    // trace might not show the final destination.
    if (zipkinSpan.localEndpoint() != null && zipkinSpan.kind().equals(Span2.Kind.SERVER)) {
      EndpointAddress endpointAddress = new Gson().fromJson(zipkinSpan.localEndpoint().toString(),
                                                            EndpointAddress.class);
      result.put(getLabelName("endpoint.ipv4"), endpointAddress.ipv4);
      result.put(getLabelName("endpoint.ipv6"), endpointAddress.ipv6);
    }

    for (Annotation annotation : zipkinSpan.annotations()) {
      result.put(getLabelName(annotation.value), formatTimestamp(annotation.timestamp));
    }

    if (zipkinSpan.localEndpoint() != null && !zipkinSpan.localEndpoint().serviceName.isEmpty()) {
      result.put(kComponentLabelKey, zipkinSpan.localEndpoint().serviceName);
    }

    if (zipkinSpan.parentId() == null) {
      String agentName = System.getProperty("stackdriver.trace.zipkin.agent", "zipkin-java");
      result.put(kAgentLabelKey, agentName);
    }

    return result;
  }
  
  private String getLabelName(String zipkinName) {
    if (renamedLabels.containsKey(zipkinName)) {
      return renamedLabels.get(zipkinName);
    } else {
      return prefix + zipkinName;
    }
  }

  private String formatTimestamp(long microseconds) {
    long milliseconds = microseconds / 1000;
    Date date = new Date(milliseconds);
    return new SimpleDateFormat("yyyy-MM-dd (HH:mm:ss.SSS)").format(date);
  }
}
