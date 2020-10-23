#!/bin/sh
#
# Copyright 2015-2020 The OpenZipkin Authors
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

set -eux

# This script decides based on $RELEASE_VERSION whether to build or download the binaries we need.
if [ "$RELEASE_VERSION" = "master" ]
then
  echo "*** Building from source..."
  # Use the same command as we suggest in zipkin-server/README.md
  #  * Uses mvn not ./mvnw to reduce layer size: we control the Maven version in Docker
  (cd /code; mvn -T1C -q --batch-mode -DskipTests -Dlicense.skip=true --also-make -pl module/storage-stackdriver clean package)
  cp /code/module/storage-stackdriver/target/zipkin-module-storage-stackdriver-*-module.jar stackdriver.jar
else
  echo "*** Downloading from Maven...."
  # This prefers Maven central, but uses our release repository if it isn't yet synced.
  mvn --batch-mode org.apache.maven.plugins:maven-dependency-plugin:get \
      -DremoteRepositories=bintray::::https://dl.bintray.com/openzipkin/maven -Dtransitive=false \
      -Dartifact=io.zipkin.gcp:zipkin-module-storage-stackdriver:${RELEASE_VERSION}:jar:module

  # Copy the module jar from the local Maven repository
  find ~/.m2/repository -name zipkin-module-storage-stackdriver-${RELEASE_VERSION}-module.jar -exec cp {} stackdriver.jar \;
fi

# sanity check!
test -f stackdriver.jar

(mkdir stackdriver && cd stackdriver && jar -xf ../stackdriver.jar) && rm stackdriver.jar
