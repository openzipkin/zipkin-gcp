/**
 * Copyright 2016-2018 The OpenZipkin Authors
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

import com.google.devtools.cloudtrace.v1.Trace;
import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.devtools.cloudtrace.v1.Traces;
import java.io.IOException;

final class TracesParser implements TraceCollator.Observer {

  /** Special-case which avoids using thread local for a single span message */
  static Traces parse(String projectId, byte[] traceIdPrefixedSpan) {
    char[] traceId = new char[32];
    for (int i = 0; i < 32; i++) traceId[i] = (char) traceIdPrefixedSpan[i];

    Trace trace = Trace.newBuilder()
        .setProjectId(projectId)
        .setTraceId(new String(traceId))
        .addSpans(parseTraceIdPrefixedSpan(traceIdPrefixedSpan)).build();

    return Traces.newBuilder().addTraces(trace).build();
  }

  final String projectId;
  final Trace.Builder currentTrace = Trace.newBuilder();
  final Traces.Builder tracesBuilder = Traces.newBuilder();

  TracesParser(String projectId) {
    this.projectId = projectId;
  }

  @Override public void firstTrace(char[] traceId, byte[] traceIdPrefixedSpan) {
    initCurrentTrace(traceId, traceIdPrefixedSpan);
    tracesBuilder.clear();
  }

  void initCurrentTrace(char[] traceId, byte[] traceIdPrefixedSpan) {
    currentTrace.clearSpans()
        .setProjectId(projectId)
        .setTraceId(new String(traceId))
        .addSpans(parseTraceIdPrefixedSpan(traceIdPrefixedSpan));
  }

  @Override public void nextSpan(byte[] traceIdPrefixedSpan) {
    TraceSpan traceSpan = parseTraceIdPrefixedSpan(traceIdPrefixedSpan);
    currentTrace.addSpans(traceSpan);
  }

  @Override public void nextTrace(char[] traceId, byte[] traceIdPrefixedSpan) {
    tracesBuilder.addTraces(currentTrace);
    initCurrentTrace(traceId, traceIdPrefixedSpan);
  }

  Traces finish() {
    return tracesBuilder.addTraces(currentTrace).build();
  }

  static TraceSpan parseTraceIdPrefixedSpan(byte[] traceIdPrefixedSpan) {
    // start parsing after the trace ID
    int off = 32, len = traceIdPrefixedSpan.length - off;
    try {
      return TraceSpan.parser().parseFrom(traceIdPrefixedSpan, off, len);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
