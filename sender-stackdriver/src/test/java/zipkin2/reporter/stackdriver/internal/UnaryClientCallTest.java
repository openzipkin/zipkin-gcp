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
package zipkin2.reporter.stackdriver.internal;

import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc.TraceServiceImplBase;
import com.google.protobuf.Empty;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Callback;

import static com.google.devtools.cloudtrace.v1.TraceServiceGrpc.METHOD_PATCH_TRACES;
import static io.grpc.CallOptions.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class UnaryClientCallTest {
  @Rule public final GrpcServerRule server = new GrpcServerRule().directExecutor();
  final TestTraceService traceService = spy(new TestTraceService());

  static class PatchTracesCall extends UnaryClientCall<PatchTracesRequest, Empty> {
    final Channel channel;

    PatchTracesCall(Channel channel, PatchTracesRequest request) {
      super(channel, METHOD_PATCH_TRACES, DEFAULT, request);
      this.channel = channel;
    }

    @Override
    public PatchTracesCall clone() {
      return new PatchTracesCall(channel, request());
    }
  }

  PatchTracesCall call;

  @Before public void setUp() throws Throwable {
    server.getServiceRegistry().addService(traceService);
    call = new PatchTracesCall(server.getChannel(), PatchTracesRequest.newBuilder().build());
  }

  @Test public void execute_success() throws Throwable {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });

    call.execute();

    verifyPatchRequestSent();
  }

  @Test public void enqueue_success() throws Throwable {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });

    awaitCallbackResult();

    verifyPatchRequestSent();
  }

  void verifyPatchRequestSent() {
    ArgumentCaptor<PatchTracesRequest> requestCaptor =
        ArgumentCaptor.forClass(PatchTracesRequest.class);

    verify(traceService).patchTraces(requestCaptor.capture(), any());

    PatchTracesRequest request = requestCaptor.getValue();
    assertThat(request).isEqualTo(PatchTracesRequest.getDefaultInstance());
  }

  @Test(expected = StatusRuntimeException.class)
  public void accept_execute_serverError() throws Throwable {
    onClientCall(observer -> observer.onError(new IllegalStateException()));

    call.execute();
  }

  @Test(expected = StatusRuntimeException.class)
  public void accept_enqueue_serverError() throws Throwable {
    onClientCall(observer -> observer.onError(new IllegalStateException()));

    awaitCallbackResult();
  }

  static class TestTraceService extends TraceServiceImplBase {
  }

  void awaitCallbackResult() throws Throwable {
    AtomicReference<Throwable> ref = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    call.enqueue(new Callback<Empty>() {
      @Override public void onSuccess(Empty empty) {
        latch.countDown();
      }

      @Override public void onError(Throwable throwable) {
        ref.set(throwable);
        latch.countDown();
      }
    });
    latch.await(10, TimeUnit.MILLISECONDS);
    if (ref.get() != null) throw ref.get();
  }

  void onClientCall(Consumer<StreamObserver<Empty>> onClientCall) {
    doAnswer((Answer<Void>) invocationOnMock -> {
      StreamObserver<Empty> observer = ((StreamObserver) invocationOnMock.getArguments()[1]);
      onClientCall.accept(observer);
      return null;
    }).when(traceService).patchTraces(any(PatchTracesRequest.class), any(StreamObserver.class));
  }
}
