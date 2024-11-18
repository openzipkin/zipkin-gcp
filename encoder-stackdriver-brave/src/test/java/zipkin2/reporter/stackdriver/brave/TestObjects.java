/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.handler.MutableSpan;

public class TestObjects {
  static MutableSpan clientSpan() {
    MutableSpan braveSpan = new MutableSpan();
    braveSpan.traceId("7180c278b62e8f6a216a2aea45d08fc9");
    braveSpan.parentId("6b221d5bc9e6496c");
    braveSpan.id("5b4185666d50f68b");
    braveSpan.name("get");
    braveSpan.kind(brave.Span.Kind.CLIENT);
    braveSpan.localServiceName("frontend");
    braveSpan.localIp("127.0.0.1");
    braveSpan.remoteServiceName("backend");
    braveSpan.remoteIpAndPort("192.168.99.101", 9000);
    braveSpan.startTimestamp(1472470996199000L);
    braveSpan.finishTimestamp(1472470996199000L + 207000L);
    braveSpan.annotate(1472470996238000L, "foo");
    braveSpan.annotate(1472470996403000L, "bar");
    braveSpan.tag("clnt/finagle.version", "6.45.0");
    braveSpan.tag("http.path", "/api");
    return braveSpan;
  }

  static MutableSpan spring6ServerSpan() {
    MutableSpan braveSpan = new MutableSpan();
    braveSpan.traceId("673adb3c54aa03af9e941a86a679a9d5");
    braveSpan.id("9e941a86a679a9d5");
    braveSpan.name("http get /test");
    braveSpan.kind(brave.Span.Kind.SERVER);
    braveSpan.localServiceName("backend");
    braveSpan.localIp("127.0.0.1");
    braveSpan.startTimestamp(1472470996199000L);
    braveSpan.finishTimestamp(1472470996199000L + 207000L);
    braveSpan.annotate(1472470996238000L, "foo");
    braveSpan.annotate(1472470996403000L, "bar");
    braveSpan.tag("exception", "none");
    braveSpan.tag("http.url", "/test");
    braveSpan.tag("method", "GET");
    braveSpan.tag("outcome", "SUCCESS");
    braveSpan.tag("status", "200");
    braveSpan.tag("uri", "/test");
    return braveSpan;
  }
}
