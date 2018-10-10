# propagation-stackdriver

This is a propagation that behaves like `B3Propagation`, but falls back on tracing context from the 'x-cloud-trace-context' key.

To use it, you can feed it into your tracing system in the following way:

`Tracing.newBuilder().propagationFactory(StackdriverTracePropagation.FACTORY).build();`

If using Spring Boot auto-configuration, you can also add a dependency to `org.springframework.cloud:spring-cloud-gcp-starter-trace` and this propagation will be automatically set up.

# Extractor

This propagation makes use of the `CompositeExtractor` concept, which attempts to extract the tracing context from multiple extractors.
At the moment, it doesn't support partial extraction, meaning that if one extractor returns a context that is not empty, that context is returned and the following ones are not ran.

The first attempted extractor is the `B3Propagation` one, using the `X-B3-TraceId`, `X-B3-SpanId`, etc. keys.

If `B3Propagation` can't find a context, the next extractor is the `XCloudTraceContextExtractor`.
It checks the `x-cloud-trace-context` key, which is structured in the following way:

`x-cloud-trace-context: TRACE_ID/SPAN_ID;o=TRACE_TRUE`

* `TRACE_ID`: a 32-character hexadecimal value representing a 128-bit number.
* `SPAN_ID`: decimal representation of the unsigned span ID. If 0, it is ignored by Zipkin.
* `TRACE_TRUE`: `1` if the request should be traced, `0` otherwise.

If `TRACE_TRUE` is absent, the request is traced by default.
In other words, this extractor will trace keys structured like `x-cloud-trace-context: TRACE_ID/SPAN_ID`.

# Injector

After the trace context has been extracted from an incoming request, or a new one has been built, the remaining trace context is injected using `B3Propagation`'s injector.
This means that the trace context will now be managed through the B3 keys, `X-B3-TraceId`, `X-B3-SpanId`, etc.
