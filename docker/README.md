## zipkin-gcp Docker image

This repository contains the Docker build definition for `zipkin-gcp`.

This layers Google Cloud Platform support on the base zipkin docker image.

Currently, this adds Stackdriver Trace storage

## Running

By default, this image will search for credentials in a json file at `$GOOGLE_APPLICATION_CREDENTIALS`

If you want to try Zipkin against Stackdriver, the easiest start is to share
your credentials with Zipkin's docker image.

```bash
# Note: this is mirrored as ghcr.io/openzipkin/zipkin-gcp
$ docker run -d -p 9411:9411 --name zipkin-gcp \
  -e STORAGE_TYPE=stackdriver \
  -e GOOGLE_APPLICATION_CREDENTIALS=/zipkin/.gcp/credentials.json \
  -e STACKDRIVER_PROJECT_ID=your_project \
  -v $HOME/.gcp:/zipkin/.gcp:ro \
  openzipkin/zipkin-gcp
```

## Configuration

Configuration is via environment variables, defined [here](../README.md).

In docker, the following can also be set:

    * `JAVA_OPTS`: Use to set java arguments, such as heap size or trust store location.

### Stackdriver

Stackdriver Configuration variables are detailed [here](../module/README.md#configuration).

## Building

To build a zipkin-gcp Docker image from source, in the top level of the repository, run:


```bash
$ build-bin/docker/docker_build openzipkin/zipkin-gcp:test
```

To build from a published version, run this instead:

```bash
$ build-bin/docker/docker_build openzipkin/zipkin-gcp:test 0.18.1
```

