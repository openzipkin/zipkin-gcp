# propagation-stackdriver

This is a propagation that behaves like `B3Propagation`, but falls back on tracing context from the 'x-cloud-trace-context' key.

To use it, you can feed it into your tracing system in the following way:

```java
tracingBuilder.propagationFactory(StackdriverTracePropagation.newFactory(B3Propagation.FACTORY));
```

If using Spring Boot auto-configuration, you can also add a dependency to `org.springframework.cloud:spring-cloud-gcp-starter-trace` and this propagation will be automatically set up.

# Extractor
This component prefers the `Propagation.Factory` passed to `StackdriverTracePropagation.newFactory`,
typically, but not always set to `B3Propagation.FACTORY`. For example, this may be set to a
different configuration using `B3Propagation.factoryBuilder()`.

For example, if the "b3" header exists, it is used and any "x-cloud-trace-context" will be ignored.
When absent, the following applies:

`x-cloud-trace-context: TRACE_ID/SPAN_ID;o=TRACE_TRUE`

* `TRACE_ID`: a 32-character hexadecimal value representing a 128-bit number.
* `SPAN_ID`: decimal representation of the unsigned span ID.
* `TRACE_TRUE`: `1` if the request should be traced, `0` otherwise.

### Notes

- One may also choose to omit the span ID by setting it to 0 like this: `TRACE_ID/0;o=TRACE_TRUE`.
  In this case, a new root span will be generated for the request.

- If `TRACE_TRUE` is omitted, then the decision to trace the request will be deferred to the sampler.


# Injector

The "x-cloud-trace-context" is never sent. Whatever the primary format dictates for the request will
be used. For example, `B3Propagation` implements single format "b3" for messaging requests, while
multiple headers (ex "X-B3-TraceID") are default for HTTP and RPC. In other words, `B3Propagation`
is used as-is, depending on its configuration, and "x-cloud-trace-context" is never sent.
