/*
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

import com.google.devtools.cloudtrace.v2.Span;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static zipkin2.reporter.stackdriver.StackdriverEncoderBenchmarks.CLIENT_SPAN;
import static zipkin2.reporter.stackdriver.StackdriverSender.SPAN_ID_PREFIX;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class TracesParserBenchmarks {
  static final String PROJECT_ID = "zipkin-demo";
  static final ByteString TRACE_ID_PREFIX =
      ByteString.copyFromUtf8("projects/" + PROJECT_ID + "/traces/");
  static final int SPAN_NAME_SIZE = TRACE_ID_PREFIX.size() + 32 + SPAN_ID_PREFIX.size() + 16;
  static final byte[] ENCODED_CLIENT_SPAN = StackdriverEncoder.V2.encode(CLIENT_SPAN);
  static final List<byte[]> HUNDRED_ENCODED_CLIENT_SPANS;

  static {
    HUNDRED_ENCODED_CLIENT_SPANS = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      for (int j = 1; j <= 10; j++) {
        HUNDRED_ENCODED_CLIENT_SPANS.add(
            StackdriverEncoder.V2.encode(
                CLIENT_SPAN
                    .toBuilder()
                    .traceId(Integer.toHexString(i))
                    .id(Integer.toHexString(j))
                    .build()));
      }
    }
  }

  @Benchmark
  public List<Span> parseClientSpan() {
    List<Span> spans = new ArrayList<>();
    spans.add(StackdriverSender.parseTraceIdPrefixedSpan(
        ENCODED_CLIENT_SPAN, SPAN_NAME_SIZE, TRACE_ID_PREFIX));
    return spans;
  }

  @Benchmark
  public List<Span> parse100ClientSpans() {
    List<Span> spans = new ArrayList<>();
    for (int i = 0, len = HUNDRED_ENCODED_CLIENT_SPANS.size(); i < len; i++) {
      spans.add(StackdriverSender.parseTraceIdPrefixedSpan(
          HUNDRED_ENCODED_CLIENT_SPANS.get(i), SPAN_NAME_SIZE, TRACE_ID_PREFIX));
    }
    return spans;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt =
        new OptionsBuilder()
            .include(".*" + TracesParserBenchmarks.class.getSimpleName() + ".*")
            .build();

    new Runner(opt).run();
  }
}
