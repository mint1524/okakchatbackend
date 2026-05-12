# syntax=docker/dockerfile:1
FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY auth-service/ auth-service/
RUN --mount=type=cache,target=/root/.gradle \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m -Dorg.gradle.workers.max=1" \
    gradle :auth-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/build/install/auth-service/ .
EXPOSE 8081
CMD ["bin/auth-service"]
