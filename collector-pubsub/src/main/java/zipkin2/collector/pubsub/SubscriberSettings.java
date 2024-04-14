/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
