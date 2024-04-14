/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pubsub;

import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;
import com.google.pubsub.v1.SubscriberGrpc;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import zipkin2.Span;

public class QueueBasedSubscriberImpl extends SubscriberGrpc.SubscriberImplBase {

  private final BlockingQueue<Span> spanQueue = new LinkedBlockingDeque<>();

  @Override
  public StreamObserver<StreamingPullRequest> streamingPull(
      StreamObserver<StreamingPullResponse> responseObserver) {
    return new StreamingPullStreamObserver(spanQueue, responseObserver);
  }

  public void addSpans(List<Span> spans) {
    spans.forEach(this::addSpan);
  }

  public void addSpan(Span span) {
    spanQueue.add(span);
  }

  @Override
  public void acknowledge(AcknowledgeRequest request, StreamObserver<Empty> responseObserver) {
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void modifyAckDeadline(ModifyAckDeadlineRequest request,
      StreamObserver<Empty> responseObserver) {
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
