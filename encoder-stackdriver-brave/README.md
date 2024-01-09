# encoder-stackdriver-brave

This encodes brave spans into Stackdriver proto3 format.

```java
// connect the sender to the correct encoding
spanHandler = AsyncZipkinSpanHandler.newBuilder(sender).build(new StackdriverV2Encoder(Tags.ERROR));
```
