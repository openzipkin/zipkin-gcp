/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.reporter.stackdriver.brave;

import brave.Tags;
import brave.handler.MutableSpan;
import brave.handler.MutableSpanBytesEncoder;
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

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class StackdriverV2EncoderBenchmarks {
  static final StackdriverV2Encoder encoder = new StackdriverV2Encoder(Tags.ERROR);
  static final MutableSpanBytesEncoder braveEncoder =
      MutableSpanBytesEncoder.zipkinJsonV2(Tags.ERROR);
  static final MutableSpan CLIENT_SPAN = clientSpan();

  static MutableSpan clientSpan() {
    MutableSpan braveSpan = new MutableSpan();
    braveSpan.traceId("7180c278b62e8f6a216a2aea45d08fc9");
    braveSpan.parentId("6b221d5bc9e6496c");
    braveSpan.id("5b4185666d50f68b");
    braveSpan.name("get");
    braveSpan.kind(brave.Span.Kind.CLIENT);
    braveSpan.localServiceName("frontend");
    braveSpan.localIp("127.0.0.1");
    braveSpan.remoteServiceName("backend");
    braveSpan.remoteIpAndPort("192.168.99.101", 9000);
    braveSpan.startTimestamp(1472470996199000L);
    braveSpan.finishTimestamp(1472470996199000L + 207000L);
    braveSpan.annotate(1472470996238000L, "foo");
    braveSpan.annotate(1472470996403000L, "bar");
    braveSpan.tag("clnt/finagle.version", "6.45.0");
    braveSpan.tag("http.path", "/api");
    return braveSpan;
  }

  @Benchmark
  public int sizeInBytesClientSpan_json_zipkin_json() {
    return braveEncoder.sizeInBytes(CLIENT_SPAN);
  }

  @Benchmark
  public int sizeInBytesClientSpan_json_stackdriver_proto3() {
    return encoder.sizeInBytes(CLIENT_SPAN);
  }

  @Benchmark
  public byte[] encodeClientSpan_json_zipkin_json() {
    return braveEncoder.encode(CLIENT_SPAN);
  }

  @Benchmark
  public byte[] encodeClientSpan_json_stackdriver_proto3() {
    return encoder.encode(CLIENT_SPAN);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt =
        new OptionsBuilder()
            .include(".*" + StackdriverV2EncoderBenchmarks.class.getSimpleName() + ".*")
            .build();

    new Runner(opt).run();
  }
}
