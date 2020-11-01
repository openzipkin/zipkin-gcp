#
# Copyright 2016-2020 The OpenZipkin Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

ARG java_version
ARG zipkin_version

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

FROM ghcr.io/openzipkin/java:${java_version} as install

WORKDIR /install

# Conditions aren't supported in Dockerfile instructions, so we copy source even if it isn't used.
COPY --from=scratch /code /code

# This will either be a snapshot like 2.4.5-SNAPSHOT or a real version ex. "2.4.5"
ARG release_version
ENV RELEASE_VERSION=$release_version
# When true, main images reuse module jars in the context root
ARG release_from_maven_build=false
ENV RELEASE_FROM_MAVEN_BUILD=$release_from_maven_build
ARG artifacts
RUN GROUP_ID=io.zipkin.gcp ARTIFACTS=$artifacts /code/docker/bin/install_zipkin_modules

FROM ghcr.io/openzipkin/zipkin:${zipkin_version} as zipkin-gcp
LABEL org.opencontainers.image.description="Zipkin with Google Cloud Trace storage on OpenJDK and Alpine Linux"

COPY --from=install /install/ /zipkin/

# Readback is currently not supported
ENV QUERY_ENABLED false

ARG modules
ENV MODULE_OPTS="-Dloader.path=${modules} -Dspring.profiles.active=${modules}"
