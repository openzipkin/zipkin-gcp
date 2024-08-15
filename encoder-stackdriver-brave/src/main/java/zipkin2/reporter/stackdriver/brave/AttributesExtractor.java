/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Span;
import brave.Tag;
import brave.handler.MutableSpan;
import com.google.common.net.InetAddresses;
import com.google.devtools.cloudtrace.v2.AttributeValue;
import com.google.devtools.cloudtrace.v2.Span.Attributes;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;

import static zipkin2.reporter.stackdriver.brave.SpanUtil.toTruncatableString;

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
 * <p>Zipkin annotations with equivalent Stackdriver labels will be renamed to the Stackdriver
 * Trace
 * name. Any Zipkin annotations without a Stackdriver label equivalent are renamed to
 * zipkin.io/[key_name]
 */
final class AttributesExtractor {

  private static final String kAgentLabelKey = "/agent";
  private static final String kComponentLabelKey = "/component";
  private static final String kKindLabelKey = "/kind";

  private final Tag<Throwable> errorTag;
  private final Map<String, String> renamedLabels;

  AttributesExtractor(Tag<Throwable> errorTag, Map<String, String> renamedLabels) {
    this.errorTag = errorTag;
    this.renamedLabels = renamedLabels;
  }

  /**
   * Extracts the Stackdriver span labels that are equivalent to the Zipkin Span tags.
   *
   * @param braveSpan The Zipkin Span
   * @return {@link Attributes} with the Stackdriver span labels equivalent to the Zipkin tags.
   */
  Attributes extract(MutableSpan braveSpan) {
    Attributes.Builder attributes = Attributes.newBuilder();

    // Add Kind as a tag for now since there is no structured way of sending it with Stackdriver
    // Trace API V2
    if (braveSpan.kind() != null) {
      attributes.putAttributeMap(kKindLabelKey, toAttributeValue(kindLabel(braveSpan.kind())));
    }

    String errorValue = errorTag.value(braveSpan.error(), null);
    if (errorValue != null) {
      attributes.putAttributeMap(getLabelName("error"), toAttributeValue(errorValue));
    }

    braveSpan.forEachTag(this::addTag, attributes);

    // Only use server receive spans to extract endpoint data as spans
    // will be rewritten into multiple single-host Stackdriver spans. A client send
    // trace might not show the final destination.
    if (braveSpan.localServiceName() != null && braveSpan.kind() == Span.Kind.SERVER) {
      if (braveSpan.localIp() != null) {
        // Create an IP without querying DNS
        InetAddress ip = InetAddresses.forString(braveSpan.localIp());
        if (ip instanceof Inet4Address) {
          attributes.putAttributeMap(
              getLabelName("endpoint.ipv4"), toAttributeValue(ip.getHostAddress()));
        } else if (ip instanceof Inet6Address) {
          attributes.putAttributeMap(
              getLabelName("endpoint.ipv6"), toAttributeValue(ip.getHostAddress()));
        }
      }
    }

    if (braveSpan.localServiceName() != null &&
        !braveSpan.localServiceName().isEmpty()) {
      attributes.putAttributeMap(
          kComponentLabelKey, toAttributeValue(braveSpan.localServiceName()));
    }

    if (braveSpan.parentId() == null) {
      String agentName = System.getProperty("stackdriver.trace.zipkin.agent", "zipkin-java");
      attributes.putAttributeMap(kAgentLabelKey, toAttributeValue(agentName));
    }

    return attributes.build();
  }

  void addTag(Attributes.Builder target, String key, String value) {
    target.putAttributeMap(getLabelName(key), toAttributeValue(value));
  }

  static AttributeValue toAttributeValue(String text) {
    return AttributeValue.newBuilder()
        .setStringValue(toTruncatableString(text))
        .build();
  }

  private String getLabelName(String zipkinName) {
    String renamed = renamedLabels.get(zipkinName);
    return renamed != null ? renamed : zipkinName;
  }

  private String kindLabel(Span.Kind kind) {
    switch (kind) {
      case CLIENT:
        return "client";
      case SERVER:
        return "server";
      case PRODUCER:
        return "producer";
      case CONSUMER:
        return "consumer";
      default:
        return kind.name().toLowerCase();
    }
  }
}
