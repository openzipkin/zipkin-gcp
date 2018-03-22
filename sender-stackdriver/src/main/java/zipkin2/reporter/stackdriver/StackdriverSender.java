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

import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.Traces;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import io.grpc.CallOptions;
import io.grpc.Channel;
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
    ManagedChannel channel = ManagedChannelBuilder.forTarget("cloudtrace.googleapis.com").build();
    Builder result = newBuilder(channel);
    result.shutdownChannelOnClose = true;
    return result;
  }

  public static Builder newBuilder(Channel channel) { // visible for testing
    return new Builder(channel);
  }

  public static final class Builder {
    final Channel channel;
    String projectId;
    CallOptions callOptions = DEFAULT;
    boolean shutdownChannelOnClose;

    Builder(Channel channel) {
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

  final Channel channel;
  final CallOptions callOptions;
  final String projectId;
  final boolean shutdownChannelOnClose;
  final int projectIdFieldSize;

  StackdriverSender(Builder builder) {
    channel = builder.channel;
    callOptions = builder.callOptions;
    projectId = builder.projectId;
    shutdownChannelOnClose = builder.shutdownChannelOnClose;
    projectIdFieldSize = 1 /* field no */ + CodedOutputStream.computeStringSizeNoTag(projectId);
  }

  @Override public Encoding encoding() {
    return Encoding.PROTO3;
  }

  @Override public int messageMaxBytes() {
    return 1024 * 1024; // 1 MiB for now
  }

  // re-use trace collator to avoid re-allocating arrays
  final ThreadLocal<TraceCollator> traceCollator = new ThreadLocal<TraceCollator>() {
    @Override protected TraceCollator initialValue() {
      return new TraceCollator();
    }
  };

  @Override public int messageSizeInBytes(List<byte[]> traceIdPrefixedSpans) {
    int length = traceIdPrefixedSpans.size();
    if (length == 0) return 0;
    if (length == 1) return messageSizeInBytes(traceIdPrefixedSpans.get(0).length);

    PatchTracesRequestSizer sizer = new PatchTracesRequestSizer(projectIdFieldSize);
    traceCollator.get().collate(traceIdPrefixedSpans, sizer);
    return sizer.finish();
  }

  @Override public int messageSizeInBytes(int traceIdPrefixedSpanSize) {
    return PatchTracesRequestSizer.size(projectIdFieldSize, traceIdPrefixedSpanSize - 32);
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public Call<Void> sendSpans(List<byte[]> traceIdPrefixedSpans) {
    if (closeCalled) throw new IllegalStateException("closed");
    int length = traceIdPrefixedSpans.size();
    if (length == 0) return Call.create(null);

    Traces traces;
    if (length == 1) {
      traces = TracesParser.parse(projectId, traceIdPrefixedSpans.get(0));
    } else {
      TracesParser parser = new TracesParser(projectId);
      traceCollator.get().collate(traceIdPrefixedSpans, parser);
      traces = parser.finish();
    }

    PatchTracesRequest request = PatchTracesRequest.newBuilder()
        .setProjectId(projectId)
        .setTraces(traces)
        .build();

    return new PatchTracesCall(request).map(EmptyToVoid.INSTANCE);
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
    ((ManagedChannel) channel).shutdownNow();
  }

  final class PatchTracesCall extends UnaryClientCall<PatchTracesRequest, Empty> {

    PatchTracesCall(PatchTracesRequest request) {
      super(channel, METHOD_PATCH_TRACES, callOptions, request);
    }

    @Override public String toString() {
      return "PatchTracesCall{" + request() + "}";
    }

    @Override public PatchTracesCall clone() {
      return new PatchTracesCall(request());
    }
  }

  enum EmptyToVoid implements Call.Mapper<Empty, Void> {
    INSTANCE {
      @Override public Void map(Empty empty) {
        return null;
      }
    }
  }
}
