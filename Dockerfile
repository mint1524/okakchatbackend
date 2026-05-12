# syntax=docker/dockerfile:1

# ── Stage 1: build all three services in one JVM process ──────────────────────
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY auth-service/ auth-service/
COPY chat-service/ chat-service/
COPY ai-proxy/ ai-proxy/
RUN --mount=type=cache,target=/root/.gradle \
    GRADLE_OPTS="-Xmx512m -Dorg.gradle.workers.max=1 -Dkotlin.daemon.jvm.options=-Xmx768m" \
    gradle :auth-service:installDist :chat-service:installDist :ai-proxy:installDist \
    --no-daemon

# ── Stage 2: auth-service runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS auth-service
WORKDIR /app
COPY --from=builder /app/auth-service/build/install/auth-service/ .
EXPOSE 8081
CMD ["bin/auth-service"]

# ── Stage 3: chat-service runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS chat-service
WORKDIR /app
COPY --from=builder /app/chat-service/build/install/chat-service/ .
EXPOSE 8082
CMD ["bin/chat-service"]

# ── Stage 4: ai-proxy runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS ai-proxy
WORKDIR /app
COPY --from=builder /app/ai-proxy/build/install/ai-proxy/ .
EXPOSE 8083
CMD ["bin/ai-proxy"]
