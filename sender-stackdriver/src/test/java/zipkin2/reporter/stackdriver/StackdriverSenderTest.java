/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.common.collect.ImmutableList;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Span;
import zipkin2.reporter.stackdriver.zipkin.StackdriverEncoder;
import zipkin2.translation.stackdriver.SpanTranslator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static zipkin2.TestObjects.FRONTEND;

@ExtendWith(GrpcCleanupExtension.class)
class StackdriverSenderTest {
  TestTraceService traceService = spy(new TestTraceService());
  String projectId = "test-project";
  StackdriverSender sender;

  Span span = Span.newBuilder().traceId("1").id("a").name("get").localEndpoint(FRONTEND).build();

  @BeforeEach void setUp(Resources resources) throws Exception {
    String serverName = InProcessServerBuilder.generateName();

    Server server = InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(traceService)
        .build().start();
    resources.register(server, Duration.ofSeconds(10)); // shutdown deadline

    ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    resources.register(channel, Duration.ofSeconds(10));// close deadline

    sender = StackdriverSender.newBuilder(channel).projectId(projectId).build();
  }

  @Test void verifyRequestSent_single() throws IOException {
    byte[] oneTrace = StackdriverEncoder.V2.encode(span);
    List<byte[]> encodedSpans = ImmutableList.of(oneTrace);

    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    sender.send(encodedSpans);

    // verify our estimate is correct
    int actualSize = takeRequest().getSerializedSize();
    assertThat(sender.messageSizeInBytes(oneTrace.length)).isEqualTo(actualSize);
  }

  @Test void verifyRequestSent_multipleTraces() throws IOException {
    // intentionally change only the boundaries to help break any offset-based logic
    List<Span> spans =
        ImmutableList.of(
            span.toBuilder().traceId("10000000000000000000000000000002").build(),
            span.toBuilder().traceId("10000000000000000000000000000001").build(),
            span.toBuilder().traceId("20000000000000000000000000000001").build(),
            span.toBuilder().traceId("20000000000000000000000000000002").build());

    verifyRequestSent(spans);
  }

  @Test void verifyRequestSent_multipleSpans() throws IOException {
    // intentionally change only the boundaries to help break any offset-based logic
    List<Span> spans =
        ImmutableList.of(
            span.toBuilder().traceId("10000000000000000000000000000002").build(),
            span.toBuilder().traceId("10000000000000000000000000000001").build(),
            // intentionally out-of-order
            span.toBuilder().traceId("10000000000000000000000000000002").id("b").build(),
            span.toBuilder().traceId("10000000000000000000000000000001").id("c").build());

    verifyRequestSent(spans);
  }

  void verifyRequestSent(List<Span> spans) throws IOException {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    List<byte[]> encodedSpans =
        spans.stream().map(StackdriverEncoder.V2::encode).collect(Collectors.toList());

    sender.send(encodedSpans);

    BatchWriteSpansRequest request = takeRequest();

    List<com.google.devtools.cloudtrace.v2.Span> translated =
        SpanTranslator.translate(projectId, spans);

    // sanity check the data
    assertThat(request.getSpansList()).containsExactlyElementsOf(translated);

    // verify our estimate is correct
    int actualSize = request.getSerializedSize();
    assertThat(sender.messageSizeInBytes(encodedSpans)).isEqualTo(actualSize);
  }

  @Test void sendFailureWhenServiceFailsWithKnownGrpcFailure() {
    onClientCall(observer -> {
      observer.onError(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));
    });

    assertThatThrownBy(() -> sender.send(Collections.emptyList()))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("RESOURCE_EXHAUSTED");
  }

  @Test void sendFailureWhenServiceFailsForUnknownReason() {
    onClientCall(observer -> {
      observer.onError(new RuntimeException("oh no"));
    });

    assertThatThrownBy(() -> sender.send(Collections.emptyList()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test void send_empty() throws IOException {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });

    sender.send(Collections.emptyList());
  }

  void onClientCall(Consumer<StreamObserver<Empty>> onClientCall) {
    doAnswer(
        (Answer<Void>)
            invocationOnMock -> {
              StreamObserver<Empty> observer =
                  ((StreamObserver) invocationOnMock.getArguments()[1]);
              onClientCall.accept(observer);
              return null;
            })
        .when(traceService)
        .batchWriteSpans(any(BatchWriteSpansRequest.class), any(StreamObserver.class));
  }

  BatchWriteSpansRequest takeRequest() {
    ArgumentCaptor<BatchWriteSpansRequest> requestCaptor =
        ArgumentCaptor.forClass(BatchWriteSpansRequest.class);

    verify(traceService).batchWriteSpans(requestCaptor.capture(), any());

    return requestCaptor.getValue();
  }

  static class TestTraceService extends TraceServiceGrpc.TraceServiceImplBase {
  }
}
