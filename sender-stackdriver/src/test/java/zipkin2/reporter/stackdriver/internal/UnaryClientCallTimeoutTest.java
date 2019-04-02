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
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executors;

public class UnaryClientCallTimeoutTest extends BaseUnaryClientCallTest {
  private static final long SERVER_TIMEOUT_MS = 50;

  @Before
  public void setUp() {
    server.getServiceRegistry().addService(traceService);
    call = new BatchWriteSpansCall(server.getChannel(), BatchWriteSpansRequest.newBuilder().build(), SERVER_TIMEOUT_MS);
  }

  @Test(expected = IllegalStateException.class)
  public void execute_timeout() throws Throwable {
    onClientCall(
        observer ->
            Executors.newSingleThreadExecutor().submit(() ->
            {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {}
              observer.onCompleted();
            }));

    call.execute();
  }

}
