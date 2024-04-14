/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.pubsub;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;

public class PubSubSender extends BytesMessageSender.Base {

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
      this.topicAdminClient = pubSubSender.topicAdminClient;
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

      if (executorProvider == null) executorProvider = defaultExecutorProvider();

      if (publisher == null) {
        try {
          publisher = Publisher.newBuilder(topic).setExecutorProvider(executorProvider).build();
        } catch (IOException e) {
          throw new PubSubSenderInitializationException(e);
        }
      }

      if (topicAdminClient == null) {
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

  public Builder toBuilder() {
    return new Builder(this);
  }

  final String topic;
  final int messageMaxBytes;
  final Publisher publisher;
  final ExecutorProvider executorProvider;
  final TopicAdminClient topicAdminClient;

  volatile boolean closeCalled;

  PubSubSender(Builder builder) {
    super(builder.encoding);
    topic = builder.topic;
    messageMaxBytes = builder.messageMaxBytes;
    publisher = builder.publisher;
    executorProvider = builder.executorProvider;
    topicAdminClient = builder.topicAdminClient;
  }

  @Override public int messageMaxBytes() {
    return messageMaxBytes;
  }

  @Override public void send(List<byte[]> byteList) throws IOException {
    if (closeCalled) throw new ClosedSenderException();

    byte[] messageBytes = BytesMessageEncoder.forEncoding(encoding()).encode(byteList);
    PubsubMessage message =
        PubsubMessage.newBuilder().setData(ByteString.copyFrom(messageBytes)).build();

    try {
      publisher.publish(message).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException(e.getMessage());
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) throw (RuntimeException) cause;
      if (cause instanceof Error) throw (Error) cause;
      throw new RuntimeException(cause);
    }
  }

  /**
   * Shutdown on Publisher is not async thus moving the synchronized block to another function in
   * order not to block until the shutdown is over.
   */
  @Override public void close() {
    if (!setClosed()) {
      return;
    }
    publisher.shutdown();
  }

  private synchronized boolean setClosed() {
    if (closeCalled) {
      return false;
    } else {
      closeCalled = true;
      return true;
    }
  }

  @Override public final String toString() {
    return "PubSubSender{topic=" + topic + "}";
  }
}
