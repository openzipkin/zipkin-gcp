## zipkin-gcp Docker image

To build a zipkin-gcp Docker image, in the top level of the repository, run something
like

```bash
$ docker build -t openzipkin/zipkin-gcp:test -f docker/Dockerfile .
```

### Dockerfile migration

We are currently migrating the Docker configuration from https://github.com/openzipkin/docker-zipkin-gcp.
If making any changes here, make sure to also reflect them there.
