FROM maven:3-jdk-8-onbuild

ENTRYPOINT ["java", "-jar", "/usr/src/app/target/server-*.jar"]
