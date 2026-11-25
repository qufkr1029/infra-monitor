FROM gradle:9.8-jdk25-alpine AS build
ENV GRADLE_USER_HOME=/home/gradle/.gradle
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN --mount=type=cache,id=infra-monitor-gradle,target=/home/gradle/.gradle \
    gradle --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar ./app.jar
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
