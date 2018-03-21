/**
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin2.storage.stackdriver.call;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import zipkin2.Callback;

final class CallbackToUnaryClientCallListener<RespT> extends ClientCall.Listener<RespT> {
  private final Callback<RespT> callback;
  /** this differentiates between not yet set and null */
  boolean valueSet; // guarded by this
  RespT value; // guarded by this

  CallbackToUnaryClientCallListener(Callback<RespT> callback) {
    this.callback = callback;
  }

  @Override
  public void onHeaders(Metadata headers) {
  }

  @Override
  public synchronized void onMessage(RespT value) {
    if (valueSet) {
      throw Status.INTERNAL
          .withDescription("More than one value received for unary call")
          .asRuntimeException();
    }
    valueSet = true;
    this.value = value;
  }

  @Override
  public synchronized void onClose(Status status, Metadata trailers) {
    if (status.isOk()) {
      if (!valueSet) {
        callback.onError(
            Status.INTERNAL
                .withDescription("No value received for unary call")
                .asRuntimeException(trailers));
      }
      callback.onSuccess(value);
    } else {
      callback.onError(status.asRuntimeException(trailers));
    }
  }
}
