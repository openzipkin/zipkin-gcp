FROM maven:3-jdk-8-onbuild

ENV JVM_GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled"
COPY docker-entrypoint.bash /
RUN chmod a+x /docker-entrypoint.bash
EXPOSE 9411

ENTRYPOINT ["/docker-entrypoint.bash"]
