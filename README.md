[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://travis-ci.org/openzipkin/zipkin-gcp.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin-gcp)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.gcp/zipkin-module-storage-stackdriver.svg)](https://search.maven.org/search?q=g:io.zipkin.gcp%20AND%20a:zipkin-module-storage-stackdriver)

# zipkin-gcp
Shared libraries that provide Zipkin integration with the Google Cloud Platform. Requires JRE 6 or later.

# Usage
These components integrate traced applications and servers through Google Cloud services
via interfaces defined in [Zipkin](https://github.com/openzipkin/zipkin)
and [zipkin-reporter-java](https://github.com/openzipkin/zipkin-reporter-java).

## Senders
The component in an traced application that sends timing data (spans)
out of process is called a Sender. Senders are called on interval by an
[async reporter](https://github.com/openzipkin/zipkin-reporter-java#asyncreporter).

NOTE: Applications can be written in any language, while we currently
only have senders in Java, senders in other languages are welcome.

Sender | Description
--- | ---
[Stackdriver Trace](./sender/stackdriver) | Free cloud service provider

## Collectors
The component in a zipkin server that receives trace data is called a
collector. This decodes spans reported by applications and persists them
to a configured storage component.

Collector | Description
--- | ---

## Storage
The component in a zipkin server that persists and queries collected
data is called `StorageComponent`. This primarily supports the Zipkin
Api and all collector components.

Storage | Description
--- | ---
[Stackdriver Trace](./storage/stackdriver) | Free cloud service provider

## Server integration
In order to integrate with zipkin-server, you need to use properties
launcher to load your collector (or sender) alongside the zipkin-server
process.

To integrate a module with a Zipkin server, you need to:
* add a module jar to the `loader.path`
* enable the profile associated with that module
* launch Zipkin with `PropertiesLauncher`

Each module will also have different minimum variables that need to be set.

Ex.
```
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.gcp:zipkin-module-storage-stackdriver:LATEST:module stackdriver.jar
$ STORAGE_TYPE=stackdriver STACKDRIVER_PROJECT_ID=zipkin-demo \
    java \
    -Dloader.path='stackdriver.jar,stackdriver.jar!/lib' \
    -Dspring.profiles.active=stackdriver \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

## Example integrating Stackdriver Storage

If you cannot use our [Docker image](./docker), you can still integrate
yourself by downloading a couple jars.

[Here's an example](autoconfigure/storage-stackdriver#quick-start) of
integrating Stackdriver storage.

## Troubleshooting

### Translation issues

When using a component that sends data to Stackdriver, if you see nothing in the console,
try enabling DEBUG logging on the translation component. If using Spring Boot (ex normal
app or zipkin server integration), add the following system property:

```
-Dlogging.level.zipkin2=DEBUG
```

Note: If using our docker image or anything that uses JAVA_OPTS, you can add this there.

With this in place, you'll see the input and output of translation like below. Keep a copy
of this when contacting us on [gitter](https://gitter.im/openzipkin/zipkin) for support.

```
2018-04-09 13:58:44.112 DEBUG [/] 11325 --- [   XNIO-2 I/O-3] z.t.stackdriver.SpanTranslator           : >> translating zipkin span: {"traceId":"d42316227862f939","parentId":"d42316227862f939","id":"dfbb21f9cf4c52b3","kind":"CLIENT","name":"get","timestamp":1523253523054380,"duration":4536,"localEndpoint":{"serviceName":"frontend","ipv4":"192.168.1.113"},"tags":{"http.method":"GET","http.path":"/api"}}
2018-04-09 13:58:44.113 DEBUG [/] 11325 --- [   XNIO-2 I/O-3] z.t.stackdriver.SpanTranslator           : << translated to stackdriver span: span_id: 16199746076534411288
kind: RPC_CLIENT
name: "get"
start_time {
  seconds: 1523253523
  nanos: 54380000
}
end_time {
  seconds: 1523253523
  nanos: 58916000
}
parent_span_id: 15286085897530046777
labels {
  key: "/http/method"
  value: "GET"
}
labels {
  key: "zipkin.io/http.path"
  value: "/api"
}
labels {
  key: "/component"
  value: "frontend"
}
```

### Healthcheck API

If you are running Zipkin server, `/health` HTTP endpoint can be used to check service health.

### gRPC Headers

If you suspect an issue between Zipkin server and Stackdriver, inspecting gRPC headers may be useful.
Set `STACKDRIVER_HTTP_LOGGING` environment variable to `HEADERS` to log gRPC status information.

### GCP console

If you believe that spans are reaching Stackdriver, verify what happens to them from the GCP side by visiting APIs & Services > Dashboard > Stackdriver Trace API > Metrics section.

* Is there any traffic at all? If not, then the requests are likely not reaching Stackdriver at all. Check your applications/proxies to make sure requests go to the right place.
* Are there errors? If so, narrow down the source of errors by selecting specific Credentials and Methods values from the filter drop-downs on top and seeing the effect in "Errors by API method" chart or "Methods" table.
    * If all writes are failing, check that your service account has access to `cloudtrace.agent` role or `cloudtrace.traces.patch` permission.
    * If reads are failing, check for `cloudtrace.user` role or the specific permission (full permissions list).
NOTE: read and write permissions are separate; only the admin role has both.

## Artifacts
All artifacts publish to the group ID "io.zipkin.gcp". We use a common
release version for all components.

### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/zipkin) and synchronized to [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.gcp%22)
### Library Snapshots
Snapshots are uploaded to [JFrog](https://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
