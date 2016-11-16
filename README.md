# Stackdriver Trace adapters for Zipkin.
This project provides adapters so that Zipkin tracing libraries can be used with
the Stackdriver Trace service.

Note: Due to differences between the Zipkin data model and the Stackdriver Trace data model,
only Zipkin traces that are recorded from Zipkin libraries that
[properly record timestamps](https://github.com/openzipkin/openzipkin.github.io/issues/49)
will be converted correctly. Converting Zipkin traces from other libraries may result in
disconnected spans within a trace.

## Collector
A drop-in replacement for the standard Zipkin HTTP collector that writes to the
Stackdriver Trace service.

### Credentials
The collector uses [Google Application Default
Credentials](https://developers.google.com/identity/protocols/application-default-credentials)
for authentication to the Stackdriver Trace API.
### Example Usage
#### Using an explicit file path
```
GOOGLE_APPLICATION_CREDENTIALS="/path/to/credentials.json" PROJECT_ID="my_project_id" java -jar collector.jar
```

#### Using a built-in GCP service account (such as on Google Compute Engine)
```
PROJECT_ID="my_project_id" java -jar collector.jar
```

#### Using Docker
```
docker run -d -e PROJECT_ID="my_project_id" -p 9411:9411 b.gcr.io/stackdriver-trace-docker/zipkin-collector
```
## Storage
A write-only Zipkin storage component that writes to the Stackdriver Trace service. This can be used
with zipkin-server.

## Translation
Contains code for translating from the Zipkin data model to the Stackdriver Trace data model.
