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
package zipkin2.stackdriver;

import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.Traces;
import com.google.protobuf.Empty;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.util.Collection;
import java.util.List;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.stackdriver.call.UnaryClientCall;
import zipkin2.storage.SpanConsumer;
import zipkin2.translation.stackdriver.TraceTranslator;

import static com.google.devtools.cloudtrace.v1.TraceServiceGrpc.METHOD_PATCH_TRACES;

/**
 * Consumes Zipkin spans, translates them to Stackdriver spans using a provided
 * TraceTranslator, and issues a {@link PatchTracesRequest}.
 */
final class StackdriverSpanConsumer implements SpanConsumer {

  final Channel channel;
  final String projectId;
  final CallOptions callOptions;

  StackdriverSpanConsumer(Channel channel, String projectId, CallOptions callOptions) {
    this.channel = channel;
    this.projectId = projectId;
    this.callOptions = callOptions;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    Collection<Trace> traces = TraceTranslator.translateSpans(projectId, spans);
    PatchTracesRequest request =
        PatchTracesRequest.newBuilder()
            .setProjectId(projectId)
            .setTraces(Traces.newBuilder().addAllTraces(traces).build())
            .build();
    return new PatchTracesCall(request).map(EmptyToVoid.INSTANCE);
  }

  private final class PatchTracesCall extends UnaryClientCall<PatchTracesRequest, Empty> {

    PatchTracesCall(PatchTracesRequest request) {
      super(channel, METHOD_PATCH_TRACES, callOptions, request);
    }

    @Override
    public String toString() {
      return "PatchTracesCall{" + request() + "}";
    }

    @Override
    public PatchTracesCall clone() {
      return new PatchTracesCall(request());
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
