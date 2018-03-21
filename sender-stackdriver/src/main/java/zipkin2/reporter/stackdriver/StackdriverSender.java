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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.devtools.cloudtrace.v1.Traces;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;
import zipkin2.reporter.stackdriver.internal.UnaryClientCall;

import static com.google.devtools.cloudtrace.v1.TraceServiceGrpc.METHOD_PATCH_TRACES;
import static io.grpc.CallOptions.DEFAULT;

public final class StackdriverSender extends Sender {

  public static Builder newBuilder() {
    return newBuilder("cloudtrace.googleapis.com");
  }

  public static Builder newBuilder(String apiHost) {
    if (apiHost == null) throw new NullPointerException("apiHost == null");
    Builder result = newBuilder(ManagedChannelBuilder.forTarget(apiHost).build());
    result.shutdownChannelOnClose = true;
    return result;
  }

  public static Builder newBuilder(ManagedChannel channel) { // visible for testing
    return new Builder(channel);
  }

  public static final class Builder {
    final ManagedChannel channel;
    String projectId;
    CallOptions callOptions = DEFAULT;
    boolean shutdownChannelOnClose;

    Builder(ManagedChannel channel) {
      if (channel == null) throw new NullPointerException("channel == null");
      this.channel = channel;
    }

    public Builder projectId(String projectId) {
      if (projectId == null) throw new NullPointerException("projectId == null");
      this.projectId = projectId;
      return this;
    }

    public Builder callOptions(CallOptions callOptions) {
      if (callOptions == null) throw new NullPointerException("callOptions == null");
      this.callOptions = callOptions;
      return this;
    }

    public StackdriverSender build() {
      if (projectId == null) throw new NullPointerException("projectId == null");
      return new StackdriverSender(this);
    }
  }

  final ManagedChannel channel;
  final CallOptions callOptions;
  final String projectId;
  final boolean shutdownChannelOnClose;
  final int projectIdInBytes;

  StackdriverSender(Builder builder) {
    channel = builder.channel;
    callOptions = builder.callOptions;
    projectId = builder.projectId;
    shutdownChannelOnClose = builder.shutdownChannelOnClose;
    projectIdInBytes = 1 /* field no */ + CodedOutputStream.computeStringSizeNoTag(projectId);
  }

  @Override public Encoding encoding() {
    return Encoding.PROTO3;
  }

  @Override public int messageMaxBytes() {
    return 1024 * 1024; // 1 MiB for now
  }

  // StackdriverMessageSizer is not thread safe
  static final ThreadLocal<StackdriverMessageSizer> MESSAGE_SIZER =
      new ThreadLocal<StackdriverMessageSizer>() {
        @Override protected StackdriverMessageSizer initialValue() {
          return new StackdriverMessageSizer();
        }
      };

  @Override public int messageSizeInBytes(List<byte[]> encodedTraces) {
    return MESSAGE_SIZER.get().messageSizeInBytes(projectIdInBytes, encodedTraces);
  }

  @Override public int messageSizeInBytes(int traceSizeInBytes) {
    return StackdriverMessageSizer.messageSizeInBytes(projectIdInBytes, traceSizeInBytes);
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public Call<Void> sendSpans(List<byte[]> encodedSpans) {
    if (closeCalled) throw new IllegalStateException("closed");
    if (encodedSpans.isEmpty()) return Call.create(null);
    PatchTracesRequest request = encodePatchTracesRequest(projectId, encodedSpans);
    return new PatchTracesCall(request).map(EmptyToVoid.INSTANCE);
  }

  /**
   * @param projectId stable for all spans
   * @param encodedTraces unsorted serialized {@link Trace} objects that have only trace_id, and a single element span list.
   */
  static PatchTracesRequest encodePatchTracesRequest(String projectId, List<byte[]> encodedTraces) {
    // TODO: the logic below could be rewritten to lazy chain all the byte arrays, eliminating a lot
    // of GC churn. However, this is an optimization that can be done at any time.

    // decode (traceid, span) from the input bytes and sort by trace ID
    Multimap<String, TraceSpan> indexedById = MultimapBuilder.treeKeys().arrayListValues().build();
    for (int i = 0, length = encodedTraces.size(); i < length; i++) {
      try {
        Trace trace = Trace.parseFrom(encodedTraces.get(i));
        indexedById.put(trace.getTraceId(), trace.getSpans(0));
      } catch (InvalidProtocolBufferException e) {
        throw new AssertionError(e);
      }
    }

    // Add the project ID to each trace
    Traces.Builder traces = Traces.newBuilder();
    for (String traceId : indexedById.keySet()) {
      traces.addTraces(Trace.newBuilder()
          .setTraceId(traceId)
          .setProjectId(projectId)
          .addAllSpans(indexedById.get(traceId)).build());
    }

    return PatchTracesRequest.newBuilder().setProjectId(projectId).setTraces(traces).build();
  }

  @Override public CheckResult check() {
    return CheckResult.OK;
  }

  @Override public final String toString() {
    return "StackdriverSender{" + projectId + "}";
  }

  @Override public void close() {
    if (!shutdownChannelOnClose) return;
    if (closeCalled) return;
    closeCalled = true;
    channel.shutdownNow();
  }

  final class PatchTracesCall extends UnaryClientCall<PatchTracesRequest, Empty> {

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
