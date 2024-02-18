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

import com.google.auth.Credentials;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.Clients;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageModule;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageProperties;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZipkinStackdriverStorageIntegrationTest {
  @Order(0) @RegisterExtension StackdriverMockServer mockServer = new StackdriverMockServer();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  StackdriverStorage storage;
  ZipkinStackdriverStorageProperties storageProperties;
  @Order(1) @RegisterExtension BeforeAllCallback init = new BeforeAllCallback() {
    @Override public void beforeAll(ExtensionContext extensionContext) {
      TestPropertyValues.of(
          "zipkin.storage.type:stackdriver",
          "zipkin.storage.stackdriver.project-id:test_project",
          "zipkin.storage.stackdriver.api-host:localhost:" + mockServer.getPort()).applyTo(context);
      context.register(
          PropertyPlaceholderAutoConfiguration.class,
          TestConfiguration.class,
          ZipkinStackdriverStorageModule.class);
      context.refresh();
      storage = context.getBean(StackdriverStorage.class);
      storageProperties = context.getBean(ZipkinStackdriverStorageProperties.class);
    }
  };

  @AfterEach void close() {
    mockServer.reset();
  }

  @Test void openSSLAvailable() {
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

  @Test void mockGrpcServerServesOverSSL() { // sanity checks the mock server
    TraceServiceGrpc.TraceServiceBlockingStub sslTraceService =
        Clients.builder("gproto+https://" + mockServer.grpcURI() + "/")
            .factory(ClientFactory.builder()
                .tlsCustomizer(tls -> tls.trustManager(InsecureTrustManagerFactory.INSTANCE))
                .build())
            .build(TraceServiceGrpc.TraceServiceBlockingStub.class);

    sslTraceService.batchWriteSpans(BatchWriteSpansRequest.getDefaultInstance());
  }

  @Test void traceConsumerGetsCalled() throws Exception {
    List<String> spanIds =
        LongStream.of(1, 2, 3)
            .mapToObj(Long::toHexString)
            .map(id -> "000000000000000" + id)
            .collect(Collectors.toList());

    assertThat(mockServer.spanIds()).withFailMessage("Unexpected traces in Stackdriver").isEmpty();

    CountDownLatch spanCountdown = new CountDownLatch(3);
    mockServer.setSpanCountdown(spanCountdown);

    for (String id : spanIds) {
      Span span = Span.newBuilder().id(id).traceId(id).name("/a").timestamp(1L).build();

      storage.spanConsumer().accept(asList(span)).execute();
    }

    spanCountdown.await(1, TimeUnit.SECONDS);

    assertThat(spanCountdown.getCount()).isZero();
    assertThat(mockServer.spanIds())
        .as("Not all spans made it to Stackdriver")
        .containsExactlyInAnyOrderElementsOf(spanIds);
  }

  @Configuration
  static class TestConfiguration {
    // TODO(denyska): figure out how to request credentials in StackdriverMockServer
    @Bean("googleCredentials")
    public Credentials mockGoogleCredentials() {
      return new Credentials() {
        @Override
        public String getAuthenticationType() {
          return null;
        }

        @Override
        public Map<String, List<String>> getRequestMetadata(URI uri) {
          return null;
        }

        @Override
        public boolean hasRequestMetadata() {
          return false;
        }

        @Override
        public boolean hasRequestMetadataOnly() {
          return false;
        }

        @Override
        public void refresh() {
        }
      };
    }

    @Bean
    ClientFactory managedChannel() {
      return ClientFactory.builder()
          .tlsCustomizer(tls -> tls.trustManager(InsecureTrustManagerFactory.INSTANCE))
          .build();
    }
  }
}
