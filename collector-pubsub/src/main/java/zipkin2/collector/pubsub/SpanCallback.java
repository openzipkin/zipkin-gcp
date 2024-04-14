/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pubsub;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import zipkin2.Callback;

final class SpanCallback implements Callback<Void> {

  private final AckReplyConsumer ackReplyConsumer;

  public SpanCallback(AckReplyConsumer ackReplyConsumer) {
    this.ackReplyConsumer = ackReplyConsumer;
  }

  @Override
  public void onSuccess(Void value) {
    ackReplyConsumer.ack();
  }

  @Override
  public void onError(Throwable throwable) {
    ackReplyConsumer.nack();
  }
}
