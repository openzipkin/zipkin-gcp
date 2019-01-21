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
package zipkin2.translation.stackdriver;

import com.google.devtools.cloudtrace.v2.TruncatableString;

final class SpanUtil {

  static TruncatableString toTruncatableString(String string) {
    // NOTE: Java and Go implementations of opencensus seem to differ in their interpretation of
    // whether a "request" Span needs to have truncated strings. We'll assume the simpler
    // implementation of Java is correct and that the truncation semantics are for responses, not
    // requests.
    //
    // Reference:
    //   Java - https://github.com/census-instrumentation/opencensus-java/blob/d5f7efe3ea6b808bad1b3c36db9e496d72e75238/exporters/trace/stackdriver/src/main/java/io/opencensus/exporter/trace/stackdriver/StackdriverV2ExporterHandler.java#L420
    //
    //   Go - https://github.com/census-ecosystem/opencensus-go-exporter-stackdriver/blob/899e456273f5c46d23aef8f0c66e899d7d1e17f4/trace_proto.go#L247
    return TruncatableString.newBuilder().setValue(string).setTruncatedByteCount(0).build();
  }

  private SpanUtil() {}
}
