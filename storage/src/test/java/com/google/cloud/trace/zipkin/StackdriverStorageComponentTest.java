package com.google.cloud.trace.zipkin;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import com.google.cloud.trace.v1.consumer.TraceConsumer;
import com.google.devtools.cloudtrace.v1.Traces;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

@RunWith(JUnitParamsRunner.class)
public class StackdriverStorageComponentTest {
  private ThreadPoolTaskExecutor executor;

  @After
  public void after() {
    if (executor != null) {
      executor.destroy();
    }
  }

  @Parameters({"5, 10, 100, 25"})
  @Test
  public void slowGRPC(
      final int corePoolSize,
      final int maxPoolSize,
      final long queueCapacity,
      final int grpcDelayInMillis)
      throws InterruptedException {
    final int awaitTerminationInSeconds = 2;
    final long maxCapacity =
        TimeUnit.SECONDS.toMillis(awaitTerminationInSeconds) * maxPoolSize / grpcDelayInMillis;

    this.executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinStackdriverStorage-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);

    executor.setQueueCapacity((int) queueCapacity);
    executor.setAwaitTerminationSeconds(awaitTerminationInSeconds);
    executor.initialize();

    TraceConsumer stackdriverConsumer = createSlowTraceConsumer(grpcDelayInMillis);
    StackdriverStorageComponent component =
        new StackdriverStorageComponent("test", stackdriverConsumer, executor);
    final SpanConsumer zipkinConsumer = component.spanConsumer();

    AtomicLong successCount = new AtomicLong();
    AtomicLong rejectCount = new AtomicLong();
    for (long i = 0; i < maxCapacity; i++) {
      zipkinConsumer.accept(createTestSpans(i)).enqueue(new Callback<Void>() {
        @Override
        public void onSuccess(@Nullable Void aVoid) {
          successCount.incrementAndGet();
        }

        @Override
        public void onError(Throwable throwable) {
          rejectCount.incrementAndGet();
        }
      });
    }

    executor.shutdown();

    System.out.println(format("Rejected: %s, completed: %s", rejectCount.get(), successCount.get()));

    assertThat("At least one task has to be rejected", rejectCount.get(), greaterThan(0L));
    assertThat(
        "Number of rejected + completed should be the same as number of samples",
        rejectCount.get() + successCount.get(),
        equalTo(maxCapacity));
    assertThat(
        "Number of completed tasks should be less or equal than max executor capacity",
        successCount.get(),
        lessThanOrEqualTo(maxCapacity));
    assertThat(
        "Number of completed tasks should be at least of task queue size",
        successCount.get(),
        greaterThanOrEqualTo(queueCapacity));
  }

  private TraceConsumer createSlowTraceConsumer(final long grpcDelayInMillis) {
    return new TraceConsumer() {
      @Override
      public void receive(Traces trace) {
        try {
          TimeUnit.MILLISECONDS.sleep(grpcDelayInMillis);
        } catch (InterruptedException e) {
          // no special handling
          e.printStackTrace();
        }
      }
    };
  }

  private List<Span> createTestSpans(long traceId) {
    return Arrays.asList(
        Span.newBuilder().traceId(Long.toString(traceId)).name("test-span").id("5").build());
  }
}
