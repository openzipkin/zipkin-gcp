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
package zipkin2.reporter.stackdriver;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Blocks until {@link #onClose}. */
// ported from zipkin2.reporter.internal.AwaitableCallback
final class AwaitableUnaryClientCallListener<V> extends ClientCall.Listener<V> {
  final CountDownLatch countDown = new CountDownLatch(1);
  /** this differentiates between not yet set and null */
  boolean resultSet; // guarded by this

  Object result; // guarded by this

  long serverTimeoutMs; // how long to wait for server response in milliseconds

  AwaitableUnaryClientCallListener(long serverTimeoutMs) {
    if (serverTimeoutMs <= 0) {
      throw new IllegalArgumentException("Server response timeout must be greater than 0");
    }
    this.serverTimeoutMs = serverTimeoutMs;
  }

  /**
   * Blocks until {@link #onClose}. Throws if no value was received, multiple
   * values were received, there was a status error, or waited longer than {@link #serverTimeoutMs}.
   */
  V await() throws IOException {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          if (!countDown.await(serverTimeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                "timeout waiting for onClose. timeoutMs=" + serverTimeoutMs
                    + ", resultSet=" + resultSet);
          }
          Object result;
          synchronized (this) {
            if (!resultSet) continue;
            result = this.result;
          }
          if (result instanceof Throwable) {
            if (result instanceof Error) throw (Error) result;
            if (result instanceof IOException) throw (IOException) result;
            if (result instanceof RuntimeException) throw (RuntimeException) result;
            // Don't set interrupted status when the callback received InterruptedException
            throw new RuntimeException((Throwable) result);
          }
          return (V) result;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void onHeaders(Metadata headers) {
  }

  @Override
  public synchronized void onMessage(V value) {
    if (resultSet) {
      throw Status.INTERNAL
          .withDescription("More than one value received for unary call")
          .asRuntimeException();
    }
    result = value;
    resultSet = true;
  }

  @Override
  public synchronized void onClose(Status status, Metadata trailers) {
    if (status.isOk()) {
      if (!resultSet) {
        result =
            Status.INTERNAL
                .withDescription("No value received for unary call")
                .asRuntimeException(trailers);
      }
    } else {
      result = status.asRuntimeException(trailers);
    }
    resultSet = true;
    countDown.countDown();
  }
}
