/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
