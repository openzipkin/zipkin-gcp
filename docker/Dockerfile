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

FROM openzipkin/zipkin-builder

WORKDIR /code
COPY . /code
RUN mvn -B --no-transfer-progress package -DskipTests=true -pl module/storage-stackdriver -am

RUN \
  cd /code && mkdir -p /zipkin-gcp/stackdriver && cp module/storage-stackdriver/target/zipkin-module-storage-stackdriver-*-module.jar /zipkin-gcp/stackdriver && cd /zipkin-gcp/stackdriver && jar xf *.jar && rm *.jar

FROM openzipkin/zipkin:master
MAINTAINER Zipkin "https://zipkin.io/"

COPY --from=0 /zipkin-gcp/ /zipkin/lib/

RUN echo > .stackdriver_profile

ENV MODULE_OPTS="-Dloader.path=lib/stackdriver -Dspring.profiles.active=stackdriver"
