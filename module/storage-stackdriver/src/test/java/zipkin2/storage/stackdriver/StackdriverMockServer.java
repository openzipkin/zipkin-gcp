/*
 * Copyright 2016-2019 The OpenZipkin Authors
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
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcServiceBuilder;
import io.grpc.stub.StreamObserver;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.unmodifiableSet;

/** Starts up a local Stackdriver Trace server, listening for GRPC requests on {@link #grpcURI}. */
public class StackdriverMockServer extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(StackdriverMockServer.class);

  private final Server server;

  private final Set<String> traceIds = Sets.newConcurrentHashSet();
  private final Set<String> spanIds = Sets.newConcurrentHashSet();
  private CountDownLatch spanCountdown;

  public StackdriverMockServer() {
    try {
      this.server = new ServerBuilder()
          .service(new GrpcServiceBuilder()
              .addService(new Service())
              .build())
          .tlsSelfSigned()
          .build();
    } catch (SSLException|CertificateException e) {
      throw new Error(e);
    }
  }

  public int getPort() {
    return server.activePort().get().localAddress().getPort();
  }

  @Override
  protected void before() throws Throwable {
    this.server.start().join();

    LOG.info("Started MOCK grpc server on 'localhost:{}'", getPort());
  }

  @Override
  public void after() {
    this.server.stop().join();
  }

  public void reset() {
    this.spanCountdown = null;
    this.traceIds.clear();
    this.spanIds.clear();
  }

  class Service extends TraceServiceGrpc.TraceServiceImplBase {
    @Override
    public void batchWriteSpans(BatchWriteSpansRequest request, StreamObserver<Empty> responseObserver) {
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
    return String.format("%s:%s", "localhost", this.getPort());
  }

  public Set<String> spanIds() {
    return unmodifiableSet(spanIds);
  }

  public Set<String> traceIds() {
    return unmodifiableSet(traceIds);
  }
}
