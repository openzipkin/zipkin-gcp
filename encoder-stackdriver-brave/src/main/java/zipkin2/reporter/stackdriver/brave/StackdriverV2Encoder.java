/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
package zipkin2.reporter.stackdriver.brave;

import brave.Tag;
import brave.handler.MutableSpan;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.Encoding;

@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public class StackdriverV2Encoder implements BytesEncoder<MutableSpan> {
  final SpanTranslator spanTranslator;

  public StackdriverV2Encoder(Tag<Throwable> errorTag) {
    if (errorTag == null) throw new NullPointerException("errorTag == null");
    this.spanTranslator = new SpanTranslator(errorTag);
  }

  @Override
  public Encoding encoding() {
    return Encoding.PROTO3;
  }

  @Override
  public int sizeInBytes(MutableSpan input) {
    return 32 + translate(input).getSerializedSize();
  }

  /** This encodes a TraceSpan message prefixed by a potentially padded 32 character trace ID */
  @Override
  public byte[] encode(MutableSpan span) {
    Span translated = translate(span);
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

  Span translate(MutableSpan span) {
    return spanTranslator.translate(Span.newBuilder(), span).build();
  }
}
