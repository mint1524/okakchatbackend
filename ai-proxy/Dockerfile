# syntax=docker/dockerfile:1
FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY ai-proxy/ ai-proxy/
RUN --mount=type=cache,target=/root/.gradle \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m -Dorg.gradle.workers.max=1" \
    gradle :ai-proxy:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/ai-proxy/build/install/ai-proxy/ .
EXPOSE 8083
CMD ["bin/ai-proxy"]
