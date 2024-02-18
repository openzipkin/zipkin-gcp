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
package zipkin2.reporter.stackdriver;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.stackdriver.zipkin.StackdriverEncoder;
import zipkin2.translation.stackdriver.SpanTranslator;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Same as StackdriverSpanConsumerTest: tests everything wired together */
@ExtendWith(GrpcCleanupExtension.class)
class AsyncReporterStackdriverSenderTest {
  TestTraceService traceService = spy(new TestTraceService());
  String projectId = "test-project";
  AsyncReporter<Span> reporter;

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

    reporter = AsyncReporter.builder(
            StackdriverSender.newBuilder(channel).projectId(projectId).build())
        .messageTimeout(0, TimeUnit.MILLISECONDS) // don't spawn a thread
        .build(StackdriverEncoder.V2);
  }

  @Test void send_empty() {
    reporter.flush();

    ArgumentCaptor<BatchWriteSpansRequest> requestCaptor =
        ArgumentCaptor.forClass(BatchWriteSpansRequest.class);

    verify(traceService).batchWriteSpans(requestCaptor.capture(), any());

    BatchWriteSpansRequest request = requestCaptor.getValue();
    assertThat(request.getName()).isEqualTo("projects/" + projectId);

    assertThat(request.getSpansList()).isEmpty();
  }

  @Test void send() {
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

    assertThat(request.getSpansList()).containsExactlyElementsOf(
        SpanTranslator.translate(projectId, asList(TestObjects.CLIENT_SPAN)));
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

  static class TestTraceService extends TraceServiceGrpc.TraceServiceImplBase {
  }
}
