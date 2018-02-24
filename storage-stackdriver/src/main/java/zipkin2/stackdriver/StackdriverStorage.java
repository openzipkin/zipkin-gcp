/**
 * Copyright 2016-2017 The OpenZipkin Authors
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

import com.google.auto.value.AutoValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import zipkin2.CheckResult;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

/**
 * StackdriverStorage is a StorageComponent that consumes spans using the Stackdriver
 * TraceSpanConsumer.
 *
 * <p>No SpanStore methods are implemented because read operations are not yet supported.
 */
@AutoValue
public abstract class StackdriverStorage extends StorageComponent {
  public static Builder newBuilder() {
    return new AutoValue_StackdriverStorage.Builder()
        .apiHost("cloudtrace.googleapis.com");
  }

  public static Builder newBuilder(ManagedChannel channel) { // visible for testing
    return new AutoValue_StackdriverStorage.Builder()
        .shutdownChannelOnClose(false)
        .channel(channel);
  }

  @AutoValue.Builder
  public static abstract class Builder extends StorageComponent.Builder {
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

    public final Builder apiHost(String apiHost) {
      shutdownChannelOnClose(true);
      return channel(ManagedChannelBuilder.forTarget(apiHost).build());
    }

    public abstract Builder projectId(String projectId);

    public abstract Builder callOptions(CallOptions callOptions);

    abstract Builder channel(ManagedChannel channel);

    abstract Builder shutdownChannelOnClose(boolean shutdownChannelOnClose);

    @Override public abstract StackdriverStorage build();

    Builder() {
    }
  }

  abstract String projectId();

  abstract ManagedChannel channel();

  abstract CallOptions callOptions();

  abstract boolean shutdownChannelOnClose();

  @Override public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public SpanConsumer spanConsumer() {
    return new StackdriverSpanConsumer(channel(), projectId(), callOptions());
  }

  @Override
  public CheckResult check() {
    return CheckResult.OK;
  }

  @Override public void close() {
    if (!shutdownChannelOnClose()) return;
    channel().shutdownNow();
  }

  StackdriverStorage() {
  }
}
