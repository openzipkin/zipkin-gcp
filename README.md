[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://circleci.com/gh/openzipkin/zipkin-gcp.svg?style=svg)](https://circleci.com/gh/openzipkin/zipkin-gcp)
[![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin-gcp/images/download.svg)](https://bintray.com/openzipkin/maven/zipkin-gcp/_latestVersion)

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
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.java:zipkin-autoconfigure-storage-stackdriver:LATEST:module stackdriver.jar
$ STORAGE_TYPE=stackdriver STACKDRIVER_PROJECT_ID=zipkin-demo \
    java \
    -Dloader.path='stackdriver.jar,stackdriver.jar!/lib' \
    -Dspring.profiles.active=stackdriver \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

## Example integrating Stackdriver Storage

If you cannot use our [Docker image](https://github.com/openzipkin/docker-zipkin-gcp), you can still integrate
yourself by downloading a couple jars.

[Here's an example](autoconfigure/storage-stackdriver#quick-start) of
integrating Stackdriver storage.
