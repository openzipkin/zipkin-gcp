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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.reporter.stackdriver.internal.UnaryClientCall;

public class ExpiringHealthCheckTest {

  ExpiringHealthCheck expiringHealthCheck;
  Call mockServiceCall;

  @Before
  public void setUp() {
    mockServiceCall = mock(Call.class);
    expiringHealthCheck = new ExpiringHealthCheck(
        ByteString.copyFromUtf8("blah"), request -> mockServiceCall);
  }

  @Test
  public void failingServiceConveysException() throws Exception {
    when(mockServiceCall.execute()).thenThrow(new RuntimeException("unexpected boom"));
    CheckResult result = expiringHealthCheck.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).hasMessage("unexpected boom");
  }

  @Test
  public void successfulServiceExpectedValidationErrorIgnored() throws Exception {
    when(mockServiceCall.execute()).thenReturn(Empty.getDefaultInstance());

    CheckResult result = expiringHealthCheck.check();
    assertThat(result.ok()).isTrue();
    assertThat(result.error()).isNull();
  }

  @Test
  public void cachedValueUsedWhenSecondChecksDoneTooSoon() throws Exception {
    Clock startClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    when(mockServiceCall.execute()).thenReturn(Empty.getDefaultInstance());

    expiringHealthCheck.setClock(startClock);
    expiringHealthCheck.check();
    verify(mockServiceCall, times(1)).execute();

    // not enough time passed; cached value returned
    expiringHealthCheck.setClock(Clock.offset(startClock, Duration.ofSeconds(5)));
    expiringHealthCheck.check();
    verify(mockServiceCall, times(1)).execute();

  }

  @Test
  public void serviceCalledAgainWhenEnoughTimePassed() throws Exception {
    Clock startClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    when(mockServiceCall.execute()).thenReturn(Empty.getDefaultInstance());

    expiringHealthCheck.setClock(startClock);
    expiringHealthCheck.check();
    verify(mockServiceCall, times(1)).execute();

    // enough time passed; service called again
    expiringHealthCheck.setClock(Clock.offset(startClock, Duration.ofSeconds(65)));
    expiringHealthCheck.check();
    verify(mockServiceCall, times(2)).execute();

  }
}
