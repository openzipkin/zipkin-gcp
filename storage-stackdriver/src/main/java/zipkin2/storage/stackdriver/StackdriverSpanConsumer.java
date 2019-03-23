/*
 * Copyright 2016-2019 The OpenZipkin Authors
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


import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;
import zipkin2.translation.stackdriver.SpanTranslator;

/**
 * Consumes Zipkin spans, translates them to Stackdriver spans using a provided TraceTranslator, and
 * issues a {@link BatchWriteSpansRequest}.
 */
final class StackdriverSpanConsumer implements SpanConsumer {

  final TraceServiceGrpc.TraceServiceFutureStub traceService;
  final String projectId;
  final String projectName;

  StackdriverSpanConsumer(TraceServiceGrpc.TraceServiceFutureStub traceService, String projectId) {
    this.traceService = traceService;
    this.projectId = projectId;
    projectName = "projects/" + projectId;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    List<com.google.devtools.cloudtrace.v2.Span> stackdriverSpans =
        SpanTranslator.translate(projectId, spans);
    BatchWriteSpansRequest request =
        BatchWriteSpansRequest.newBuilder()
            .setName(projectName)
            .addAllSpans(stackdriverSpans)
            .build();
    return new BatchWriteSpansCall(request).map(EmptyToVoid.INSTANCE);
  }

  private final class BatchWriteSpansCall extends Call.Base<Empty> {

    final BatchWriteSpansRequest request;

    volatile ListenableFuture<Empty> responseFuture;

    BatchWriteSpansCall(BatchWriteSpansRequest request) {
      this.request = request;
    }

    @Override
    public String toString() {
      return "BatchWriteSpansCall{" + request + "}";
    }

    @Override
    public BatchWriteSpansCall clone() {
      return new BatchWriteSpansCall(request);
    }

    @Override protected Empty doExecute() {
      return Futures.getUnchecked(sendRequest());
    }

    @Override protected void doEnqueue(Callback<Empty> callback) {
      Futures.addCallback(sendRequest(),
          new FutureCallback<Empty>() {
            @Override public void onSuccess(@NullableDecl Empty empty) {
              callback.onSuccess(empty);
            }

            @Override public void onFailure(Throwable throwable) {
              callback.onError(throwable);
            }
          },
          MoreExecutors.directExecutor());
    }

    @Override protected void doCancel() {
      ListenableFuture<Empty> responseFuture = this.responseFuture;
      if (responseFuture != null) {
        responseFuture.cancel(true);
      }
    }

    private ListenableFuture<Empty> sendRequest() {
      ListenableFuture<Empty> responseFuture = traceService.batchWriteSpans(request);
      this.responseFuture = responseFuture;
      return responseFuture;
    }
  }

  enum EmptyToVoid implements Call.Mapper<Empty, Void> {
    INSTANCE {
      @Override
      public Void map(Empty empty) {
        return null;
      }
    };
  }
}
