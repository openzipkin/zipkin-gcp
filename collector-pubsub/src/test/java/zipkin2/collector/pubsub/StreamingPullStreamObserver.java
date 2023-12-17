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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

public class StreamingPullStreamObserver extends AbstractExecutionThreadService implements StreamObserver<StreamingPullRequest> {

    private final BlockingQueue<Span> spanQueue;
    private final ServerCallStreamObserver<StreamingPullResponse> responseObserver;

    public StreamingPullStreamObserver(BlockingQueue<Span> spanQueue, StreamObserver<StreamingPullResponse> responseObserver) {
        this.spanQueue = spanQueue;
        this.responseObserver = (ServerCallStreamObserver<StreamingPullResponse>) responseObserver;
        this.responseObserver.disableAutoInboundFlowControl();

        this.responseObserver.setOnReadyHandler(
                () -> {
                        if(!isRunning()) {
                            startAsync().awaitRunning();
                        }
                        this.responseObserver.request(1);
                });
        this.responseObserver.setOnCancelHandler(this::stopIfRunning);

    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            if(responseObserver.isReady()) {
                Span span = spanQueue.take();

                StreamingPullResponse.Builder builder = StreamingPullResponse.newBuilder();

                byte[] encoded = SpanBytesEncoder.JSON_V2.encodeList(Collections.singletonList(span));
                PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(ByteString.copyFrom(encoded)).build();
                ReceivedMessage receivedMessage = ReceivedMessage.newBuilder().setAckId(UUID.randomUUID().toString()).setMessage(pubsubMessage).build();

                builder.addReceivedMessages(receivedMessage);

                responseObserver.onNext(builder.build());
            } else {
                Thread.sleep(1000l);
            }
        }
    }

    @Override
    public void onNext(StreamingPullRequest streamingPullRequest) {
        if(!isRunning()) {
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
