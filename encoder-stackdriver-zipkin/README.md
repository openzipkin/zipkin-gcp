# encoder-stackdriver-zipkin

This encodes zipkin spans into Stackdriver proto3 format.

```java
// connect the sender to the correct encoding
reporter = AsyncReporter.newBuilder(sender).build(StackdriverEncoder.V2);
```
