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
import java.util.List;

import com.google.api.gax.core.ExecutorProvider;

import com.google.cloud.pubsub.v1.Publisher;
import zipkin2.Call;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

public class PubSubSender extends Sender {

    public static PubSubSender create(String topic) {
        return null;
    }

    public static final class Builder {
        String topic;
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

        public Builder publisher(Publisher publisher) {
            if (publisher == null) throw new NullPointerException("publisher == null");
            this.publisher = publisher;
            return this;
        }

        public Builder publisher(ExecutorProvider executorProvider) {
            if (executorProvider == null) throw new NullPointerException("executorProvider == null");
            this.executorProvider = executorProvider;
            return this;
        }

    }

    @Override public Encoding encoding() {
        return null;
    }

    @Override public int messageMaxBytes() {
        return 0;
    }

    @Override public int messageSizeInBytes(List<byte[]> list) {
        return 0;
    }

    @Override public Call<Void> sendSpans(List<byte[]> list) {
        return null;
    }

    final String topic  = "saddsa";
    final Encoding encoding = Encoding.JSON;
    final Publisher publisher = null;
    final ExecutorProvider executorProvider = null;


    public PubSubSender() throws IOException {
        Publisher publisher = Publisher.newBuilder(topic).setExecutorProvider(executorProvider)
                .build();


        publisher.publish(null).addListener(()-> {}, executorProvider.getExecutor());
        //publisherStub = new Pu
    }
}
