FROM openjdk:17.0.1-jdk-slim
#ARG jar-file=target/*.jar
ARG jar-file=build/libs/library-demo-0.0.1-SNAPSHOT.jar
COPY ./target/library-demo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]