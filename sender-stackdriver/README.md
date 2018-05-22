# sender-stackdriver

This encodes zipkin spans into Stackdriver proto3 format. Later, they
are bundled into a PatchTracesRequest and sent via gRPC transport.

## Configuration

This sender only works with an async reporter configured for StackDriver
encoding.

A minimal configuration of this sender would be:

```java
// reads env GOOGLE_APPLICATION_CREDENTIALS
credentials = GoogleCredentials.getApplicationDefault()
    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/trace.append"));

// Setup the sender to authenticate the Google Stackdriver service
sender = StackdriverSender.newBuilder()
  .projectId("zipkin-demo")
  .callOptions(CallOptions.DEFAULT.withCallCredentials(MoreCallCredentials.from(credentials)))
  .build();

// connect the sender to the correct encoding
reporter = AsyncReporter.newBuilder(sender).build(StackdriverEncoder.V1);
```

Note: Use 128-bit trace IDs and do not re-use span IDs across client and server.
* Ex. in Brave `Tracing.Builder.supportsJoin(false).traceId128Bit(true)`

Note: There are a few library dependencies implied
* io.grpc:grpc-auth < for authentication in general
* com.google.auth:google-auth-library-oauth2-http < for GCP oauth
* io.grpc:grpc-netty < the remote connection to GCP