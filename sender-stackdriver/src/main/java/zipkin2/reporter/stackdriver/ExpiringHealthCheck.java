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

package zipkin2.reporter.stackdriver;

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.protobuf.ByteString;
import java.util.function.Function;
import zipkin2.CheckResult;
import zipkin2.reporter.stackdriver.StackdriverSender.BatchWriteSpansCall;

/**
 * Stackdriver Trace Health check that throttles server calls to once per minute.
 * Capable of accepting recent status to avoid making unnecessary calls.
 */
public class ExpiringHealthCheck {

  // 1 minute default
  private final static int DEFAULT_EXPIRATION_PERIOD_MS = 60000;

  private final ByteString projectName;

  private final Function<BatchWriteSpansRequest, BatchWriteSpansCall> serviceCall;

  private CheckResult lastCheckResult;

  private long expirationTimestamp;

  public ExpiringHealthCheck(ByteString projectName, Function<BatchWriteSpansRequest,StackdriverSender.BatchWriteSpansCall> serviceCall) {
    this.projectName = projectName;
    this.serviceCall = serviceCall;
  }

  /**
   * Tests Stackdriver Trace by sending a malformed request.
   * @return successful check result if input validation error is detected; failed check with the
   * reported exception otherwise.
   */
  public CheckResult check() {
    if (System.currentTimeMillis() > expirationTimestamp) {
      updateCheckResult(sendTestSpan());
    }
    return lastCheckResult;
  }

  /**
   * Accepts an external check result, resetting expiration time.
   * Package private visibility to keep functionality within the tooling.
   */
  void setCheckResult(CheckResult externalResult) {
    updateCheckResult(externalResult);
  }

  private void updateCheckResult(CheckResult newResult) {
    lastCheckResult = newResult;
    expirationTimestamp = System.currentTimeMillis() + DEFAULT_EXPIRATION_PERIOD_MS;
  }

  private CheckResult sendTestSpan() {
      long currentTimeSeconds = System.currentTimeMillis() / 1000;

      Span testSpan = Span.newBuilder().build();

      BatchWriteSpansRequest request = BatchWriteSpansRequest.newBuilder()
          .setNameBytes(projectName)
          .addSpans(testSpan)
          .build();

      try {
        serviceCall.apply(request).execute();
      } catch (Exception e) {
        if (e.getMessage().contains("Invalid span name")) {
          return CheckResult.OK;
        }
        return CheckResult.failed(e);
      }

      return CheckResult.OK;
    }

}
