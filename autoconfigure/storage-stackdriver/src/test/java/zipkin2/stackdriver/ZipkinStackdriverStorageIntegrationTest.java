/**
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
package zipkin2.stackdriver;

import com.google.auth.Credentials;
import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc.TraceServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.stackdriver.ZipkinStackdriverStorageAutoConfiguration;
import zipkin.autoconfigure.stackdriver.ZipkinStackdriverStorageProperties;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
import static zipkin2.stackdriver.StackdriverMockServer.CLIENT_SSL_CONTEXT;

public class ZipkinStackdriverStorageIntegrationTest {
  @ClassRule public static final StackdriverMockServer mockServer = new StackdriverMockServer();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  StackdriverStorage storage;
  ZipkinStackdriverStorageProperties storageProperties;

  @Before public void init() {
    addEnvironment(
        context,
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:test_project",
        "zipkin.storage.stackdriver.api-host:localhost:" + mockServer.getPort());
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        TestConfiguration.class,
        ZipkinStackdriverStorageAutoConfiguration.class);
    context.refresh();
    storage = context.getBean(StackdriverStorage.class);
    storageProperties = context.getBean(ZipkinStackdriverStorageProperties.class);
  }

  @After public void close() {
    mockServer.reset();
    context.close();
  }

  @Test
  public void openSSLAvailable() throws InterruptedException {
    assertThat(OpenSsl.isAvailable())
        .withFailMessage("OpenSsl unavailable:" + OpenSsl.unavailabilityCause())
        .isTrue();

    assertThat(SslContext.defaultServerProvider())
        .withFailMessage("OpenSsl suppose to be default")
        .isEqualTo(SslProvider.OPENSSL);

    assertThat(SslContext.defaultClientProvider())
        .withFailMessage("OpenSsl suppose to be default")
        .isEqualTo(SslProvider.OPENSSL);
  }

  @Test
  public void mockGrpcServerServesOverSSL() { // sanity checks the mock server
    NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(mockServer.grpcURI());

    TraceServiceBlockingStub sslTraceService =
        TraceServiceGrpc.newBlockingStub(channelBuilder.sslContext(CLIENT_SSL_CONTEXT).build());

    sslTraceService.patchTraces(PatchTracesRequest.getDefaultInstance());
  }

  @Test
  public void traceConsumerGetsCalled() throws Exception {
    List<Long> spanIds = LongStream.of(1, 2, 3).boxed().collect(Collectors.toList());

    assertThat(mockServer.spanIds())
        .withFailMessage("Unexpected traces in Stackdriver")
        .isEmpty();

    CountDownLatch spanCountdown = new CountDownLatch(3);
    mockServer.setSpanCountdown(spanCountdown);

    for (Long i : spanIds) {
      String id = Long.toHexString(i);
      Span span = Span.newBuilder().id(id).traceId(id).name("/a").timestamp(1L).build();

      storage.spanConsumer().accept(asList(span)).execute();
    }

    spanCountdown.await(1, TimeUnit.SECONDS);

    assertThat(spanCountdown.getCount()).isZero();
    assertThat(mockServer.spanIds())
        .withFailMessage("Not all spans made it to Stackdriver")
        .containsExactlyElementsOf(spanIds);
  }

  @Configuration static class TestConfiguration {
    //TODO(denyska): figure out how to request credentials in StackdriverMockServer
    @Bean("googleCredentials") public Credentials mockGoogleCredentials() {
      return new Credentials() {
        @Override public String getAuthenticationType() {
          return null;
        }

        @Override public Map<String, List<String>> getRequestMetadata(URI uri) {
          return null;
        }

        @Override public boolean hasRequestMetadata() {
          return false;
        }

        @Override public boolean hasRequestMetadataOnly() {
          return false;
        }

        @Override public void refresh() {
        }
      };
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel managedChannel(ZipkinStackdriverStorageProperties properties) {
      return NettyChannelBuilder.forTarget(properties.getApiHost())
          .sslContext(CLIENT_SSL_CONTEXT)
          .build();
    }
  }
}
