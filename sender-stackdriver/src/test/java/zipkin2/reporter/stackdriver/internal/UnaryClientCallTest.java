/*
 * Copyright 2016-2023 The OpenZipkin Authors
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
package zipkin2.reporter.stackdriver.internal;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Callback;

import static io.grpc.CallOptions.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static zipkin2.reporter.stackdriver.internal.UnaryClientCall.DEFAULT_SERVER_TIMEOUT_MS;

@ExtendWith(GrpcCleanupExtension.class)
class UnaryClientCallTest {
  final TestTraceService traceService = spy(new TestTraceService());

  static class BatchWriteSpansCall extends UnaryClientCall<BatchWriteSpansRequest, Empty> {
    final Channel channel;

    BatchWriteSpansCall(Channel channel, BatchWriteSpansRequest request,
        long serverResponseTimeout) {
      super(channel, TraceServiceGrpc.getBatchWriteSpansMethod(), DEFAULT, request,
          serverResponseTimeout);
      this.channel = channel;
    }

    @Override
    public BatchWriteSpansCall clone() {
      return new BatchWriteSpansCall(channel, request(), DEFAULT_SERVER_TIMEOUT_MS);
    }
  }

  BatchWriteSpansCall call;

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

    call = new BatchWriteSpansCall(channel, BatchWriteSpansRequest.newBuilder().build(),
        DEFAULT_SERVER_TIMEOUT_MS);
  }

  @Test void execute_success() throws Throwable {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    call.execute();

    verifyPatchRequestSent();
  }

  @Test void enqueue_success() throws Throwable {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    awaitCallbackResult();

    verifyPatchRequestSent();
  }

  void verifyPatchRequestSent() {
    ArgumentCaptor<BatchWriteSpansRequest> requestCaptor =
        ArgumentCaptor.forClass(BatchWriteSpansRequest.class);

    verify(traceService).batchWriteSpans(requestCaptor.capture(), any());

    BatchWriteSpansRequest request = requestCaptor.getValue();
    assertThat(request).isEqualTo(BatchWriteSpansRequest.getDefaultInstance());
  }

  @Test void accept_execute_serverError() throws Throwable {
    assertThrows(StatusRuntimeException.class, () -> {
      onClientCall(observer -> observer.onError(new IllegalStateException()));

      call.execute();
    });
  }

  @Test void accept_enqueue_serverError() throws Throwable {
    assertThrows(StatusRuntimeException.class, () -> {
      onClientCall(observer -> observer.onError(new IllegalStateException()));

      awaitCallbackResult();
    });
  }

  @Test void execute_timeout() throws Throwable {
    assertThrows(IllegalStateException.class, () -> {
      long overriddenTimeout = 50;
      call = new BatchWriteSpansCall(call.channel, BatchWriteSpansRequest.newBuilder().build(),
          overriddenTimeout);
      onClientCall(
          observer ->
              Executors.newSingleThreadExecutor().submit(() ->
              {
                try {
                  Thread.sleep(overriddenTimeout + 10);
                } catch (InterruptedException e) {
                }
                observer.onCompleted();
              }));

      call.execute();
    });
  }

  static class TestTraceService extends TraceServiceGrpc.TraceServiceImplBase {
  }

  void awaitCallbackResult() throws Throwable {
    AtomicReference<Throwable> ref = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    call.enqueue(
        new Callback<Empty>() {
          @Override
          public void onSuccess(Empty empty) {
            latch.countDown();
          }

          @Override
          public void onError(Throwable throwable) {
            ref.set(throwable);
            latch.countDown();
          }
        });
    latch.await(10, TimeUnit.MILLISECONDS);
    if (ref.get() != null) throw ref.get();
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
}
