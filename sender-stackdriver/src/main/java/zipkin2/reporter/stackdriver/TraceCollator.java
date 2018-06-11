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

import com.google.common.primitives.UnsignedBytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Mutable to avoid array allocation when collating traces */
final class TraceCollator {
  final ArrayList<byte[]> sortedTraceIdPrefixedSpans = new ArrayList<>();
  final char[] currentTraceId = new char[32], lastTraceId = new char[32];

  interface Observer {
    void firstTrace(char[] traceId, byte[] traceIdPrefixedSpan);

    void nextSpan(byte[] traceIdPrefixedSpan);

    void nextTrace(char[] traceId, byte[] traceIdPrefixedSpan);
  }

  void collate(List<byte[]> traceIdPrefixedSpans, Observer observer) {
    int length = traceIdPrefixedSpans.size();
    assert length > 1;

    // trace IDs are potentially redundant. Sort first
    sortedTraceIdPrefixedSpans.clear();
    sortedTraceIdPrefixedSpans.addAll(traceIdPrefixedSpans);
    Collections.sort(sortedTraceIdPrefixedSpans, TRACE_ID_COMPARATOR);

    byte[] currentTraceIdPrefixedSpan = sortedTraceIdPrefixedSpans.get(0);
    parseCurrentTraceId(currentTraceIdPrefixedSpan);
    setLastTraceId();

    observer.firstTrace(currentTraceId, currentTraceIdPrefixedSpan);

    for (int i = 1; i < length; i++) {
      currentTraceIdPrefixedSpan = sortedTraceIdPrefixedSpans.get(i);
      parseCurrentTraceId(currentTraceIdPrefixedSpan);

      if (Arrays.equals(currentTraceId, lastTraceId)) {
        observer.nextSpan(currentTraceIdPrefixedSpan);
      } else {
        observer.nextTrace(currentTraceId, currentTraceIdPrefixedSpan);
        setLastTraceId();
      }
    }
  }

  void parseCurrentTraceId(byte[] currentTraceIdPrefixedSpan) {
    for (int i = 0; i < 32; i++) currentTraceId[i] = (char) currentTraceIdPrefixedSpan[i];
  }

  void setLastTraceId() {
    System.arraycopy(currentTraceId, 0, lastTraceId, 0, 32);
  }

  static final Comparator<byte[]> TRACE_ID_COMPARATOR =
      new Comparator<byte[]>() {
        @Override
        public int compare(byte[] left, byte[] right) {
          for (int i = 0; i < 32; i++) {
            int comparison = UnsignedBytes.compare(left[i], right[i]);
            if (comparison == 0) continue;
            return comparison;
          }
          return 0;
        }
      };
}
