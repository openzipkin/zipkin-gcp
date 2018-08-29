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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import zipkin2.Annotation;
import zipkin2.Span;

/**
 * LabelExtractor extracts the set of Stackdriver Span labels equivalent to the annotations in a
 * given Zipkin Span.
 *
 * <p>Zipkin annotations are converted to Stackdriver Span labels by using annotation.value as the
 * key and annotation.timestamp as the value.
 *
 * <p>Zipkin tags are converted to Stackdriver Span labels by using annotation.key as the key and
 * the String value of annotation.value as the value.
 *
 * <p>Zipkin annotations with equivalent Stackdriver labels will be renamed to the Stackdriver Trace
 * name. Any Zipkin annotations without a Stackdriver label equivalent are renamed to
 * zipkin.io/[key_name]
 */
final class LabelExtractor {

  private static final String kAgentLabelKey = "/agent";
  private static final String kComponentLabelKey = "/component";
  private final Map<String, String> renamedLabels;
  // The maximum label value size in Stackdriver is 16 KiB. This should be safe.
  static final int LABEL_LENGTH_MAX = 8192;

  LabelExtractor(Map<String, String> renamedLabels) {
    this.renamedLabels = renamedLabels;
  }

  /**
   * Extracts the Stackdriver span labels that are equivalent to the Zipkin Span annotations.
   *
   * @param zipkinSpan The Zipkin Span
   * @return A map of the Stackdriver span labels equivalent to the Zipkin annotations.
   */
  Map<String, String> extract(Span zipkinSpan) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> tag : zipkinSpan.tags().entrySet()) {
      String value = tag.getValue();
      if (value.length() > LABEL_LENGTH_MAX) {
        value = value.substring(0, LABEL_LENGTH_MAX);
      }
      result.put(getLabelName(tag.getKey()), value);
    }

    // Only use server receive spans to extract endpoint data as spans
    // will be rewritten into multiple single-host Stackdriver spans. A client send
    // trace might not show the final destination.
    if (zipkinSpan.localEndpoint() != null && zipkinSpan.kind() == Span.Kind.SERVER) {
      if (zipkinSpan.localEndpoint().ipv4() != null) {
        result.put(getLabelName("endpoint.ipv4"), zipkinSpan.localEndpoint().ipv4());
      }
      if (zipkinSpan.localEndpoint().ipv6() != null) {
        result.put(getLabelName("endpoint.ipv6"), zipkinSpan.localEndpoint().ipv6());
      }
    }

    for (Annotation annotation : zipkinSpan.annotations()) {
      result.put(getLabelName(annotation.value()), formatTimestamp(annotation.timestamp()));
    }

    if (zipkinSpan.localEndpoint() != null && !zipkinSpan.localEndpoint().serviceName().isEmpty()) {
      result.put(kComponentLabelKey, zipkinSpan.localEndpoint().serviceName());
    }

    if (zipkinSpan.parentId() == null) {
      String agentName = System.getProperty("stackdriver.trace.zipkin.agent", "zipkin-java");
      result.put(kAgentLabelKey, agentName);
    }

    return result;
  }

  private String getLabelName(String zipkinName) {
    String renamed = renamedLabels.get(zipkinName);
    return renamed != null ? renamed : zipkinName;
  }

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  // SimpleDateFormat is not thread safe
  private static final ThreadLocal<SimpleDateFormat> DATE =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd (HH:mm:ss.SSS)");
          result.setTimeZone(UTC);
          return result;
        }
      };

  static String formatTimestamp(long microseconds) {
    long milliseconds = microseconds / 1000;
    return DATE.get().format(new Date(milliseconds));
  }
}
