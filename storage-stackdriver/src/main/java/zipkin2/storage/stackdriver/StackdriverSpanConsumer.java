/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.stackdriver;

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.linecorp.armeria.client.grpc.protocol.UnaryGrpcClient;
import com.linecorp.armeria.common.util.Exceptions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

  static final String BATCH_WRITE_SPANS_PATH =
      "/google.devtools.cloudtrace.v2.TraceService/BatchWriteSpans";

  final UnaryGrpcClient grpcClient;
  final String projectId;
  final String projectName;

  StackdriverSpanConsumer(UnaryGrpcClient grpcClient, String projectId) {
    this.grpcClient = grpcClient;
    this.projectId = projectId;
    projectName = "projects/" + projectId;
  }

  @Override public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    List<com.google.devtools.cloudtrace.v2.Span> stackdriverSpans =
        SpanTranslator.translate(projectId, spans);
    BatchWriteSpansRequest request =
        BatchWriteSpansRequest.newBuilder()
            .setName(projectName)
            .addAllSpans(stackdriverSpans)
            .build();
    return new BatchWriteSpansCall(grpcClient, request);
  }

  static final class BatchWriteSpansCall extends Call.Base<Void> {
    final UnaryGrpcClient grpcClient;
    final BatchWriteSpansRequest request;

    volatile CompletableFuture<byte[]> responseFuture;

    BatchWriteSpansCall(UnaryGrpcClient grpcClient, BatchWriteSpansRequest request) {
      this.grpcClient = grpcClient;
      this.request = request;
    }

    @Override public String toString() {
      return "BatchWriteSpansCall{" + request + "}";
    }

    @Override public BatchWriteSpansCall clone() {
      return new BatchWriteSpansCall(grpcClient, request);
    }

    @Override protected Void doExecute() {
      try {
        sendRequest().join();
        return null;
      } catch (CompletionException e) {
        propagateIfFatal(e);
        Exceptions.throwUnsafely(e.getCause());
        return null;  // Unreachable
      }
    }

    @Override protected void doEnqueue(Callback<Void> callback) {
      try {
        sendRequest().handle((resp, t) -> {
          if (t != null) {
            callback.onError(t);
          } else {
            callback.onSuccess(null);
          }
          return null;
        });
      } catch (RuntimeException | Error e) {
        Call.propagateIfFatal(e);
        callback.onError(e);
        throw e;
      }
    }

    @Override protected void doCancel() {
      CompletableFuture<byte[]> responseFuture = this.responseFuture;
      if (responseFuture != null) {
        responseFuture.cancel(true);
      }
    }

    private CompletableFuture<byte[]> sendRequest() {
      CompletableFuture<byte[]> responseFuture = grpcClient.execute(
          BATCH_WRITE_SPANS_PATH, request.toByteArray());
      this.responseFuture = responseFuture;
      return responseFuture;
    }
  }
}
