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

import static zipkin.storage.StorageAdapters.blockingToAsync;

import com.google.cloud.trace.v1.sink.TraceSink;
import com.google.cloud.trace.zipkin.translation.TraceTranslator;
import java.io.IOException;
import java.util.concurrent.Executor;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

/**
 * StackdriverStorageComponent is a StorageComponent that consumes spans using the StackdriverSpanConsumer.
 *
 * No SpanStore methods are implemented because read operations are not supported.
 */
public class StackdriverStorageComponent implements StorageComponent {

  private final TraceTranslator traceTranslator;
  private final AsyncSpanConsumer spanConsumer;

  public StackdriverStorageComponent(String projectId, TraceSink sink, Executor executor) {
    this.traceTranslator = new TraceTranslator(projectId);
    this.spanConsumer = blockingToAsync(new StackdriverSpanConsumer(traceTranslator, sink), executor);
  }

  @Override
  public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public AsyncSpanStore asyncSpanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public AsyncSpanConsumer asyncSpanConsumer() {
    return spanConsumer;
  }

  @Override
  public CheckResult check() {
    return CheckResult.OK;
  }

  @Override
  public void close() throws IOException {

  }
}
