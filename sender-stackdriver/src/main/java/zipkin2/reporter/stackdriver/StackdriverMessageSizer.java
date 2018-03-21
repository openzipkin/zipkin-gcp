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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag;

/** Mutable to avoid array allocation when computing the size of a message */
final class StackdriverMessageSizer {
  ArrayList<byte[]> sortedEncodedTraces = new ArrayList<>();
  byte[] currentTraceId = new byte[32], lastTraceId = new byte[32];

  int messageSizeInBytes(int projectIdInBytes, List<byte[]> encodedTraces) {
    int length = encodedTraces.size();
    if (length == 0) return 0;
    if (length == 1) return messageSizeInBytes(projectIdInBytes, encodedTraces.get(0).length);

    // encoded spans include potentially redundant trace IDs. First, we need to sort them
    sortedEncodedTraces.clear();
    sortedEncodedTraces.addAll(encodedTraces);
    Collections.sort(sortedEncodedTraces, TRACE_ID_COMPARATOR);
    byte[] currentTrace = sortedEncodedTraces.get(0);
    System.arraycopy(currentTrace, TRACE_ID_OFFSET, currentTraceId, 0, 32);
    System.arraycopy(currentTrace, TRACE_ID_OFFSET, lastTraceId, 0, 32);

    int sizeOfAllTraceFields = resizeTraceField(projectIdInBytes, currentTrace.length);
    // first collect the concatenated size of the encoded traces
    for (int i = 1; i < length; i++) {
      currentTrace = sortedEncodedTraces.get(i);
      System.arraycopy(currentTrace, TRACE_ID_OFFSET, currentTraceId, 0, 32);

      if (Arrays.equals(currentTraceId, lastTraceId)) {
        // we are appending a span to an existing trace, subtract trace ID overhead
        sizeOfAllTraceFields += currentTrace.length - 1 - 32;
      } else {
        // we are starting a new trace field
        sizeOfAllTraceFields += resizeTraceField(projectIdInBytes, currentTrace.length);
        System.arraycopy(currentTrace, TRACE_ID_OFFSET, lastTraceId, 0, 32);
      }
    }

    int result = projectIdInBytes;
    // add the overhead for the traces field
    result += 1 + computeUInt32SizeNoTag(sizeOfAllTraceFields) + sizeOfAllTraceFields;
    return result;
  }

  static int messageSizeInBytes(int projectIdInBytes, int traceSizeInBytes) {
    int sizeOfAllTraceFields = resizeTraceField(projectIdInBytes, traceSizeInBytes);
    int result = projectIdInBytes;
    // add the overhead for the traces field
    result += 1 + computeUInt32SizeNoTag(sizeOfAllTraceFields) + sizeOfAllTraceFields;
    return result;
  }

  /** Corrects the size of a traces field, knowing it is missing the project Id */
  static int resizeTraceField(int projectIdInBytes, int encodedTraceMissingProjectId) {
    int sizeOfTrace = projectIdInBytes + encodedTraceMissingProjectId;
    // add the overhead for a repeated trace field
    return 1 + computeUInt32SizeNoTag(sizeOfTrace) + sizeOfTrace;
  }

  // bytes should start with a trace_id field, the length of trace ID is always 32, so we know the offset.
  static final int TRACE_ID_OFFSET = 1 /* field no */ + 1 /* size to encode the number 32 */;
  static final Comparator<byte[]> TRACE_ID_COMPARATOR = new Comparator<byte[]>() {
    @Override public int compare(byte[] left, byte[] right) {
      for (int i = TRACE_ID_OFFSET, length = TRACE_ID_OFFSET + 32; i < length; i++) {
        int leftByte = left[i] & 0xff;
        int rightByte = right[i] & 0xff;
        if (leftByte == rightByte) continue;
        return leftByte < rightByte ? -1 : 1;
      }
      return 0;
    }
  };
}
