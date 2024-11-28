/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pubsub;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.pubsub.v1.SubscriptionName;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import zipkin2.Span;
import zipkin2.codec.Encoding;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

@ExtendWith(GrpcCleanupExtension.class)
class PubSubCollectorTest {
  private InMemoryStorage store;
  private InMemoryCollectorMetrics metrics;
  private CollectorComponent collector;

  private QueueBasedSubscriberImpl subImplTest = new QueueBasedSubscriberImpl();

  @BeforeEach void initCollector(Resources resources) throws IOException {
    String serverName = InProcessServerBuilder.generateName();

    Server server = InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(subImplTest)
        .build().start();
    resources.register(server, Duration.ofSeconds(10)); // shutdown deadline

    ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    resources.register(channel, Duration.ofSeconds(10));// close deadline

    TransportChannel transportChannel = GrpcTransportChannel.create(channel);
    TransportChannelProvider transportChannelProvider =
        FixedTransportChannelProvider.create(transportChannel);

    ExecutorProvider executorProvider = testExecutorProvider();

    store = InMemoryStorage.newBuilder().build();
    metrics = new InMemoryCollectorMetrics();

    SubscriberSettings subscriberSettings = new SubscriberSettings();
    subscriberSettings.setChannelProvider(transportChannelProvider);
    subscriberSettings.setExecutorProvider(executorProvider);
    subscriberSettings.setCredentialsProvider(new NoCredentialsProvider());
    subscriberSettings.setFlowControlSettings(
        FlowControlSettings.newBuilder().setMaxOutstandingElementCount(1000L).build());

    collector = new PubSubCollector.Builder()
        .subscription(SubscriptionName.format("test-project", "test-subscription"))
        .storage(store)
        .encoding(Encoding.JSON)
        .executorProvider(executorProvider)
        .subscriberSettings(subscriberSettings)
        .metrics(metrics)
        .build()
        .start();
    metrics = metrics.forTransport("pubsub");
  }

  @Test void collectSpans() throws Exception {
    List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    subImplTest.addSpans(spans);
    assertSpansAccepted(spans);
  }

  @Test void testNow() {
    subImplTest.addSpan(CLIENT_SPAN);
    await().atMost(10, TimeUnit.SECONDS).until(() -> store.acceptedSpanCount() == 1);
  }

  @AfterEach void teardown() throws Exception {
    store.close();
    collector.close();
  }

  private InstantiatingExecutorProvider testExecutorProvider() {
    return InstantiatingExecutorProvider.newBuilder()
        .setExecutorThreadCount(5 * Runtime.getRuntime().availableProcessors())
        .build();
  }

  void assertSpansAccepted(List<Span> spans) throws Exception {
    await().atMost(20, TimeUnit.SECONDS).until(() -> store.acceptedSpanCount() == 3);

    List<Span> someSpans = store.spanStore().getTrace(spans.get(0).traceId()).execute();

    assertThat(metrics.messages()).as("check accept metrics.").isPositive();
    assertThat(metrics.bytes()).as("check bytes metrics.").isPositive();
    assertThat(metrics.messagesDropped()).as("check dropped metrics.").isEqualTo(0);
    assertThat(someSpans).as("recorded spans should not be null").isNotNull();
    assertThat(spans).as("some spans have been recorded").containsAll(someSpans);
  }
}