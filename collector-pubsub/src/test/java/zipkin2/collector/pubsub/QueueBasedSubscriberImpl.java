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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;
import com.google.pubsub.v1.SubscriberGrpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

public class QueueBasedSubscriberImpl extends SubscriberGrpc.SubscriberImplBase {

    private final BlockingQueue<Span> spanQueue = new LinkedBlockingDeque<>();

    @Override
    public StreamObserver<StreamingPullRequest> streamingPull(StreamObserver<StreamingPullResponse> responseObserver) {
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
    public void modifyAckDeadline(ModifyAckDeadlineRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

}
