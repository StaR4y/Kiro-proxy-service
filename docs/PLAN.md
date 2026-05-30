# Project Plan

## Current Status

The project is in backend foundation phase.

Completed:

- Spring Boot WebFlux service skeleton.
- R2DBC MySQL application access.
- Flyway MySQL schema migration.
- Account, API key, model mapping, and request log tables.
- Admin APIs for accounts, API keys, model mappings, and logs.
- Local admin login with first-start `admin` bootstrap and random password logging.
- Admin user listing, creation, enable/disable, and password reset APIs.
- Startup guide logs for docs, health check, login, accounts, and first password flow.
- Batch Kiro account import API with optional account upsert.
- Basic startup/import helper scripts for local reverse-proxy operation.
- OpenAI-compatible `/v1/models`.
- OpenAI-compatible `/v1/chat/completions`.
- OpenAI Responses-style `/v1/responses`.
- API key creation with SHA-256 secret storage.
- API key credits limit and aggregate usage tracking.
- Per-key fixed-window rate limiting.
- Account pool selection with round-robin/sticky mode.
- Session affinity from headers/body session hints.
- Account suspension, quota exhaustion, and error cooldown state.
- Kiro upstream client with Reactor Netty connection pool.
- AWS event-stream parser for assistant content and usage metadata.
- OpenAPI UI at `/docs`.
- Documentation for API, configuration, architecture, and troubleshooting.
- Multi-module layout with API contracts in `api` and concrete server implementation in `service`.

Verified:

- `./gradlew test`
- `./gradlew build`
- local MySQL startup with Flyway migration
- `GET /health`
- `GET /v1/models`

## Design Principles

- Keep inbound and database work non-blocking where practical.
- Keep controllers thin; place orchestration in services.
- Store only durable state in MySQL; cache routing state for low-latency reads.
- Use atomic SQL updates for counters to avoid read-modify-write races.
- Keep Kiro protocol conversion isolated from account/API key management.
- Make the first service usable before adding every compatibility edge case.

## Known Gaps

- `stream=true` produces SSE after upstream completion, not true upstream chunk passthrough.
- Claude `/v1/messages` is not implemented.
- Expired Kiro access token refresh is not implemented.
- Dynamic Kiro model discovery is not implemented.
- Tool-call/tool-result conversion is only partial.
- Image, document, and file content conversion is not complete.
- Prompt cache controls are not fully implemented.
- Per-account outbound proxy URL is stored but not wired into WebClient dispatch.
- Rate limiting is in-memory and not distributed across multiple nodes.

## Milestone 1: API Parity

Goal: cover common client formats from `Kiro-account-manager`.

Tasks:

1. Implement Claude `/v1/messages`.
2. Complete tool-call and tool-result conversion.
3. Add image/document request conversion.
4. Add prompt cache control conversion.
5. Add dynamic `/v1/models` from Kiro upstream with cache TTL.
6. Add compatibility tests for OpenAI, Responses, and Claude payloads.

## Milestone 2: Account Reliability

Goal: make multi-account operation stable under real traffic.

Tasks:

1. Add token refresh workflow.
2. Prevent concurrent refresh for the same account.
3. Add manual token validation endpoint.
4. Add account-to-API-key binding.
5. Add group-based account selection.
6. Add account-bound outbound proxy dispatch.
7. Add quota reset reconciliation.

## Milestone 3: Streaming and Latency

Goal: reduce perceived latency for streaming clients.

Tasks:

1. Parse AWS event-stream chunks incrementally.
2. Flush OpenAI SSE chunks as upstream chunks arrive.
3. Add cancellation handling for disconnected clients.
4. Track first-token latency and total latency separately.
5. Add stream-specific error recovery behavior.

## Milestone 4: Operations

Goal: make the service easy to operate.

Tasks:

1. Add Testcontainers MySQL integration tests.
2. Add Dockerfile and docker-compose sample.
3. Add Prometheus metrics for route status, account state, upstream errors, and latency.
4. Add structured request IDs to logs.
5. Add migration recovery notes for production.
6. Add production profile that disables Swagger by default.

## Verification Checklist

For every feature change:

```bash
./gradlew test
./gradlew build
```

For startup changes:

```bash
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS kiro_proxy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
./gradlew :service:bootRun
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/v1/models
```

For proxy changes:

1. Create API key.
2. Add one valid Kiro account.
3. Call `/v1/chat/completions`.
4. Check `/admin/logs`.
5. Check account/API key counters.
