FROM gradle:9.8-jdk25-alpine AS build
# Keep caches outside the Gradle image's declared VOLUME for the legacy builder.
ENV GRADLE_USER_HOME=/opt/gradle-cache
WORKDIR /workspace
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar ./app.jar
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
