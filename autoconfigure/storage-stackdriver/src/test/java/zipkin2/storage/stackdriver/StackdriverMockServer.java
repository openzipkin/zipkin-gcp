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
package zipkin2.storage.stackdriver;

import com.google.common.collect.Sets;
import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;

import static java.util.Collections.unmodifiableSet;

/** Starts up a local Stackdriver Trace server, listening for GRPC requests on {@link #grpcURI}. */
public class StackdriverMockServer extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(StackdriverMockServer.class);

  static final SslContext CLIENT_SSL_CONTEXT;
  static final SslContext SERVER_SSL_CONTEXT;

  static {
    try {
      final SelfSignedCertificate cert = new SelfSignedCertificate("localhost");
      CLIENT_SSL_CONTEXT = GrpcSslContexts.forClient().trustManager(cert.cert()).build();
      SERVER_SSL_CONTEXT = GrpcSslContexts.forServer(cert.certificate(), cert.privateKey()).build();
    } catch (CertificateException | SSLException e) {
      throw new AssertionError(e);
    }
  }

  private final int port;
  private final Server server;

  private final Set<String> traceIds = Sets.newConcurrentHashSet();
  private final Set<Long> spanIds = Sets.newConcurrentHashSet();
  private CountDownLatch spanCountdown;

  public StackdriverMockServer() {
    this.port = SocketUtils.findAvailableTcpPort();
    this.server =
        NettyServerBuilder.forPort(port)
            .sslContext(SERVER_SSL_CONTEXT)
            .addService(new Service())
            .build();
  }

  public int getPort() {
    return port;
  }

  @Override
  protected void before() throws Throwable {
    this.server.start();

    LOG.info("Started MOCK grpc server on 'localhost:{}'", port);
  }

  @Override
  public void after() {
    this.server.shutdownNow();
  }

  public void reset() {
    this.spanCountdown = null;
    this.traceIds.clear();
    this.spanIds.clear();
  }

  class Service extends TraceServiceGrpc.TraceServiceImplBase {
    @Override
    public void patchTraces(PatchTracesRequest request, StreamObserver<Empty> responseObserver) {
      final List<Trace> tracesList = request.getTraces().getTracesList();
      for (Trace trace : tracesList) {
        for (TraceSpan span : trace.getSpansList()) {
          spanIds.add(span.getSpanId());
          if (spanCountdown != null) {
            spanCountdown.countDown();
          }
        }
      }
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  public void setSpanCountdown(CountDownLatch spanCountdown) {
    this.spanCountdown = spanCountdown;
  }

  public String grpcURI() {
    return String.format("%s:%s", "localhost", this.port);
  }

  public Set<Long> spanIds() {
    return unmodifiableSet(spanIds);
  }

  public Set<String> traceIds() {
    return unmodifiableSet(traceIds);
  }
}
