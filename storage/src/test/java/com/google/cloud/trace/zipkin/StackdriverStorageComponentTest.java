package com.google.cloud.trace.zipkin;

import com.google.cloud.trace.v1.sink.TraceSink;
import com.google.devtools.cloudtrace.v1.Trace;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@RunWith(JUnitParamsRunner.class)
public class StackdriverStorageComponentTest
{
  private ThreadPoolTaskExecutor executor;

  @After
  public void after()
  {
    if (executor != null)
    {
      executor.destroy();
    }
  }

  @Parameters({"5, 10, 100, 25"})
  @Test
  public void slowGRPC(final int corePoolSize, final int maxPoolSize, final long queueCapacity, final int grpcDelayInMillis)
      throws InterruptedException
  {
    final int awaitTerminationInSeconds = 2;
    final long maxCapacity = TimeUnit.SECONDS.toMillis(awaitTerminationInSeconds) * maxPoolSize / grpcDelayInMillis;

    this.executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinStackdriverStorage-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);

    executor.setQueueCapacity((int) queueCapacity);
    executor.setAwaitTerminationSeconds(awaitTerminationInSeconds);
    executor.initialize();

    TraceSink sink = createSlowTraceSink(grpcDelayInMillis);
    StackdriverStorageComponent component = new StackdriverStorageComponent("test", sink, executor);
    final AsyncSpanConsumer consumer = component.asyncSpanConsumer();

    long rejectCount = 0;
    for (long i = 0; i < maxCapacity; i++)
    {
      try
      {
        consumer.accept(createTestSpans(i), Callback.NOOP);
      }
      catch (TaskRejectedException rejected)
      {
        rejectCount++;
      }
    }

    executor.shutdown();

    final long completedCount = executor.getThreadPoolExecutor().getCompletedTaskCount();
    System.out.println(format("Rejected: %s, completed: %s", rejectCount, completedCount));

    assertThat("At least one task has to be rejected", rejectCount, greaterThan(0l));
    assertThat("Number of rejected + completed should be the same as number of samples", rejectCount + completedCount, equalTo(maxCapacity));
    assertThat("Number of completed tasks should be less or equal than max executor capacity", completedCount, lessThanOrEqualTo(maxCapacity));
    assertThat("Number of completed tasks should be at least of task queue size", completedCount, greaterThanOrEqualTo(queueCapacity));
  }

  private TraceSink createSlowTraceSink(final long grpcDelayInMillis)
  {
    return new TraceSink()
    {
      @Override
      public void receive(Trace trace)
      {
        try
        {
          TimeUnit.MILLISECONDS.sleep(grpcDelayInMillis);
        }
        catch (InterruptedException e)
        {
          // no special handling
          e.printStackTrace();
        }
      }
    };
  }

  private List<Span> createTestSpans(long traceId)
  {
    return Arrays.asList(Span.builder()
                             .traceId(traceId)
                             .name("test-span")
                             .id(5)
                             .build());
  }
}
