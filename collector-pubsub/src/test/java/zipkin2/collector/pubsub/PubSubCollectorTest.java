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
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static zipkin2.TestObjects.CLIENT_SPAN;
import zipkin2.codec.Encoding;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

public class PubSubCollectorTest {

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


        collector = new PubSubCollector.Builder()
                .subscription("projects/test-project/topics/test-subscription")
                .storage(store)
                .encoding(Encoding.JSON)
                .executorProvider(executorProvider)
                .subscriberSettings(subscriberSettings)
                .build();
    }

    @After
    public void teardown() throws IOException, InterruptedException {
        store.close();
        collector.close();
    }

    @Test
    public void testNow() {
        collector.start();
        subImplTest.addSpan(CLIENT_SPAN);
        await().atMost(10, TimeUnit.SECONDS).until(() -> store.acceptedSpanCount() == 1);
    }

    private InstantiatingExecutorProvider testExecutorProvider() {
        return InstantiatingExecutorProvider.newBuilder()
                                            .setExecutorThreadCount(5 * Runtime.getRuntime().availableProcessors())
                                            .build();
    }

}