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

import com.google.devtools.cloudtrace.v1.TraceSpan;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.util.List;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.translation.stackdriver.SpanTranslator;

@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public enum StackdriverEncoder implements BytesEncoder<Span> {
  V1 {
    @Override
    public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override
    public int sizeInBytes(Span input) {
      return 32 + translate(input).getSerializedSize();
    }

    /** This encodes a TraceSpan message prefixed by a potentially padded 32 character trace ID */
    @Override
    public byte[] encode(Span span) {
      TraceSpan translated = translate(span);
      byte[] result = new byte[32 + translated.getSerializedSize()];

      // Zipkin trace ID is conditionally 16 or 32 characters, but Stackdriver needs 32
      String traceId = span.traceId();
      if (traceId.length() == 16) {
        for (int i = 0; i < 16; i++) result[i] = '0';
        for (int i = 0; i < 16; i++) result[i + 16] = (byte) traceId.charAt(i);
      } else {
        for (int i = 0; i < 32; i++) result[i] = (byte) traceId.charAt(i);
      }

      CodedOutputStream output = CodedOutputStream.newInstance(result, 32, result.length - 32);
      try {
        translated.writeTo(output);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
      return result;
    }

    TraceSpan translate(Span span) {
      return SpanTranslator.translate(TraceSpan.newBuilder(), span).build();
    }

    @Override
    public byte[] encodeList(List<Span> spans) {
      throw new UnsupportedOperationException("used in rest api; unused in reporter and collector");
    }
  }
}
