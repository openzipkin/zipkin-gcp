/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pubsub;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

public class StreamingPullStreamObserver extends AbstractExecutionThreadService
    implements StreamObserver<StreamingPullRequest> {

  private final BlockingQueue<Span> spanQueue;
  private final ServerCallStreamObserver<StreamingPullResponse> responseObserver;

  public StreamingPullStreamObserver(BlockingQueue<Span> spanQueue,
      StreamObserver<StreamingPullResponse> responseObserver) {
    this.spanQueue = spanQueue;
    this.responseObserver = (ServerCallStreamObserver<StreamingPullResponse>) responseObserver;
    this.responseObserver.disableAutoInboundFlowControl();

    this.responseObserver.setOnReadyHandler(
        () -> {
          if (!isRunning()) {
            startAsync().awaitRunning();
          }
          this.responseObserver.request(1);
        });
    this.responseObserver.setOnCancelHandler(this::stopIfRunning);
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      if (responseObserver.isReady()) {
        Span span = spanQueue.take();

        StreamingPullResponse.Builder builder = StreamingPullResponse.newBuilder();

        byte[] encoded = SpanBytesEncoder.JSON_V2.encodeList(Collections.singletonList(span));
        PubsubMessage pubsubMessage =
            PubsubMessage.newBuilder().setData(ByteString.copyFrom(encoded)).build();
        ReceivedMessage receivedMessage = ReceivedMessage.newBuilder()
            .setAckId(UUID.randomUUID().toString())
            .setMessage(pubsubMessage)
            .build();

        builder.addReceivedMessages(receivedMessage);

        responseObserver.onNext(builder.build());
      } else {
        Thread.sleep(1000l);
      }
    }
  }

  @Override
  public void onNext(StreamingPullRequest streamingPullRequest) {
    if (!isRunning()) {
      startAsync().awaitRunning();
    }
    responseObserver.request(1);
  }

  @Override
  public void onError(Throwable throwable) {
    if (!Status.fromThrowable(throwable).getCode().equals(Status.CANCELLED.getCode())) {
      stopIfRunning();
    }
  }

  @Override
  public void onCompleted() {
    stopIfRunning();
    responseObserver.onCompleted();
  }

  private void stopIfRunning() {
    if (isRunning()) {
      stopAsync();
    }
  }
}
