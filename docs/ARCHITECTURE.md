# Architecture

## Module Layout

```text
api/src/main/java/xyz/star4y/kiroproxy
├── admin        Local admin login and admin user API contracts
├── account      Account admin API contract and DTOs
├── apikey       API key admin API contract and DTOs
├── common       Shared API response envelope
├── model        Model mapping API contract and DTOs
├── proxy        Public proxy API contract
└── stats        Admin log API contract and DTOs

service/src/main/java/xyz/star4y/kiroproxy
├── admin        Local admin users, password hashing, sessions, and startup guide logs
├── account      Account persistence, admin API implementation, and account pool selection
├── apikey       API key generation, hashing, auth, and usage accounting
├── common       Exceptions, admin auth, global error handling, utilities
├── config       Spring Boot, WebFlux, WebClient, OpenAPI, scheduling config
├── model        Model mapping persistence and implementation
├── proxy        OpenAI-compatible API implementation, Kiro payload conversion, upstream client
└── stats        Request logs and admin log implementation
```

## Request Flow

```text
Client
  |
  | /v1/chat/completions or /v1/responses
  v
ProxyController
  |
  v
ProxyFacade
  |
  +-- ApiKeyService.authenticate()
  |
  +-- RateLimiterService.check()
  |
  +-- ModelMappingService.apply()
  |
  +-- AccountPoolService.selectAccount()
  |
  +-- KiroPayloadFactory.fromOpenAiChat()
  |
  +-- KiroUpstreamClient.complete()
  |
  +-- AwsEventStreamParser.parse()
  |
  +-- ApiKeyService.recordUsage()
  +-- AccountPoolService.recordSuccess()/recordError()
  +-- StatsService.log()
  |
  v
OpenAI-compatible response
```

## High-Concurrency Choices

- WebFlux handles inbound requests without servlet thread-per-request blocking.
- R2DBC is used for application database access.
- Flyway runs schema migration through JDBC during startup only.
- Reactor Netty connection pooling is used for Kiro upstream calls.
- Account and model mapping rules are cached in memory and refreshed periodically.
- Usage counters are written with atomic SQL updates.
- Rate limiting uses in-memory fixed-window buckets keyed by API key.

## Persistent State

Tables created by Flyway migrations:

| Table | Purpose |
| --- | --- |
| `proxy_accounts` | Kiro account credentials, metadata, quota and suspension state. |
| `api_keys` | API key hashes, status, limits, and aggregate usage. |
| `api_key_usage_daily` | Daily API key usage summary. |
| `model_mappings` | Model alias/replacement/load-balancing rules. |
| `request_logs` | Request history and latency/error tracking. |
| `admin_users` | Local admin users, password hashes, roles, and first-login state. |
| `flyway_schema_history` | Flyway migration history. |

## Account Pool Behavior

An account is skipped when:

- `enabled=false`
- `suspendedAt` is set
- quota is exhausted and `quotaResetAt` is still in the future
- recoverable failures are still in cooldown and probabilistic retry does not trigger

Recoverable upstream status codes:

- `402`
- `403`
- `429`

These increment account error state. Other upstream failures are treated as fatal request errors and do not penalize the account.

## Session Affinity

The service recognizes these headers:

- `x-claude-code-session-id`
- `x-opencode-session`
- `x-session-affinity`
- `x-conversation-id`

It also recognizes these request fields:

- `conversation_id`
- `conversationId`
- `thread_id`
- `session_id`

The affinity key is prefixed by API key ID, so different API keys do not share routing affinity.

## Upstream Kiro Call

`KiroUpstreamClient` sends a Kiro-compatible JSON payload to the configured upstream endpoint with:

- `Authorization: Bearer <account.accessToken>`
- Kiro-like `user-agent`
- Kiro-like `x-amz-user-agent`
- `x-amzn-kiro-agent-mode`
- AWS SDK invocation headers

`AwsEventStreamParser` parses AWS event-stream style responses and extracts:

- assistant text
- input tokens
- output tokens
- cache read/write tokens
- metering credits

## Current Boundaries

The current code is intentionally a backend-first foundation, not a full byte-for-byte rewrite of the Electron project.

Implemented:

- OpenAI-compatible text chat path
- Responses-to-chat conversion
- Basic function tool schema conversion
- Account failover state
- API key usage and limits

Not complete yet:

- True streaming passthrough from upstream chunks
- Claude `/v1/messages`
- Kiro token refresh
- Full tool-call/tool-result parity
- Image/document request conversion
- Dynamic model discovery from Kiro
- Distributed rate limiting for multi-node deployments
