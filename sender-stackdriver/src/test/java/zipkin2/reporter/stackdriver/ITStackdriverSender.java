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
package zipkin2.reporter.stackdriver;

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.reporter.AsyncReporter;
import zipkin2.translation.stackdriver.SpanTranslator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Same as ITStackdriverSpanConsumer: tests everything wired together */
public class ITStackdriverSender {
  @Rule public final GrpcServerRule server = new GrpcServerRule().directExecutor();
  TestTraceService traceService = spy(new TestTraceService());
  String projectId = "test-project";
  AsyncReporter<Span> reporter;

  @Before
  public void setUp() {
    server.getServiceRegistry().addService(traceService);
    reporter =
        AsyncReporter.builder(
                StackdriverSender.newBuilder(server.getChannel()).projectId(projectId).build())
            .messageTimeout(0, TimeUnit.MILLISECONDS) // don't spawn a thread
            .build(StackdriverEncoder.V1);
  }

  @Test
  public void sendSpans_empty() {
    reporter.flush();

    verify(traceService, never()).batchWriteSpans(any(), any());
  }

  @Test
  public void sendSpans() {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    reporter.report(TestObjects.CLIENT_SPAN);
    reporter.flush();

    ArgumentCaptor<BatchWriteSpansRequest> requestCaptor =
        ArgumentCaptor.forClass(BatchWriteSpansRequest.class);

    verify(traceService).batchWriteSpans(requestCaptor.capture(), any());

    BatchWriteSpansRequest request = requestCaptor.getValue();
    assertThat(request.getName()).isEqualTo("projects/" + projectId);

    assertThat(request.getSpansList()).containsExactly(
        SpanTranslator.translate(com.google.devtools.cloudtrace.v2.Span.newBuilder(),
            TestObjects.CLIENT_SPAN).build());
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

  static class TestTraceService extends TraceServiceGrpc.TraceServiceImplBase {}
}
