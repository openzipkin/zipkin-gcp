/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
}
