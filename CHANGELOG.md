# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - 2026-05-12
### Added
- Three Ktor microservices: auth-service (port 8081), chat-service (8082), ai-proxy (8083)
- PostgreSQL 16 with Flyway migrations
- JWT authentication with 15-minute access tokens and 30-day rotating refresh tokens
- Email verification via OKAK Mail v2
- Subscription management (free/pro plans) with configurable per-plan and per-user limits
- Admin panel API: user management, subscription grants, limit overrides, full audit log
- OpenAI-compatible streaming proxy via WebSocket with AES-256-GCM API key encryption
- Docker Compose stack for Dokploy deployment
- Nginx API gateway with WebSocket upgrade support
