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

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

final class SpanMessageReceiver implements MessageReceiver {

    final Collector collector;
    final CollectorMetrics metrics;

    public SpanMessageReceiver(Collector collector, CollectorMetrics metrics) {
        this.collector = collector;
        this.metrics = metrics;
    }

    @Override
    public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {
        byte[] serialized = pubsubMessage.getData().toByteArray();
        metrics.incrementMessages();
        metrics.incrementBytes(serialized.length);
        collector.acceptSpans(serialized, new SpanCallback(ackReplyConsumer));
    }

}
