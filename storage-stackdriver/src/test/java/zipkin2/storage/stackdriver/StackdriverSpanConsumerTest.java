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
package zipkin2.storage.stackdriver;

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import com.linecorp.armeria.common.grpc.protocol.ArmeriaStatusException;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import zipkin2.CheckResult;
import zipkin2.TestObjects;
import zipkin2.storage.SpanConsumer;
import zipkin2.translation.stackdriver.SpanTranslator;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Same as AsyncReporterStackdriverSenderTest: tests everything wired together */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StackdriverSpanConsumerTest {

  final TestTraceService traceService = spy(new TestTraceService());

  @RegisterExtension ServerExtension server = new ServerExtension() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service(GrpcService.builder()
          .addService(traceService)
          .build());
    }
  };

  String projectId = "test-project";
  StackdriverStorage storage;
  SpanConsumer spanConsumer;

  @BeforeEach
  void setUp() {
    storage = StackdriverStorage.newBuilder("http://localhost:" + server.httpPort())
        .projectId(projectId)
        .build();
    spanConsumer = storage.spanConsumer();
  }

  @Test void accept_empty() throws Exception {
    spanConsumer.accept(Collections.emptyList()).execute();

    verify(traceService, never()).batchWriteSpans(any(), any());
  }

  @Test void accept() throws Exception {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    spanConsumer.accept(asList(TestObjects.CLIENT_SPAN)).execute();

    ArgumentCaptor<BatchWriteSpansRequest> requestCaptor =
        ArgumentCaptor.forClass(BatchWriteSpansRequest.class);

    verify(traceService).batchWriteSpans(requestCaptor.capture(), any());

    BatchWriteSpansRequest request = requestCaptor.getValue();
    assertThat(request.getName()).isEqualTo("projects/" + projectId);
    assertThat(request.getSpansList())
        .isEqualTo(SpanTranslator.translate(projectId, asList(TestObjects.CLIENT_SPAN)));
  }

  @Test void verifyCheckReturnsFailureWhenServiceFailsWithKnownGrpcFailure() {
    onClientCall(observer -> {
      observer.onError(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));
    });

    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error())
        .isInstanceOf(ArmeriaStatusException.class)
        // there is null message, so look at the code https://github.com/line/armeria/issues/2028
        .satisfies(e -> assertThat(((ArmeriaStatusException) e).getCode())
            .isEqualTo(Status.RESOURCE_EXHAUSTED.getCode().value()));
  }

  @Test void verifyCheckReturnsFailureWhenServiceFailsForUnknownReason() {
    onClientCall(observer -> {
      observer.onError(new RuntimeException("oh no"));
    });
    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error())
        .isInstanceOf(ArmeriaStatusException.class)
        // there is null message, so look at the code https://github.com/line/armeria/issues/2028
        .satisfies(e -> assertThat(((ArmeriaStatusException) e).getCode())
            .isEqualTo(Status.UNKNOWN.getCode().value()));
  }

  @Test void verifyCheckReturnsOkWhenExpectedValidationFailure() {
    onClientCall(observer -> {
      observer.onError(new StatusRuntimeException(Status.INVALID_ARGUMENT));
    });
    assertThat(storage.check()).isSameAs(CheckResult.OK);
  }

  @Test void verifyCheckReturnsOkWhenServiceSucceeds() {
    onClientCall(observer -> {
      observer.onNext(Empty.getDefaultInstance());
      observer.onCompleted();
    });
    assertThat(storage.check()).isSameAs(CheckResult.OK);
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
