Zipkin GCP Rationale
==============
Many choices inherit rationale from upstream libraries. Become familiar with these, in order to
deeply understand choices:

* [Brave RATIONALE](https://github.com/openzipkin/brave/blob/master/brave/RATIONALE.md)
* [Brave Instrumentation RATIONALE](https://github.com/openzipkin/brave/blob/master/instrumentation/RATIONALE.md)
* [Zipkin RATIONALE](https://github.com/openzipkin/zipkin/blob/master/zipkin/RATIONALE.md)

## Why floor Java version 1.8

Brave and zipkin reporter support a floor version of Java 1.6. There are a few modules here that
were lower than Java 1.8 when we moved the floor up to 1.8.

* zipkin-sender-stackdriver (via zipkin-translation-stackdriver) - 1.7 (defined by gRPC)
* brave-propagation-stackdriver - 1.6 (defined by us)

All the other modules directly or indirectly required minimum Java 1.8. We decided to move the floor
version up to 1.8 for convenience of the maintainers, and a bet that zipkin-sender-stackdriver users
are not reliant on JRE 1.7 or old versions of Android.

1.8 is more convenient as we can re-use tests with a different source level without resorting to
Maven invoker to execute them. Notably, [IDEA-85478](https://youtrack.jetbrains.com/issue/IDEA-85478)
causes this problem, so interested parties should upvote or otherwise encourage that change.

### What if users really are reliant on Java 1.7 bytecode?

If users rely on Java 1.7 bytecode for `zipkin-sender-stackdriver`, they will tell us.

We have accidentally broken compile versions or modularity before, and people have reported, and we
promptly fixed. We can justify the effort of making `zipkin-sender-stackdriver` work in Java 1.7
again on demand (likely via use of invoker tests to isolate class versions).  