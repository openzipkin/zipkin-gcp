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

import java.util.List;

import static com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag;

/** Supports {@link StackdriverSender#messageSizeInBytes(List)} */
final class PatchTracesRequestSizer implements TraceCollator.Observer {
  /** Supports {@link StackdriverSender#messageSizeInBytes(int)} */
  static int size(int projectIdFieldSize, int traceSpanSize) {
    int sizeOfTraceMessage = traceSize(projectIdFieldSize, traceSpanSize);
    int sizeOfTraceField = 1 + computeUInt32SizeNoTag(sizeOfTraceMessage) + sizeOfTraceMessage;
    return sizeOfPatchTracesRequest(projectIdFieldSize, sizeOfTraceField);
  }

  final int projectIdFieldSize;
  int traceListSize = 0;
  int traceSpanSize;
  int currentTraceSize;

  PatchTracesRequestSizer(int projectIdFieldSize) {
    this.projectIdFieldSize = projectIdFieldSize;
  }

  @Override public void firstTrace(char[] traceId, byte[] traceIdPrefixedSpan) {
    traceSpanSize = traceIdPrefixedSpan.length - 32;
    currentTraceSize = traceSize(projectIdFieldSize, traceSpanSize);
  }

  @Override public void nextSpan(byte[] traceIdPrefixedSpan) {
    // we are appending a span to an existing trace, subtract trace ID prefix
    traceSpanSize = traceIdPrefixedSpan.length - 32;
    currentTraceSize += 1 + computeUInt32SizeNoTag(traceSpanSize) + traceSpanSize;
  }

  @Override public void nextTrace(char[] traceId, byte[] traceIdPrefixedSpan) {
    traceListSize += 1 + computeUInt32SizeNoTag(currentTraceSize) + currentTraceSize;
    traceSpanSize = traceIdPrefixedSpan.length - 32;
    currentTraceSize = traceSize(projectIdFieldSize, traceSpanSize);
  }

  int finish() {
    traceListSize += 1 + computeUInt32SizeNoTag(currentTraceSize) + currentTraceSize;
    return sizeOfPatchTracesRequest(projectIdFieldSize, traceListSize);
  }

  static int sizeOfPatchTracesRequest(int projectIdFieldSize, int traceListSize) {
    int result = projectIdFieldSize;
    result += 1 + computeUInt32SizeNoTag(traceListSize) + traceListSize; // traces field Size
    return result;
  }

  /** a trace message includes the project Id and trace span */
  static int traceSize(int projectIdFieldSize, int traceSpanSize) {
    int result = projectIdFieldSize;
    result += 1 + 1 + 32; // trace ID field Size
    result += 1 + computeUInt32SizeNoTag(traceSpanSize) + traceSpanSize;
    return result;
  }
}
