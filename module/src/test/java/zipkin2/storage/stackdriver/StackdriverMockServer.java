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
package zipkin2.storage.stackdriver;

import com.google.common.collect.Sets;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.unmodifiableSet;

/** Starts up a local Stackdriver Trace server, listening for GRPC requests on {@link #grpcURI}. */
public class StackdriverMockServer extends ServerExtension {
  private final Set<String> traceIds = Sets.newConcurrentHashSet();
  private final Set<String> spanIds = Sets.newConcurrentHashSet();
  private CountDownLatch spanCountdown;

  @Override protected void configure(ServerBuilder sb) {
    sb.service(GrpcService.builder().addService(new Service()).build()).tlsSelfSigned();
  }

  public int getPort() {
    return server().activeLocalPort();
  }

  public void reset() {
    this.spanCountdown = null;
    this.traceIds.clear();
    this.spanIds.clear();
  }

  class Service extends TraceServiceGrpc.TraceServiceImplBase {
    @Override public void batchWriteSpans(BatchWriteSpansRequest request,
        StreamObserver<Empty> responseObserver) {
      final List<Span> spansList = request.getSpansList();
      for (Span span : spansList) {
        spanIds.add(span.getSpanId());
        spanCountdown.countDown();
      }
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  public void setSpanCountdown(CountDownLatch spanCountdown) {
    this.spanCountdown = spanCountdown;
  }

  public String grpcURI() {
    return "%s:%s".formatted("localhost", getPort());
  }

  public Set<String> spanIds() {
    return unmodifiableSet(spanIds);
  }

  public Set<String> traceIds() {
    return unmodifiableSet(traceIds);
  }
}
