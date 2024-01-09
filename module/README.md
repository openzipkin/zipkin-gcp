# module-storage-stackdriver

## Overview

This is a module that can be added to a [Zipkin Server](https://github.com/openzipkin/zipkin/tree/master/zipkin-server)
deployment to send Spans to Google Stackdriver Trace over gRPC transport.

This currently only supports sending to Stackdriver, not reading back spans from the service.
Internally this module wraps the [StackdriverStorage](https://github.com/openzipkin/zipkin-gcp/tree/master/storage-stackdriver)
and exposes configuration options through environment variables.

## Experimental
* Note: This is currently experimental! *
* Note: This requires reporters send 128-bit trace IDs *
* Check https://github.com/openzipkin/b3-propagation/issues/6 for tracers that support 128-bit trace IDs

## Quick start

JRE 11+ is required to run Zipkin server.

Fetch the latest released
[executable jar for Zipkin server](https://search.maven.org/remote_content?g=io.zipkin&a=zipkin-server&v=LATEST&c=exec)
and
[module jar for stackdriver storage](https://search.maven.org/remote_content?g=io.zipkin.gcp&a=zipkin-module-gcp&v=LATEST&c=module).
Run Zipkin server with the StackDriver Storage enabled.

For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.gcp:zipkin-module-gcp:LATEST:module gcp.jar
$ STORAGE_TYPE=stackdriver STACKDRIVER_PROJECT_ID=zipkin-demo \
    java \
    -Dloader.path='gcp.jar,gcp.jar!/lib' \
    -Dspring.profiles.active=gcp \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

After executing these steps, applications can send spans
http://localhost:9411/api/v2/spans (or the legacy endpoint http://localhost:9411/api/v1/spans)

The Zipkin server can be further configured as described in the
[Zipkin server documentation](https://github.com/openzipkin/zipkin/blob/master/zipkin-server/README.md).

### Configuration

Configuration can be applied either through environment variables or an external Zipkin
configuration file.  The module includes default configuration that can be used as a 
[reference](https://github.com/openzipkin/zipkin-gcp/tree/master/autoconfigure/storage-stackdriver/src/main/resources/zipkin-server-stackdriver.yml)
for users that prefer a file based approach.

#### Environment Variables

|Environment Variable           | Value            |
|-------------------------------|------------------|
|GOOGLE_APPLICATION_CREDENTIALS | Optional. [Google Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials). Not managed by spring boot. |
|STACKDRIVER_PROJECT_ID         | GCP projectId. Optional on GCE. Required on all other platforms. If not provided on GCE, it will default to the projectId associated with the GCE resource. |
|STACKDRIVER_API_HOST           | host:port combination of the gRPC endpoint. Default: cloudtrace.googleapis.com:443 |
|STACKDRIVER_HTTP_LOGGING       | When set, controls the volume of HTTP logging of the Stackdriver Trace Api. Options are BASIC and HEADERS |

### Running

```bash
$ STORAGE_TYPE=stackdriver STACKDRIVER_PROJECT_ID=zipkin-demo STACKDRIVER_HTTP_LOGGING=basic\
    java \
    -Dloader.path='gcp.jar,gcp.jar!/lib' \
    -Dspring.profiles.active=gcp \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

### Testing

Once your storage is enabled, verify it is running:
```bash
$ curl -s localhost:9411/health|jq .zipkin.details.StackdriverStorage
{
  "status": "UP"
}
```
