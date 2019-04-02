/*
 * Copyright 2016-2019 The OpenZipkin Authors
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

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.protobuf.Empty;
import io.grpc.StatusRuntimeException;
import org.junit.Before;
import org.junit.Test;

public class UnaryClientCallTest extends BaseUnaryClientCallTest {

  @Before
  public void setUp() {
    server.getServiceRegistry().addService(traceService);
    call = new BatchWriteSpansCall(server.getChannel(), BatchWriteSpansRequest.newBuilder().build());
  }

  @Test
  public void execute_success() throws Throwable {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    call.execute();

    verifyPatchRequestSent();
  }

  @Test
  public void enqueue_success() throws Throwable {
    onClientCall(
        observer -> {
          observer.onNext(Empty.getDefaultInstance());
          observer.onCompleted();
        });

    awaitCallbackResult();

    verifyPatchRequestSent();
  }

  @Test(expected = StatusRuntimeException.class)
  public void accept_execute_serverError() throws Throwable {
    onClientCall(observer -> observer.onError(new IllegalStateException()));

    call.execute();
  }

  @Test(expected = StatusRuntimeException.class)
  public void accept_enqueue_serverError() throws Throwable {
    onClientCall(observer -> observer.onError(new IllegalStateException()));

    awaitCallbackResult();
  }

}
