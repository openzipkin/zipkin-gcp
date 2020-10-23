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

ARG zipkin_version=2.22.0

# We copy files from the context into a scratch container first to avoid a problem where docker and
# docker-compose don't share layer hashes https://github.com/docker/compose/issues/883 normally.
# COPY --from= works around the issue.
FROM scratch as scratch

COPY . /code/

FROM openzipkin/zipkin-builder as install

WORKDIR /install

# Conditions aren't supported in Dockerfile instructions, so we copy source even if it isn't used.
COPY --from=scratch /code /code

# This will either be "master" or a real version ex. "2.4.5"
ARG release_version=master
ENV RELEASE_VERSION=${release_version}
COPY docker/install.sh /tmp/
RUN /tmp/install.sh && rm /tmp/install.sh

FROM openzipkin/zipkin:$zipkin_version

COPY --from=install /install/ /zipkin/

# Readback is currently not supported
ENV QUERY_ENABLED false

ENV MODULE_OPTS="-Dloader.path=stackdriver -Dspring.profiles.active=stackdriver"
