FROM maven:3-jdk-8-onbuild

RUN mvn package

ENTRYPOINT ["java", "-jar", "/usr/src/app/target/server-1.0-SNAPSHOT.jar"]
