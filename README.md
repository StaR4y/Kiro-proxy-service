# Kiro Proxy Service

Kiro Proxy Service is a Spring Boot + MySQL backend service for exposing Kiro accounts through OpenAI-compatible API endpoints.

The current implementation focuses on a high-concurrency service foundation: WebFlux, R2DBC MySQL, connection pooling, cached routing state, API key accounting, request rate limits, account failover, and OpenAI-compatible proxy APIs.

## Features

- OpenAI-compatible `GET /v1/models`
- OpenAI-compatible `POST /v1/chat/completions`
- OpenAI Responses-style `POST /v1/responses`
- Admin APIs for accounts, API keys, model mappings, and request logs
- Local admin login and admin user management APIs
- MySQL schema migration with Flyway
- Non-blocking MySQL access with R2DBC
- Reactor Netty upstream HTTP connection pool
- API key hashing, usage accounting, credits limit, and per-key rate limiting
- Multi-account routing with round-robin or sticky strategy
- Session affinity by conversation/session headers
- Account quota, suspension, and failure cooldown state
- OpenAPI UI at `/docs`

## Requirements

- Java 17
- MySQL reachable by JDBC and R2DBC
- Gradle wrapper included in this repository

## Quick Start

Start MySQL and create the database:

```sql
CREATE DATABASE IF NOT EXISTS kiro_proxy
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Run the service:

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=kiro_proxy
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=
# Optional legacy admin token. Normal admin access uses POST /auth/login.
# export KIRO_ADMIN_TOKEN=change-me

./gradlew :service:bootRun
```

Or use the startup helper:

```bash
./scripts/start-proxy.sh
```

Check the service:

```bash
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/v1/models
```

On first startup, the service creates a local management account:

```text
username: admin
password: <random password printed in startup logs once>
```

Login and use the returned `data.accessToken` for admin APIs:

```bash
curl -X POST http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<startup-password>"}'
```

If this is the first login, change the password before using other admin APIs:

```bash
curl -X POST http://127.0.0.1:8080/auth/password \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer adm-REPLACE_ME' \
  -d '{"oldPassword":"<startup-password>","newPassword":"<new-password>"}'
```

Open the interactive API docs:

```text
http://127.0.0.1:8080/docs
```

## Minimal Setup Flow

Create an API key:

```bash
ADMIN_TOKEN="adm-REPLACE_ME"

curl -X POST http://127.0.0.1:8080/admin/api-keys \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"default","creditsLimit":1000}'
```

Add a Kiro account:

```bash
curl -X POST http://127.0.0.1:8080/admin/accounts \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "email": "user@example.com",
    "accessToken": "kiro-access-token",
    "region": "us-east-1",
    "authMethod": "idc",
    "provider": "BuilderID"
  }'
```

Batch import Kiro accounts:

```bash
ADMIN_TOKEN="$ADMIN_TOKEN" ./scripts/import-accounts.sh examples/accounts.import.json
```

Call the proxy with the generated `sk-kiro-...` key:

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-kiro-REPLACE_ME' \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Say hello in one sentence."}
    ],
    "max_tokens": 256
  }'
```

## Documentation

- [API Reference](docs/API.md)
- [Configuration](docs/CONFIGURATION.md)
- [Account Import and Proxy Startup](docs/OPERATIONS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Plan](docs/PLAN.md)

## Module Layout

- `api`: shared request/response models and Spring API contracts.
- `service`: concrete Spring Boot server implementation, MySQL persistence, account pool, API key accounting, and Kiro reverse-proxy logic.

## Current Limitations

- `stream=true` returns an SSE response after upstream completion. True upstream chunk passthrough is planned.
- Claude `/v1/messages` compatibility is not implemented yet.
- Token refresh for expired Kiro access tokens is planned.
- Dynamic Kiro model discovery is planned; `/v1/models` currently returns a static compatibility list.
- Tool, image, document, and prompt-cache conversion is partial and will be expanded.
