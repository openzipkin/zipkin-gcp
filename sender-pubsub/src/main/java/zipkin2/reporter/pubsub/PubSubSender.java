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
package zipkin2.reporter.pubsub;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.ExecutorProvider;

import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Sender;

public class PubSubSender extends Sender {

    public static PubSubSender create(String topic) {
        return newBuilder().topic(topic).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        String topic;
        int messageMaxBytes = 10 * 1024 * 1024; // 10MB PubSub limit.
        Encoding encoding = Encoding.JSON;
        Publisher publisher;
        ExecutorProvider executorProvider;
        TopicAdminClient topicAdminClient;

        Builder(PubSubSender pubSubSender) {
            this.topic = pubSubSender.topic;
            this.encoding = pubSubSender.encoding;
            this.publisher = pubSubSender.publisher;
            this.executorProvider = pubSubSender.executorProvider;
        }

        /** PubSub topic to send spans. */
        public Builder topic(String topic) {
            if (topic == null) throw new NullPointerException("topic == null");
            this.topic = topic;
            return this;
        }

        /** Maximum size of a message. PubSub max message size is 10MB */
        public Builder messageMaxBytes(int messageMaxBytes) {
            this.messageMaxBytes = messageMaxBytes;
            return this;
        }

        public Builder publisher(Publisher publisher) {
            if (publisher == null) throw new NullPointerException("publisher == null");
            this.publisher = publisher;
            return this;
        }

        /** ExecutorProvider for PubSub operations **/
        public Builder executorProvider(ExecutorProvider executorProvider) {
            if (executorProvider == null) throw new NullPointerException("executorProvider == null");
            this.executorProvider = executorProvider;
            return this;
        }

        public Builder topicAdminClient(TopicAdminClient topicAdminClient) {
            if (topicAdminClient == null) throw new NullPointerException("topicAdminClient == null");
            this.topicAdminClient = topicAdminClient;
            return this;
        }

        public PubSubSender build() {
            if (topic == null) throw new NullPointerException("topic == null");
            try {
                Publisher.newBuilder("dsdssd").setExecutorProvider(null).build();
            } catch (IOException e) {
                throw new PubSubSenderInitializationException(e);
            }

            if (executorProvider == null) executorProvider = defaultExecutorProvider(); ;

            if(publisher == null) {
                try {
                    publisher = Publisher.newBuilder(topic).setExecutorProvider(executorProvider).build();
                } catch (IOException e) {
                    throw new PubSubSenderInitializationException(e);
                }
            }

            if(topicAdminClient == null) {
                try {
                    topicAdminClient = TopicAdminClient.create();
                } catch (IOException e) {
                    throw new PubSubSenderInitializationException(e);
                }
            }

            return new PubSubSender(this);
        }

        private InstantiatingExecutorProvider defaultExecutorProvider() {
            return InstantiatingExecutorProvider.newBuilder()
                                                .setExecutorThreadCount(5 * Runtime.getRuntime().availableProcessors())
                                                .build();
        }

        Builder() {
        }
    }

    final String topic;
    final int messageMaxBytes;
    final Encoding encoding;
    final Publisher publisher;
    final ExecutorProvider executorProvider;
    final TopicAdminClient topicAdminClient;

    volatile boolean closeCalled;

    PubSubSender(Builder builder) {
        this.topic = builder.topic;
        this.messageMaxBytes = builder.messageMaxBytes;
        this.encoding = builder.encoding;
        this.publisher = builder.publisher;
        this.executorProvider = builder.executorProvider;
        this.topicAdminClient = builder.topicAdminClient;
    }

    /**
     * If no permissions given sent back ok, f permissions and topic exist ok, if topic does not exist error
     * @return
     */
    @Override
    public CheckResult check() {
        Topic topic = topicAdminClient.getTopic(TopicName.parse(this.topic));
        return CheckResult.OK;
        //   return CheckResult.failed(e);
    }

    @Override public Encoding encoding() {
        return encoding;
    }

    @Override
    public int messageMaxBytes() {
        return messageMaxBytes;
    }

    @Override
    public int messageSizeInBytes(List<byte[]> bytes) {
        return encoding().listSizeInBytes(bytes);
    }

    @Override
    public Call<Void> sendSpans(List<byte[]> byteList) {
        if (closeCalled) throw new IllegalStateException("closed");

        byte[] messageBytes = BytesMessageEncoder.forEncoding(encoding()).encode(byteList);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(ByteString.copyFrom(messageBytes)).build();

        return new PubSubCall(pubsubMessage);
    }

    /**
     * Shutdown on Publisher is not async thus moving the synchronized block to another function in order not to block until the shutdown is over
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if(!setClosed()) {
            return;
        }
        publisher.shutdown();
    }

    private synchronized boolean setClosed() {
        if(closeCalled) {
            return false;
        } else {
            closeCalled = true;
            return true;
        }
    }

    @Override public final String toString() {
        return "PubSubSender{topic=" + topic+ "}";
    }

    class PubSubCall extends Call.Base<Void> {
        private final PubsubMessage message;
        volatile ApiFuture<String> future;

        public PubSubCall(PubsubMessage message) {
            this.message = message;
        }

        @Override
        protected Void doExecute() throws IOException {
            try {
                publisher.publish(message).get();
            } catch (InterruptedException| ExecutionException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void doEnqueue(Callback<Void> callback) {
            ApiFuture<String> future = publisher.publish(message);
            ApiFutures.addCallback(future, new ApiFutureCallbackAdapter(callback), executorProvider.getExecutor());
            if (future.isCancelled()) throw new IllegalStateException("cancelled sending spans");
        }

        @Override
        protected void doCancel() {
            Future<String> maybeFuture = future;
            if (maybeFuture != null) maybeFuture.cancel(true);
        }

        @Override
        protected boolean doIsCanceled() {
            Future<String> maybeFuture = future;
            return maybeFuture != null && maybeFuture.isCancelled();
        }

        @Override
        public Call<Void> clone() {
            PubsubMessage clone = PubsubMessage.newBuilder(message).build();
            return new PubSubCall(clone);
        }
    }

    static final class ApiFutureCallbackAdapter implements ApiFutureCallback<String> {

        final Callback<Void> callback;

        public ApiFutureCallbackAdapter(Callback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onFailure(Throwable t) {
            callback.onError(t);
        }

        @Override
        public void onSuccess(String result) {
            callback.onSuccess(null);
        }
    }

}
