/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin;

import com.google.cloud.trace.v1.consumer.TraceConsumer;
import com.google.cloud.trace.zipkin.translation.TraceTranslator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin2.CheckResult;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

/**
 * StackdriverStorageComponent is a StorageComponent that consumes spans using the
 * StackdriverSpanConsumer.
 *
 * <p>No SpanStore methods are implemented because read operations are not supported.
 */
public class StackdriverStorageComponent extends StorageComponent implements PublicMetrics {

  private final TraceTranslator traceTranslator;
  private final SpanConsumer spanConsumer;
  private final ThreadPoolTaskExecutor executor;
  private final LongAdder tracesSent;

  public StackdriverStorageComponent(
      String projectId, TraceConsumer consumer, ThreadPoolTaskExecutor executor) {
    this.traceTranslator = new TraceTranslator(projectId);
    this.tracesSent = new LongAdder();
    final TraceConsumer instrumentedConsumer =
        traces -> {
          consumer.receive(traces);
          this.tracesSent.add(traces != null ? traces.getTracesCount() : 0);
        };
    this.spanConsumer =
        new StackdriverSpanConsumer(traceTranslator, instrumentedConsumer, executor);
    this.executor = executor;
  }

  @Override
  public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public SpanConsumer spanConsumer() {
    return spanConsumer;
  }

  @Override
  public CheckResult check() {
    return CheckResult.OK;
  }

  @Override
  public void close() throws IOException {}

  @Override
  public Collection<Metric<?>> metrics() {
    final ArrayList<Metric<?>> result = new ArrayList<>();

    result.add(
        new Metric<>("gauge.zipkin_storage.stackdriver.active_threads", executor.getActiveCount()));
    result.add(new Metric<>("gauge.zipkin_storage.stackdriver.pool_size", executor.getPoolSize()));
    result.add(
        new Metric<>(
            "gauge.zipkin_storage.stackdriver.core_pool_size", executor.getCorePoolSize()));
    result.add(
        new Metric<>("gauge.zipkin_storage.stackdriver.max_pool_size", executor.getMaxPoolSize()));
    result.add(
        new Metric<>(
            "gauge.zipkin_storage.stackdriver.queue_size",
            executor.getThreadPoolExecutor().getQueue().size()));
    result.add(new Metric<>("counter.zipkin_storage.stackdriver.sent", tracesSent));

    return result;
  }

  public void resetMetrics() {
    tracesSent.reset();
  }
}
