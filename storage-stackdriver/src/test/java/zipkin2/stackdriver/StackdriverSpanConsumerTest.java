/**
 * Copyright 2016-2017 The OpenZipkin Authors
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

import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc.TraceServiceImplBase;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Span;

import static io.grpc.CallOptions.DEFAULT;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class StackdriverSpanConsumerTest {
  @Rule public final GrpcServerRule server = new GrpcServerRule().directExecutor();
  TestTraceService traceService = spy(new TestTraceService());
  StackdriverSpanConsumer spanConsumer;

  @Before public void setUp() throws Throwable {
    server.getServiceRegistry().addService(traceService);
    spanConsumer = new StackdriverSpanConsumer(server.getChannel(), "zipkin", DEFAULT);
  }

  @Test public void accept() throws Throwable {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });

    Span span = Span.newBuilder().id("1").traceId("1").name("/a").timestamp(1L).build();
    spanConsumer.accept(asList(span)).execute();

    ArgumentCaptor<PatchTracesRequest> requestCaptor =
        ArgumentCaptor.forClass(PatchTracesRequest.class);

    verify(traceService).patchTraces(requestCaptor.capture(), any());

    PatchTracesRequest request = requestCaptor.getValue();
    assertThat(request.getProjectId())
        .isEqualTo(spanConsumer.projectId);
    assertThat(request.getTraces().getTracesList())
        .hasSize(1);
  }

  static class TestTraceService extends TraceServiceImplBase {
  }

  void onClientCall(Consumer<StreamObserver<Empty>> onClientCall) {
    doAnswer((Answer<Void>) invocationOnMock -> {
      StreamObserver<Empty> observer = ((StreamObserver) invocationOnMock.getArguments()[1]);
      onClientCall.accept(observer);
      return null;
    }).when(traceService).patchTraces(any(PatchTracesRequest.class), any(StreamObserver.class));
  }
}
