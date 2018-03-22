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
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class StackdriverEncoderBenchmarks {
  static final Span CLIENT_SPAN = Span.newBuilder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(Endpoint.newBuilder().serviceName("frontend").build())
      .remoteEndpoint(
          Endpoint.newBuilder().serviceName("backend").ip("192.168.99.101").port(9000).build()
      )
      .timestamp(1_000_000L) // 1 second after epoch
      .duration(123_456L)
      .addAnnotation(1_123_000L, "foo")
      .putTag("http.path", "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();

  @Benchmark public int sizeInBytesClientSpan_json_zipkin_json() {
    return SpanBytesEncoder.JSON_V2.sizeInBytes(CLIENT_SPAN);
  }

  @Benchmark public int sizeInBytesClientSpan_json_stackdriver_proto3() {
    return StackdriverEncoder.V1.sizeInBytes(CLIENT_SPAN);
  }

  @Benchmark public byte[] encodeClientSpan_json_zipkin_json() {
    return SpanBytesEncoder.JSON_V2.encode(CLIENT_SPAN);
  }

  @Benchmark public byte[] encodeClientSpan_json_stackdriver_proto3() {
    return StackdriverEncoder.V1.encode(CLIENT_SPAN);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + StackdriverEncoderBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
