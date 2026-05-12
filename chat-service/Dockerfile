# syntax=docker/dockerfile:1
FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY chat-service/ chat-service/
RUN --mount=type=cache,target=/root/.gradle \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m -Dorg.gradle.workers.max=1" \
    gradle :chat-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/chat-service/build/install/chat-service/ .
EXPOSE 8082
CMD ["bin/chat-service"]
