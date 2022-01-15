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

import com.google.api.gax.core.ExecutorProvider;

import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import zipkin2.Call;
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
        public Builder publisher(ExecutorProvider executorProvider) {
            if (executorProvider == null) throw new NullPointerException("executorProvider == null");
            this.executorProvider = executorProvider;
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

    volatile boolean closeCalled;

    PubSubSender(Builder builder) {
        this.topic = builder.topic;
        this.messageMaxBytes = builder.messageMaxBytes;
        this.encoding = builder.encoding;
        this.publisher = builder.publisher;
        this.executorProvider = builder.executorProvider;
    }

    @Override
    public CheckResult check() {
        return CheckResult.OK;
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
        publisher.publish(pubsubMessage);
        return null;
        /*

        ByteBuffer message = ByteBuffer.wrap(BytesMessageEncoder.forEncoding(encoding()).encode(list));

        PutRecordRequest request = new PutRecordRequest();
        request.setStreamName(streamName);
        request.setData(message);
        request.setPartitionKey(getPartitionKey());

        return new KinesisCall(request);
        */
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



}
