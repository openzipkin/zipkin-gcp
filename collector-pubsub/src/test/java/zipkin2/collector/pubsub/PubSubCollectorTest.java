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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import zipkin2.Span;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import zipkin2.codec.Encoding;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

public class PubSubCollectorTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private InMemoryStorage store;
    private InMemoryCollectorMetrics metrics;
    private CollectorComponent collector;

    private QueueBasedSubscriberImpl subImplTest = new QueueBasedSubscriberImpl();

    @Before
    public void setup() throws IOException {

        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
                                     .forName(serverName).directExecutor().addService(subImplTest).build().start());
        ExecutorProvider executorProvider = testExecutorProvider();

        ManagedChannel managedChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        TransportChannel transportChannel = GrpcTransportChannel.create(managedChannel);
        TransportChannelProvider transportChannelProvider = FixedTransportChannelProvider.create(transportChannel);


        store = InMemoryStorage.newBuilder().build();
        metrics = new InMemoryCollectorMetrics();

        SubscriberSettings subscriberSettings = new SubscriberSettings();
        subscriberSettings.setChannelProvider(transportChannelProvider);
        subscriberSettings.setExecutorProvider(executorProvider);
        subscriberSettings.setFlowControlSettings(FlowControlSettings.newBuilder().setMaxOutstandingElementCount(1000l).build());


        collector = new PubSubCollector.Builder()
                .subscription("projects/test-project/topics/test-subscription")
                .storage(store)
                .encoding(Encoding.JSON)
                .executorProvider(executorProvider)
                .subscriberSettings(subscriberSettings)
                .metrics(metrics)
                .build()
                .start();
        metrics = metrics.forTransport("pubsub");
    }

    @Test
    public void collectSpans() throws Exception {
        List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
        subImplTest.addSpans(spans);
        assertSpansAccepted(spans);
    }

    @Test
    public void testNow() {
        subImplTest.addSpan(CLIENT_SPAN);
        await().atMost(10, TimeUnit.SECONDS).until(() -> store.acceptedSpanCount() == 1);
    }

    @After
    public void teardown() throws IOException, InterruptedException {
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