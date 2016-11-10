#!/usr/bin/env bash
set -ea

# set jvm gc parameters
export JVM_HEAP_SIZE=${JVM_HEAP_SIZE:-256m}
export JVM_GC_OPTS="$JVM_GC_OPTS -Xms${JVM_HEAP_SIZE} -Xmx${JVM_HEAP_SIZE}"
export JAVA_OPTS="$JAVA_OPTS $JVM_GC_OPTS"

exec java $JAVA_OPTS "$@" -jar /usr/src/app/server/target/server-*.jar
