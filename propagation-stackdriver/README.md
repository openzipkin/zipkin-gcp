# propagation-stackdriver

This is a propagation that behaves like `B3Propagation`, but also extracts the tracing context from the 'x-cloud-trace-context' key.

# Extractor

This propagation makes use of the `CompositeExtractor` concept, which attempts to extract the tracing context from multiple extractors.
At the moment, it doesn't support partial extraction, meaning that if one extractor returns a context that is not empty, that context is returned and the following ones are not ran.

The first attempted extractor is the `XCloudTraceContextExtractor`.
It checks the `x-cloud-trace-context` key, which is structured in the following way:

`x-cloud-trace-context: TRACE_ID/SPAN_ID;o=TRACE_TRUE`

* `TRACE_ID`: a 32-character hexadecimal value representing a 128-bit number.
* `SPAN_ID`: decimal representation of the unsigned span ID. If 0, it is ignored by Zipkin.
* `TRACE_TRUE`: `1` if the request should be traced, `0` otherwise.

If `TRACE_TRUE` is absent, the request is traced.
In other words, this extractor will trace keys structured like `x-cloud-trace-context: TRACE_ID/SPAN_ID`.

If `XCloudTraceContextExtractor` can't find a context, the next extractor is the `B3Propagation` one.
Extraction will proceed using the `X-B3-TraceId`, `X-B3-SpanId`, etc. keys.

# Injector

After the trace context has been extracted from an incoming request, or a new one has been built, the remaining trace context is injected using `B3Propagation`'s injector.
This means that the trace context will now be managed through the B3 keys, `X-B3-TraceId`, `X-B3-SpanId`, etc.
