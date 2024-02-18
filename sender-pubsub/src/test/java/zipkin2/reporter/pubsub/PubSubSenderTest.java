/*
 * Copyright 2016-2024 The OpenZipkin Authors
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

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
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
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PublisherGrpc;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Topic;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.SpanBytesEncoder;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static zipkin2.TestObjects.CLIENT_SPAN;

@ExtendWith({MockitoExtension.class, GrpcCleanupExtension.class})
class PubSubSenderTest {
  PubSubSender sender;
  @Spy PublisherGrpc.PublisherImplBase publisherImplBase;

  @BeforeEach void initSender(Resources resources) throws IOException {
    String serverName = InProcessServerBuilder.generateName();

    Server server = InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(publisherImplBase)
        .build().start();
    resources.register(server, Duration.ofSeconds(10)); // shutdown deadline

    ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    resources.register(channel, Duration.ofSeconds(10));// close deadline

    TransportChannel transportChannel = GrpcTransportChannel.create(channel);
    TransportChannelProvider transportChannelProvider =
        FixedTransportChannelProvider.create(transportChannel);

    ExecutorProvider executorProvider = testExecutorProvider();

    String topicName = "projects/test-project/topics/my-topic";
    Publisher publisher = Publisher.newBuilder(topicName)
        .setExecutorProvider(executorProvider)
        .setChannelProvider(transportChannelProvider)
        .setCredentialsProvider(new NoCredentialsProvider())
        .build();

    PublisherStubSettings publisherStubSettings = PublisherStubSettings.newBuilder()
        .setTransportChannelProvider(transportChannelProvider)
        .setCredentialsProvider(new NoCredentialsProvider())
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

  @Test void send() throws Exception {
    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

    doAnswer(invocationOnMock -> {
      StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
      responseObserver.onNext(
          PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
      responseObserver.onCompleted();
      return null;
    }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

    sendSpans(CLIENT_SPAN, CLIENT_SPAN);

    assertThat(extractSpans(requestCaptor.getValue()))
        .containsExactly(CLIENT_SPAN, CLIENT_SPAN);
  }

  @Test void send_empty() throws Exception {
    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

    doAnswer(invocationOnMock -> {
      StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
      responseObserver.onNext(
          PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
      responseObserver.onCompleted();
      return null;
    }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

    sendSpans();

    assertThat(extractSpans(requestCaptor.getValue()))
        .isEmpty();
  }

  @Test void send_PROTO3() throws Exception {
    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

    doAnswer(invocationOnMock -> {
      StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
      responseObserver.onNext(
          PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
      responseObserver.onCompleted();
      return null;
    }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

    sender = sender.toBuilder().encoding(Encoding.PROTO3).build();

    sendSpans(CLIENT_SPAN, CLIENT_SPAN);

    assertThat(extractSpans(requestCaptor.getValue()))
        .containsExactly(CLIENT_SPAN, CLIENT_SPAN);
  }

  @Test void send_json_unicode() throws Exception {
    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

    doAnswer(invocationOnMock -> {
      StreamObserver<PublishResponse> responseObserver = invocationOnMock.getArgument(1);
      responseObserver.onNext(
          PublishResponse.newBuilder().addMessageIds(UUID.randomUUID().toString()).build());
      responseObserver.onCompleted();
      return null;
    }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

    Span unicode = CLIENT_SPAN.toBuilder().putTag("error", "\uD83D\uDCA9").build();
    sendSpans(unicode);

    assertThat(extractSpans(requestCaptor.getValue())).containsExactly(unicode);
  }

  @Test void sendFailsWithStreamNotActive() {
    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

    doAnswer(invocationOnMock -> {
      StreamObserver<Topic> responseObserver = invocationOnMock.getArgument(1);
      responseObserver.onError(new io.grpc.StatusRuntimeException(Status.NOT_FOUND));
      return null;
    }).when(publisherImplBase).publish(requestCaptor.capture(), any(StreamObserver.class));

    assertThatThrownBy(this::sendSpans)
        .isInstanceOf(ApiException.class);
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

  void sendSpans(zipkin2.Span... spans) throws Exception {
    SpanBytesEncoder bytesEncoder =
        sender.encoding() == Encoding.JSON ? SpanBytesEncoder.JSON_V2 : SpanBytesEncoder.PROTO3;
    sender.send(Stream.of(spans).map(bytesEncoder::encode).collect(toList()));
  }
}
