#
# Copyright 2016-2019 The OpenZipkin Authors
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

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

FROM openzipkin/zipkin-builder as built

COPY --from=scratch /code /code

WORKDIR /code

RUN mvn -B --no-transfer-progress package -DskipTests=true -pl module/storage-stackdriver -am

WORKDIR /zipkin-gcp

RUN cp /code/module/storage-stackdriver/target/zipkin-module-storage-stackdriver-*-module.jar stackdriver.jar && \
  echo > .stackdriver_profile && \
  (mkdir stackdriver && cd stackdriver && jar -xf ../stackdriver.jar) && \
  rm stackdriver.jar

FROM openzipkin/zipkin:master
MAINTAINER Zipkin "https://zipkin.io/"

COPY --from=built /zipkin-gcp/ /zipkin/

# Readback is currently not supported
ENV QUERY_ENABLED false

ENV MODULE_OPTS="-Dloader.path=stackdriver -Dspring.profiles.active=stackdriver"
