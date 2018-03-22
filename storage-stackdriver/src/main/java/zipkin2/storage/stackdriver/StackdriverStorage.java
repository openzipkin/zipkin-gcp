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
package zipkin2.storage.stackdriver;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import zipkin2.CheckResult;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import static io.grpc.CallOptions.DEFAULT;

/**
 * StackdriverStorage is a StorageComponent that consumes spans using the Stackdriver
 * TraceSpanConsumer.
 *
 * <p>No SpanStore methods are implemented because read operations are not yet supported.
 */
public final class StackdriverStorage extends StorageComponent {

  public static Builder newBuilder() {
    ManagedChannel channel = ManagedChannelBuilder.forTarget("cloudtrace.googleapis.com").build();
    Builder result = newBuilder(channel);
    result.shutdownChannelOnClose = true;
    return result;
  }

  public static Builder newBuilder(ManagedChannel channel) { // visible for testing
    return new Builder(channel);
  }

  public static final class Builder extends StorageComponent.Builder {
    final Channel channel;
    String projectId;
    CallOptions callOptions = DEFAULT;
    boolean shutdownChannelOnClose;

    Builder(Channel channel) {
      if (channel == null) throw new NullPointerException("channel == null");
      this.channel = channel;
    }

    /** {@inheritDoc} */
    @Override public final Builder strictTraceId(boolean strictTraceId) {
      if (!strictTraceId) {
        throw new UnsupportedOperationException("strictTraceId cannot be disabled");
      }
      return this;
    }

    /** {@inheritDoc} */
    @Override public final Builder searchEnabled(boolean searchEnabled) {
      if (!searchEnabled) {
        throw new UnsupportedOperationException("searchEnabled cannot be disabled");
      }
      return this;
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

    @Override public StackdriverStorage build() {
      if (projectId == null) throw new NullPointerException("projectId == null");
      return new StackdriverStorage(this);
    }
  }

  final Channel channel;
  final CallOptions callOptions;
  final String projectId;
  final boolean shutdownChannelOnClose;

  StackdriverStorage(Builder builder) {
    channel = builder.channel;
    callOptions = builder.callOptions;
    projectId = builder.projectId;
    shutdownChannelOnClose = builder.shutdownChannelOnClose;
  }

  @Override public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public SpanConsumer spanConsumer() {
    return new StackdriverSpanConsumer(channel, callOptions, projectId);
  }

  @Override public CheckResult check() {
    return CheckResult.OK;
  }

  @Override public final String toString() {
    return "StackdriverSender{" + projectId + "}";
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public void close() {
    if (!shutdownChannelOnClose) return;
    if (closeCalled) return;
    closeCalled = true;
    ((ManagedChannel) channel).shutdownNow();
  }
}
