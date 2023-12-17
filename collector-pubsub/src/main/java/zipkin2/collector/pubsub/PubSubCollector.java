/*
 * Copyright 2016-2022 The OpenZipkin Authors
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

import java.io.IOException;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;

import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

public class PubSubCollector extends CollectorComponent {

    public static final class Builder extends CollectorComponent.Builder {

        String subscription;
        Encoding encoding = Encoding.JSON;
        ExecutorProvider executorProvider;
        SubscriptionAdminClient subscriptionAdminClient;
        SubscriberSettings subscriberSettings;

        Collector.Builder delegate = Collector.newBuilder(PubSubCollector.class);
        CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;

        public Builder(PubSubCollector pubSubCollector) {
            this.subscription = pubSubCollector.subscription;
            this.encoding = pubSubCollector.encoding;
            this.executorProvider = pubSubCollector.executorProvider;
        }

        @Override
        public Builder storage(StorageComponent storageComponent) {
            delegate.storage(storageComponent);
            return this;
        }

        @Override
        public Builder metrics(CollectorMetrics metrics) {
            if (metrics == null) throw new NullPointerException("metrics == null");
            delegate.metrics(this.metrics = metrics.forTransport("pubsub"));
            return this;
        }

        @Override
        public Builder sampler(CollectorSampler collectorSampler) {
            delegate.sampler(collectorSampler);
            return this;
        }

        /** PubSub subscription to receive spans. */
        public Builder subscription(String subscription) {
            if (subscription == null) throw new NullPointerException("subscription == null");
            this.subscription = subscription;
            return this;
        }

        /**
         * Use this to change the encoding used in messages. Default is {@linkplain Encoding#JSON}
         *
         * <p>Note: If ultimately sending to Zipkin, version 2.8+ is required to process protobuf.
         */
        public Builder encoding(Encoding encoding) {
            if (encoding == null) throw new NullPointerException("encoding == null");
            this.encoding = encoding;
            return this;
        }

        /** ExecutorProvider for PubSub operations **/
        public Builder executorProvider(ExecutorProvider executorProvider) {
            if (executorProvider == null) throw new NullPointerException("executorProvider == null");
            this.executorProvider = executorProvider;
            return this;
        }

        public Builder subscriptionAdminClient(SubscriptionAdminClient subscriptionAdminClient) {
            if (subscriptionAdminClient == null) throw new NullPointerException("subscriptionAdminClient == null");
            this.subscriptionAdminClient = subscriptionAdminClient;
            return this;
        }

        public Builder subscriberSettings(SubscriberSettings subscriberSettings) {
            if (subscriberSettings == null) throw new NullPointerException("subscriberSettings == null");
            this.subscriberSettings = subscriberSettings;
            return this;
        }

        @Override
        public PubSubCollector build() {
            return new PubSubCollector(this);
        }

        Builder() {}
    }

    final Collector collector;
    final CollectorMetrics metrics;
    final String subscription;
    final Encoding encoding;
    Subscriber subscriber;
    final ExecutorProvider executorProvider;
    final SubscriptionAdminClient subscriptionAdminClient;
    final SubscriberSettings subscriberSettings;

    PubSubCollector(Builder builder) {
        this.collector = builder.delegate.build();
        this.metrics = builder.metrics;
        this.subscription = builder.subscription;
        this.encoding = builder.encoding;
        this.executorProvider = builder.executorProvider;
        this.subscriptionAdminClient = builder.subscriptionAdminClient;
        this.subscriberSettings = builder.subscriberSettings;
    }

    @Override
    public CollectorComponent start() {
        Subscriber.Builder builder = Subscriber.newBuilder(subscription, new SpanMessageReceiver(collector, metrics));
        subscriber = applyConfigurations(builder).build();
        subscriber.startAsync().awaitRunning();
        return this;
    }

    private Subscriber.Builder applyConfigurations(Subscriber.Builder builder) {
        if(subscriberSettings==null) {
            return builder;
        }

        subscriberSettings.getChannelProvider().ifPresent(builder::setChannelProvider);
        subscriberSettings.getHeaderProvider().ifPresent(builder::setHeaderProvider);
        subscriberSettings.getFlowControlSettings().ifPresent(builder::setFlowControlSettings);
        builder.setUseLegacyFlowControl(subscriberSettings.isUseLegacyFlowControl());
        subscriberSettings.getMaxAckExtensionPeriod().ifPresent(builder::setMaxAckExtensionPeriod);
        subscriberSettings.getMaxDurationPerAckExtension().ifPresent(builder::setMaxDurationPerAckExtension);
        subscriberSettings.getExecutorProvider().ifPresent(builder::setExecutorProvider);
        subscriberSettings.getCredentialsProvider().ifPresent(builder::setCredentialsProvider);
        subscriberSettings.getSystemExecutorProvider().ifPresent(builder::setSystemExecutorProvider);
        builder.setParallelPullCount(subscriberSettings.getParallelPullCount());
        subscriberSettings.getEndpoint().ifPresent(builder::setEndpoint);

        return builder;
    }

    @Override
    public CheckResult check() {
        try {
            subscriptionAdminClient.getSubscription(subscription);
            return CheckResult.OK;
        } catch (ApiException e) {
            return CheckResult.failed(e);
        }
    }

    @Override
    public void close() throws IOException {
        subscriber.stopAsync().awaitTerminated();
    }

}
