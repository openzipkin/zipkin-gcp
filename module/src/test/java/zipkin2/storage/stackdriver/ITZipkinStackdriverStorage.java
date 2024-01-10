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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.devtools.cloudtrace.v1.GetTraceRequest;
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.auth.MoreCallCredentials;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageModule;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageProperties;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.api.Assumptions.assumeThatCode;
import static org.awaitility.Awaitility.await;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;

/** Integration test against Stackdriver Trace on a real GCP project */
public class ITZipkinStackdriverStorage {
  final String projectId = "zipkin-gcp-ci";
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  StackdriverStorage storage;
  ZipkinStackdriverStorageProperties storageProperties;
  ManagedChannel channel;
  TraceServiceGrpc.TraceServiceBlockingStub traceServiceGrpcV1;

  @BeforeEach
  public void init() throws IOException {
    // Application Default credential is configured using the GOOGLE_APPLICATION_CREDENTIALS env var
    // See: https://cloud.google.com/docs/authentication/production#providing_credentials_to_your_application

    // TODO: implement GOOGLE_APPLICATION_CREDENTIALS_BASE64 also
    String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    assumeThat(credentialsPath).isNotBlank();
    assumeThat(new File(credentialsPath)).exists();
    assumeThatCode(GoogleCredentials::getApplicationDefault).doesNotThrowAnyException();

    TestPropertyValues.of(
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:" + projectId).applyTo(context);
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageModule.class);
    context.refresh();
    storage = context.getBean(StackdriverStorage.class);
    storageProperties = context.getBean(ZipkinStackdriverStorageProperties.class);

    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
        .createScoped("https://www.googleapis.com/auth/cloud-platform");

    channel = ManagedChannelBuilder.forTarget("cloudtrace.googleapis.com")
        .build();
    traceServiceGrpcV1 = TraceServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(MoreCallCredentials.from(credentials));
  }

  @AfterEach
  public void close() {
    context.close();

    if (channel != null) {
      channel.shutdownNow();
    }
  }

  @Test void healthCheck() {
    assertThat(storage.check().ok()).isTrue();
  }

  @Test void spanConsumer() throws IOException {
    Random random = new Random();
    Span span = Span.newBuilder()
        .traceId(random.nextLong(), random.nextLong())
        .parentId("1")
        .id("2")
        .name("get")
        .kind(Span.Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .remoteEndpoint(BACKEND)
        .timestamp((TODAY + 50L) * 1000L)
        .duration(200000L)
        .addAnnotation((TODAY + 100L) * 1000L, "foo")
        .putTag("http.path", "/api")
        .putTag("clnt/finagle.version", "6.45.0")
        .build();

    storage.spanConsumer().accept(asList(span)).execute();

    Trace trace = await()
        .atLeast(1, TimeUnit.SECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .ignoreExceptionsMatching(e ->
            e instanceof StatusRuntimeException sre &&
                sre.getStatus().getCode() == Status.Code.NOT_FOUND
        )
        .until(() -> traceServiceGrpcV1.getTrace(GetTraceRequest.newBuilder()
            .setProjectId(projectId)
            .setTraceId(span.traceId())
            .build()), t -> t.getSpansCount() == 1);

    assertThat(trace.getSpans(0).getSpanId()).isEqualTo(2);
    assertThat(trace.getSpans(0).getParentSpanId()).isEqualTo(1);
  }
}
