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
package zipkin2.storage.stackdriver;


import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.Empty;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.util.List;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.reporter.stackdriver.internal.UnaryClientCall;
import zipkin2.storage.SpanConsumer;
import zipkin2.translation.stackdriver.SpanTranslator;

/**
 * Consumes Zipkin spans, translates them to Stackdriver spans using a provided TraceTranslator, and
 * issues a {@link BatchWriteSpansRequest}.
 */
final class StackdriverSpanConsumer implements SpanConsumer {

  final Channel channel;
  final CallOptions callOptions;
  final String projectId;
  final String projectName;

  StackdriverSpanConsumer(Channel channel, CallOptions callOptions, String projectId) {
    this.channel = channel;
    this.callOptions = callOptions;
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

  private final class BatchWriteSpansCall extends UnaryClientCall<BatchWriteSpansRequest, Empty> {

    BatchWriteSpansCall(BatchWriteSpansRequest request) {
      super(channel, TraceServiceGrpc.getBatchWriteSpansMethod(), callOptions, request);
    }

    @Override
    public String toString() {
      return "BatchWriteSpansCall{" + request() + "}";
    }

    @Override
    public BatchWriteSpansCall clone() {
      return new BatchWriteSpansCall(request());
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
