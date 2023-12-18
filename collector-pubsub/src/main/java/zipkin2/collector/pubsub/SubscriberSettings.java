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
package zipkin2.collector.pubsub;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import java.util.Optional;
import org.threeten.bp.Duration;

public class SubscriberSettings {

  private Optional<TransportChannelProvider> channelProvider = Optional.empty();
  private Optional<HeaderProvider> headerProvider = Optional.empty();
  private Optional<FlowControlSettings> flowControlSettings = Optional.empty();
  private boolean useLegacyFlowControl = false;
  private Optional<Duration> maxAckExtensionPeriod = Optional.empty();
  private Optional<Duration> maxDurationPerAckExtension = Optional.empty();
  private Optional<ExecutorProvider> executorProvider = Optional.empty();
  private Optional<CredentialsProvider> credentialsProvider = Optional.empty();
  private Optional<ExecutorProvider> systemExecutorProvider = Optional.empty();
  private int parallelPullCount = 1;
  private Optional<String> endpoint = Optional.empty();

  public Optional<TransportChannelProvider> getChannelProvider() {
    return channelProvider;
  }

  public void setChannelProvider(TransportChannelProvider channelProvider) {
    this.channelProvider = Optional.of(channelProvider);
  }

  public Optional<HeaderProvider> getHeaderProvider() {
    return headerProvider;
  }

  public void setHeaderProvider(HeaderProvider headerProvider) {
    this.headerProvider = Optional.of(headerProvider);
  }

  public Optional<FlowControlSettings> getFlowControlSettings() {
    return flowControlSettings;
  }

  public void setFlowControlSettings(FlowControlSettings flowControlSettings) {
    this.flowControlSettings = Optional.of(flowControlSettings);
  }

  public boolean isUseLegacyFlowControl() {
    return useLegacyFlowControl;
  }

  public void setUseLegacyFlowControl(boolean useLegacyFlowControl) {
    this.useLegacyFlowControl = useLegacyFlowControl;
  }

  public Optional<Duration> getMaxAckExtensionPeriod() {
    return maxAckExtensionPeriod;
  }

  public void setMaxAckExtensionPeriod(Duration maxAckExtensionPeriod) {
    this.maxAckExtensionPeriod = Optional.of(maxAckExtensionPeriod);
  }

  public Optional<Duration> getMaxDurationPerAckExtension() {
    return maxDurationPerAckExtension;
  }

  public void setMaxDurationPerAckExtension(Duration maxDurationPerAckExtension) {
    this.maxDurationPerAckExtension = Optional.of(maxDurationPerAckExtension);
  }

  public Optional<ExecutorProvider> getExecutorProvider() {
    return executorProvider;
  }

  public void setExecutorProvider(ExecutorProvider executorProvider) {
    this.executorProvider = Optional.of(executorProvider);
  }

  public Optional<CredentialsProvider> getCredentialsProvider() {
    return credentialsProvider;
  }

  public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
    this.credentialsProvider = Optional.of(credentialsProvider);
  }

  public Optional<ExecutorProvider> getSystemExecutorProvider() {
    return systemExecutorProvider;
  }

  public void setSystemExecutorProvider(ExecutorProvider systemExecutorProvider) {
    this.systemExecutorProvider = Optional.of(systemExecutorProvider);
  }

  public int getParallelPullCount() {
    return parallelPullCount;
  }

  public void setParallelPullCount(int parallelPullCount) {
    this.parallelPullCount = parallelPullCount;
  }

  public Optional<String> getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = Optional.of(endpoint);
  }
}
