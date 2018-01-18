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
import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.Traces;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

/**
 * Consumes Zipkin spans, translates them to Stackdriver spans using a provided TraceTranslator, and
 * writes them to a provided Stackdriver {@link TraceConsumer}.
 */
public class StackdriverSpanConsumer implements SpanConsumer {

  private final TraceTranslator translator;
  private final TraceConsumer consumer;
  private final AsyncListenableTaskExecutor executor;

  public StackdriverSpanConsumer(
      TraceTranslator translator, TraceConsumer consumer, AsyncListenableTaskExecutor executor) {
    this.translator = translator;
    this.consumer = consumer;
    this.executor = executor;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    final Traces.Builder tracesBuilder = Traces.newBuilder();
    for (Trace trace : translator.translateSpans(spans)) {
      tracesBuilder.addTraces(trace);
    }
    return new TracesCall(tracesBuilder.build());
  }

  private class TracesCall extends Call<Void> {

    private final Traces traces;
    private final AtomicBoolean executed;

    private volatile boolean canceled;

    private TracesCall(Traces traces) {
      this.traces = traces;
      executed = new AtomicBoolean();
    }

    @Override
    public Void execute() throws IOException {
      markExecuted();
      if (canceled) {
        throw new IOException("Canceled");
      }
      consumer.receive(traces);
      return null;
    }

    @Override
    public void enqueue(Callback<Void> callback) {
      markExecuted();
      if (canceled) {
        callback.onError(new IOException("Canceled"));
        return;
      }
      try {
        executor
            .submitListenable(
                () -> {
                  consumer.receive(traces);
                  return (Void) null;
                })
            .addCallback(callback::onSuccess, callback::onError);
      } catch (Throwable t) {
        callback.onError(t);
      }
    }

    @Override
    public void cancel() {
      canceled = true;
    }

    @Override
    public boolean isCanceled() {
      return canceled;
    }

    @Override
    public Call<Void> clone() {
      return new TracesCall(traces);
    }

    private void markExecuted() {
      if (!executed.compareAndSet(false, true)) {
        throw new IllegalStateException("Already Executed");
      }
    }
  }
}
