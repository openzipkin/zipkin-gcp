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
package zipkin2.reporter.stackdriver.internal;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import zipkin2.reporter.Call;
import zipkin2.reporter.Callback;

public abstract class UnaryClientCall<ReqT, RespT> extends Call.Base<RespT> {
  public static final int DEFAULT_SERVER_TIMEOUT_MS = 5000;
  final ClientCall<ReqT, RespT> call;
  final ReqT request;
  final long serverTimeoutMs;

  protected UnaryClientCall(
      Channel channel,
      MethodDescriptor<ReqT, RespT> descriptor,
      CallOptions callOptions,
      ReqT request,
      long serverTimeoutMs) {
    this.call = channel.newCall(descriptor, callOptions);
    this.request = request;
    this.serverTimeoutMs = serverTimeoutMs;
  }

  protected final ReqT request() {
    return request;
  }

  @Override
  protected final RespT doExecute() throws IOException {
    AwaitableUnaryClientCallListener<RespT> listener =
        new AwaitableUnaryClientCallListener<>(this.serverTimeoutMs);
    beginUnaryCall(listener);
    return listener.await();
  }

  @Override
  protected final void doEnqueue(Callback<RespT> callback) {
    ClientCall.Listener<RespT> listener = new CallbackToUnaryClientCallListener<>(callback);
    try {
      beginUnaryCall(listener);
    } catch (RuntimeException | Error t) {
      callback.onError(t);
      throw t;
    }
  }

  void beginUnaryCall(ClientCall.Listener<RespT> listener) {
    try {
      call.start(listener, new Metadata());
      call.request(1);
      call.sendMessage(request);
      call.halfClose();
    } catch (RuntimeException | Error t) {
      call.cancel(null, t);
      throw t;
    }
  }

  @Override
  protected final void doCancel() {
    call.cancel(null, null);
  }
}
