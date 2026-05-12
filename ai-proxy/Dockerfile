FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts gradle/ ./
COPY shared/ shared/
COPY ai-proxy/ ai-proxy/
RUN gradle :ai-proxy:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/ai-proxy/build/install/ai-proxy/ .
EXPOSE 8083
CMD ["bin/ai-proxy"]
