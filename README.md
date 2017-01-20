# Stackdriver Trace Zipkin Collector

[![Build Status](https://travis-ci.org/GoogleCloudPlatform/stackdriver-zipkin.svg?branch=master)](https://travis-ci.org/GoogleCloudPlatform/stackdriver-zipkin) 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.trace.adapters.zipkin/collector/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.trace.adapters.zipkin/collector)

This project provides adapters so that Zipkin tracing libraries can be used with
Google's free [Stackdriver Trace](https://cloud.google.com/trace/) distributed tracing service. A how-to guide and documentation are available [here](https://cloud.google.com/trace/docs/zipkin). If you are not already using Zipkin, you may be interested in the [offical Stackdriver Trace SDKs](https://cloud.google.com/trace/api/) instead.

Note: Due to differences between the Zipkin data model and the Stackdriver Trace data model,
only Zipkin traces that are recorded from Zipkin libraries that
[properly record timestamps](https://github.com/openzipkin/openzipkin.github.io/issues/49)
will be converted correctly. Converting Zipkin traces from other libraries may result in
disconnected spans within a trace.

## Collector
A drop-in replacement for the standard Zipkin HTTP collector that writes to the
Stackdriver Trace service.

### Configuration

|Environment Variable           | Value            |
|-------------------------------|------------------|
|GOOGLE_APPLICATION_CREDENTIALS | Optional. [Google Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials). |
|PROJECT_ID                     | GCP projectId. Optional on GCE. Required on all other platforms. If not provided on GCE, it will default to the projectId associated with the GCE resource. |
|COLLECTOR_SAMPLE_RATE          | Optional. Percentage of traces to retain, defaults to always sample. However, if there is a problem sending Traces to the Stackdriver Trace service, Traces may be dropped.

### Example Usage
The collector may be downloaded from [Maven Central](https://search.maven.org/remote_content?g=com.google.cloud.trace.adapters.zipkin&a=collector&v=LATEST)
or run using the Docker image:
`gcr.io/stackdriver-trace-docker/zipkin-collector`.

#### Running on GCE
By default, the Zipkin collector uses the [Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials)
and writes traces to the projectId associated with the GCE resource. If this is desired, no
additional configuration is required.
```
java -jar collector.jar
```
or just
```
./collector.jar
```

If docker is used from a GCE host, authentication will happen automatically and Zipkin collector can be started with:
```
docker run -p 9411:9411 gcr.io/stackdriver-trace-docker/zipkin-collector
```


#### Using an explicit projectId and credentials file path
```
GOOGLE_APPLICATION_CREDENTIALS="/path/to/credentials.json" PROJECT_ID="my_project_id" java -jar collector.jar
```
```
docker run -v /path/to_credentials:/opt/gcloud -e GOOGLE_APPLICATION_CREDENTIALS="/opt/gcloud/credentials.json" -e PROJECT_ID="my_project_id" -p 9411:9411 gcr.io/stackdriver-trace-docker/zipkin-collector
```

## Storage
A write-only Zipkin storage component that writes to the Stackdriver Trace service. This can be used
with zipkin-server.

## Translation
Contains code for translating from the Zipkin data model to the Stackdriver Trace data model.
