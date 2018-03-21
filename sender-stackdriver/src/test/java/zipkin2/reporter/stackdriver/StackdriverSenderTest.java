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
package zipkin2.reporter.stackdriver;

import com.google.common.collect.ImmutableList;
import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc.TraceServiceImplBase;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static zipkin2.TestObjects.FRONTEND;

public class StackdriverSenderTest {
  @Rule public final GrpcServerRule server = new GrpcServerRule().directExecutor();
  TestTraceService traceService = spy(new TestTraceService());
  String projectId = "test-project";
  StackdriverSender sender;

  Span span = Span.newBuilder().traceId("1").id("a").name("get").localEndpoint(FRONTEND).build();

  @Before public void setUp() {
    server.getServiceRegistry().addService(traceService);
    sender = StackdriverSender.newBuilder(server.getChannel()).projectId(projectId).build();
  }

  @Test public void messageSizeInBytes_single() throws IOException {
    byte[] oneTrace = StackdriverEncoder.V1.encode(span);
    List<byte[]> encodedSpans = ImmutableList.of(oneTrace);

    int size = sender.messageSizeInBytes(oneTrace.length);
    assertThat(sender.messageSizeInBytes(encodedSpans))
        .isEqualTo(size);

    assertMessageSizeSameAsActual(encodedSpans);
  }

  @Test public void messageSizeInBytes_multipleTraces() throws IOException {
    // intentionally change only the boundaries to help break any offset-based logic
    byte[] trace1 = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("10000000000000000000000000000002").build()
    );
    byte[] trace2 = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("10000000000000000000000000000001").build()
    );
    byte[] trace3 = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("20000000000000000000000000000001").build()
    );

    List<byte[]> encodedSpans = ImmutableList.of(trace1, trace2, trace3);

    assertMessageSizeSameAsActual(encodedSpans);
  }

  @Test public void messageSizeInBytes_multipleSpans() throws IOException {
    // intentionally change only the boundaries to help break any offset-based logic
    byte[] trace1 = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("10000000000000000000000000000002").build()
    );
    byte[] trace2 = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("10000000000000000000000000000001").build()
    );
    // intentionally out-of-order
    byte[] trace1_spanb = StackdriverEncoder.V1.encode(
        span.toBuilder().traceId("10000000000000000000000000000002").id("b").build()
    );

    List<byte[]> encodedSpans = ImmutableList.of(trace1, trace2, trace1_spanb);

    assertMessageSizeSameAsActual(encodedSpans);
  }

  void assertMessageSizeSameAsActual(List<byte[]> encodedSpans) throws IOException {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });

    sender.sendSpans(encodedSpans).execute();

    // verify our estimate is correct
    assertThat(sender.messageSizeInBytes(encodedSpans))
        .isEqualTo(takeRequest().getSerializedSize());
  }

  void onClientCall(Consumer<StreamObserver<Empty>> onClientCall) {
    doAnswer((Answer<Void>) invocationOnMock -> {
      StreamObserver<Empty> observer = ((StreamObserver) invocationOnMock.getArguments()[1]);
      onClientCall.accept(observer);
      return null;
    }).when(traceService).patchTraces(any(PatchTracesRequest.class), any(StreamObserver.class));
  }

  PatchTracesRequest takeRequest() {
    ArgumentCaptor<PatchTracesRequest> requestCaptor =
        ArgumentCaptor.forClass(PatchTracesRequest.class);

    verify(traceService).patchTraces(requestCaptor.capture(), any());

    return requestCaptor.getValue();
  }

  static class TestTraceService extends TraceServiceImplBase {
  }
}
