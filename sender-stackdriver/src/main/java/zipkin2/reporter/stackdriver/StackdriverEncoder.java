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
import com.google.protobuf.ByteString;
import java.util.List;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.translation.stackdriver.SpanTranslator;

@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public enum StackdriverEncoder implements BytesEncoder<Span> {
  V1 {
    @Override public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override public int sizeInBytes(Span input) {
      // TODO: this allocates things
      return translate(input).getSerializedSize();
    }

    final ByteString LEADING_ZEROS = ByteString.copyFromUtf8("0000000000000000");

    /** This encodes a Trace message containing the trace ID and a single span */
    @Override public byte[] encode(Span span) {
      return translate(span).toByteArray(); // serialize in proto3 format
    }

    Trace translate(Span span) {
      // Zipkin trace ID is conditionally 16 or 32 characters, but Stackdriver needs 32
      ByteString traceId = ByteString.copyFromUtf8(span.traceId());
      if (traceId.size() == 16) traceId = LEADING_ZEROS.concat(traceId);
      // create a single-entry trace message, unpacked later
      return Trace.newBuilder()
          .setTraceIdBytes(traceId)
          .addSpans(SpanTranslator.translate(TraceSpan.newBuilder(), span))
          .build();
    }

    @Override public byte[] encodeList(List<Span> spans) {
      throw new UnsupportedOperationException("used in rest api; unused in reporter and collector");
    }
  }
}
