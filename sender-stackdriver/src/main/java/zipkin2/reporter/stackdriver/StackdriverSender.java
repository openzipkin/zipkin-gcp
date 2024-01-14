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

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.TraceServiceGrpc;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import com.google.protobuf.UnsafeByteOperations;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import java.io.IOException;
import java.util.List;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;

import static com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag;
import static io.grpc.CallOptions.DEFAULT;

public final class StackdriverSender extends BytesMessageSender.Base {
  static final int DEFAULT_SERVER_TIMEOUT_MS = 5000;

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
    long serverResponseTimeoutMs = DEFAULT_SERVER_TIMEOUT_MS;

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

    public Builder serverResponseTimeoutMs(long serverResponseTimeoutMs) {
      if (serverResponseTimeoutMs <= 0) {
        throw new IllegalArgumentException("Server response timeout must be greater than 0");
      }
      this.serverResponseTimeoutMs = serverResponseTimeoutMs;
      return this;
    }

    public StackdriverSender build() {
      if (projectId == null) throw new NullPointerException("projectId == null");
      return new StackdriverSender(this);
    }
  }

  static final ByteString SPAN_ID_PREFIX = ByteString.copyFromUtf8("/spans/");

  final Channel channel;
  final CallOptions callOptions;
  final ByteString projectName;
  final ByteString traceIdPrefix;
  final boolean shutdownChannelOnClose;
  final int projectNameFieldSize;
  final int spanNameSize;
  final int spanNameFieldSize;
  final long serverResponseTimeoutMs;

  StackdriverSender(Builder builder) {
    super(Encoding.PROTO3);
    channel = builder.channel;
    callOptions = builder.callOptions;
    projectName = ByteString.copyFromUtf8("projects/" + builder.projectId);
    serverResponseTimeoutMs = builder.serverResponseTimeoutMs;
    traceIdPrefix = projectName.concat(ByteString.copyFromUtf8("/traces/"));
    shutdownChannelOnClose = builder.shutdownChannelOnClose;
    projectNameFieldSize = CodedOutputStream.computeBytesSize(1, projectName);

    // The size of the contents of the Span.name field, used to preallocate the correct sized
    // buffer when computing Span.name.
    spanNameSize = traceIdPrefix.size() + 32 + SPAN_ID_PREFIX.size() + 16;

    spanNameFieldSize = CodedOutputStream.computeTagSize(1)
        + CodedOutputStream.computeUInt32SizeNoTag(spanNameSize) + spanNameSize;
  }

  @Override public int messageMaxBytes() {
    return 1024 * 1024; // 1 MiB for now
  }

  @Override public int messageSizeInBytes(List<byte[]> traceIdPrefixedSpans) {
    int length = traceIdPrefixedSpans.size();
    if (length == 0) return 0;
    if (length == 1) return messageSizeInBytes(traceIdPrefixedSpans.get(0).length);

    int size = projectNameFieldSize;
    for (int i = 0; i < length; i++) {
      size += spanFieldSize(traceIdPrefixedSpans.get(i).length);
    }

    return size;
  }

  @Override public int messageSizeInBytes(int traceIdPrefixedSpanSize) {
    return projectNameFieldSize + spanFieldSize(traceIdPrefixedSpanSize);
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public void send(List<byte[]> traceIdPrefixedSpans) throws IOException {
    if (closeCalled) throw new ClosedSenderException();

    BatchWriteSpansRequest.Builder request = BatchWriteSpansRequest.newBuilder()
        .setNameBytes(projectName);
    for (byte[] traceIdPrefixedSpan : traceIdPrefixedSpans) {
      request.addSpans(parseTraceIdPrefixedSpan(traceIdPrefixedSpan, spanNameSize, traceIdPrefix));
    }

    ClientCall<BatchWriteSpansRequest, Empty> call =
        channel.newCall(TraceServiceGrpc.getBatchWriteSpansMethod(), callOptions);

    AwaitableUnaryClientCallListener<Empty> listener =
        new AwaitableUnaryClientCallListener<>(serverResponseTimeoutMs);
    try {
      call.start(listener, new Metadata());
      call.request(1);
      call.sendMessage(request.build());
      call.halfClose();
    } catch (RuntimeException | Error t) {
      call.cancel(null, t);
      throw t;
    }
    listener.await();
  }

  @Override public String toString() {
    return "StackdriverSender{" + projectName.toStringUtf8() + "}";
  }

  @Override public void close() {
    if (!shutdownChannelOnClose) return;
    if (closeCalled) return;
    closeCalled = true;
    ((ManagedChannel) channel).shutdownNow();
  }

  static Span parseTraceIdPrefixedSpan(
      byte[] traceIdPrefixedSpan, int spanNameSize, ByteString traceIdPrefix) {
    // start parsing after the trace ID
    int off = 32, len = traceIdPrefixedSpan.length - off;
    Span.Builder span = Span.newBuilder();
    try {
      span.mergeFrom(traceIdPrefixedSpan, off, len);
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    int offset = 0;

    // Span name in Stackdriver is the global unique identifier of the span, including project ID,
    // trace ID, and span ID. It is _not_ the same as the name in Zipkin which is the semantic name.
    byte[] spanName = new byte[spanNameSize];
    traceIdPrefix.copyTo(spanName, offset);
    offset += traceIdPrefix.size();
    System.arraycopy(traceIdPrefixedSpan, 0, spanName, offset, 32);
    offset += 32;
    SPAN_ID_PREFIX.copyTo(spanName, offset);
    offset += SPAN_ID_PREFIX.size();
    span.getSpanIdBytes().copyTo(spanName, offset);

    span.setNameBytes(UnsafeByteOperations.unsafeWrap(spanName));
    return span.build();
  }

  int spanFieldSize(int traceIdPrefixedSpanSize) {
    int sizeOfSpanMessage = traceIdPrefixedSpanSize - 32 + spanNameFieldSize;
    return CodedOutputStream.computeTagSize(2)
        + computeUInt32SizeNoTag(sizeOfSpanMessage) + sizeOfSpanMessage;
  }
}
