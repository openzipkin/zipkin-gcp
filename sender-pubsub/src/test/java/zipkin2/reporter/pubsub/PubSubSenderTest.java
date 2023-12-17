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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.stub.GrpcPublisherStub;
import com.google.cloud.pubsub.v1.stub.PublisherStub;
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings;
import com.google.pubsub.v1.GetTopicRequest;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PublisherGrpc;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Topic;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.Span;
import static zipkin2.TestObjects.CLIENT_SPAN;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;

class PubSubSenderTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    PubSubSender sender;

    private PublisherGrpc.PublisherImplBase publisherImplBase;

    @BeforeEach
    void setUp() throws IOException, ExecutionException, InterruptedException {
        publisherImplBase = mock(PublisherGrpc.PublisherImplBase.class);

        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(InProcessServerBuilder
                                     .forName(serverName).directExecutor().addService(publisherImplBase).build().start());

        ExecutorProvider executorProvider = testExecutorProvider();

        ManagedChannel managedChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        TransportChannel transportChannel = GrpcTransportChannel.create(managedChannel);
        TransportChannelProvider transportChannelProvider = FixedTransportChannelProvider.create(transportChannel);

        String topicName = "projects/test-project/topics/my-topic";
        Publisher publisher = Publisher.newBuilder(topicName)
                                       .setExecutorProvider(executorProvider)
                                        .setChannelProvider(transportChannelProvider)
                                       .build();

        PublisherStubSettings publisherStubSettings = PublisherStubSettings.newBuilder()
                                                                           .setTransportChannelProvider(transportChannelProvider)
                                                                           .build();
        PublisherStub publisherStub = GrpcPublisherStub.create(publisherStubSettings);
        TopicAdminClient topicAdminClient = TopicAdminClient.create(publisherStub);

        sender = PubSubSender.newBuilder()
                             .topic(topicName)
                             .publisher(publisher)
                             .topicAdminClient(topicAdminClient)
                             .build();
    }

    private InstantiatingExecutorProvider testExecutorProvider() {
        return InstantiatingExecutorProvider.newBuilder()
                                            .setExecutorThreadCount(5 * Runtime.getRuntime().availableProcessors())
                                            .build();
    }

    @Test
    public void sendsSpans() throws Exception {
        ArgumentCaptor<PublishRequest> requestCaptor =
                ArgumentCaptor.forClass(PublishRequest.class);

        doAnswer(invocationOnMock -> {
            StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
            responseObserver.onNext(PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
            responseObserver.onCompleted();
            return null;
        }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

        send(CLIENT_SPAN, CLIENT_SPAN).execute();

        assertThat(extractSpans(requestCaptor.getValue()))
                .containsExactly(CLIENT_SPAN, CLIENT_SPAN);
    }

    @Test
    public void sendsSpans_PROTO3() throws Exception {
        ArgumentCaptor<PublishRequest> requestCaptor =
                ArgumentCaptor.forClass(PublishRequest.class);

        doAnswer(invocationOnMock -> {
            StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
            responseObserver.onNext(PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
            responseObserver.onCompleted();
            return null;
        }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

        sender = sender.toBuilder().encoding(Encoding.PROTO3).build();

        send(CLIENT_SPAN, CLIENT_SPAN).execute();

        assertThat(extractSpans(requestCaptor.getValue()))
                .containsExactly(CLIENT_SPAN, CLIENT_SPAN);
    }

    @Test
    public void sendsSpans_json_unicode() throws Exception {
        ArgumentCaptor<PublishRequest> requestCaptor =
                ArgumentCaptor.forClass(PublishRequest.class);

        doAnswer(invocationOnMock -> {
            StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
            responseObserver.onNext(PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
            responseObserver.onCompleted();
            return null;
        }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

        Span unicode = CLIENT_SPAN.toBuilder().putTag("error", "\uD83D\uDCA9").build();
        send(unicode).execute();

        assertThat(extractSpans(requestCaptor.getValue())).containsExactly(unicode);
    }

    @Test
    public void checkPasses() throws Exception {
        ArgumentCaptor<GetTopicRequest> captor =
                ArgumentCaptor.forClass(GetTopicRequest.class);

        doAnswer(invocationOnMock -> {
            StreamObserver<Topic> responseObserver = invocationOnMock.getArgument(1);
            responseObserver.onNext(Topic.newBuilder().setName("topic-name").build());
            responseObserver.onCompleted();
            return null;
        }).when(publisherImplBase).getTopic(captor.capture(), any(StreamObserver.class));

        CheckResult result = sender.check();
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void checkFailsWithStreamNotActive() throws Exception {
        ArgumentCaptor<GetTopicRequest> captor =
                ArgumentCaptor.forClass(GetTopicRequest.class);

        doAnswer(invocationOnMock -> {
            StreamObserver<Topic> responseObserver = invocationOnMock.getArgument(1);
            responseObserver.onError(new io.grpc.StatusRuntimeException(Status.NOT_FOUND));
            return null;
        }).when(publisherImplBase).getTopic(captor.capture(), any(StreamObserver.class));

        CheckResult result = sender.check();
        assertThat(result.error()).isInstanceOf(ApiException.class);
    }

    private List<Span> extractSpans(PublishRequest publishRequest) {
        return publishRequest.getMessagesList()
                            .stream()
                            .flatMap(this::extractSpans)
                            .collect(Collectors.toList());
    }

    Stream<Span> extractSpans(PubsubMessage pubsubMessage) {
        byte[] messageBytes = pubsubMessage.getData().toByteArray();

        if (messageBytes[0] == '[') {
            return SpanBytesDecoder.JSON_V2.decodeList(messageBytes).stream();
        }
        return SpanBytesDecoder.PROTO3.decodeList(messageBytes).stream();
    }

    Call<Void> send(zipkin2.Span... spans) {
        SpanBytesEncoder bytesEncoder =
                sender.encoding() == Encoding.JSON ? SpanBytesEncoder.JSON_V2 : SpanBytesEncoder.PROTO3;
        return sender.sendSpans(Stream.of(spans).map(bytesEncoder::encode).collect(toList()));
    }

}
