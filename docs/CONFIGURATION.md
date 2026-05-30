# Configuration

All runtime configuration is read from environment variables through `service/src/main/resources/application.yml`.

## Server

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port. |
| `SPRING_CODEC_MAX_IN_MEMORY_SIZE` | `16MB` | Max in-memory body size for WebFlux codecs. |

## MySQL

The service uses both JDBC and R2DBC:

- JDBC is used by Flyway migrations.
- R2DBC is used by application queries.

| Variable | Default | Description |
| --- | --- | --- |
| `MYSQL_HOST` | `127.0.0.1` | MySQL host. |
| `MYSQL_PORT` | `3306` | MySQL port. |
| `MYSQL_DATABASE` | `kiro_proxy` | Database name. |
| `MYSQL_USERNAME` | `root` | MySQL username. |
| `MYSQL_PASSWORD` | empty | MySQL password. |
| `MYSQL_POOL_INITIAL_SIZE` | `8` | R2DBC pool initial size. |
| `MYSQL_POOL_MAX_SIZE` | `80` | R2DBC pool max size. |
| `FLYWAY_ENABLED` | `true` | Run schema migration on startup. |

Create the database before starting the service:

```sql
CREATE DATABASE IF NOT EXISTS kiro_proxy
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## Proxy Runtime

| Variable | Default | Description |
| --- | --- | --- |
| `KIRO_ADMIN_TOKEN` | empty | Optional legacy admin token for automation or emergency access. Local admin login is available even when this is empty. |
| `KIRO_ADMIN_SESSION_TTL` | `12h` | Local admin login session lifetime. |
| `KIRO_ALLOW_ANONYMOUS_PROXY` | `false` | Allow proxy calls without API key. Keep `false` in production. |
| `KIRO_REQUEST_TIMEOUT` | `90s` | Upstream Kiro request timeout. |
| `KIRO_MAX_RETRIES` | `2` | Upstream retry count for recoverable errors. |
| `KIRO_RATE_LIMIT_PER_MINUTE` | `120` | Fixed-window per API key rate limit. `0` disables rate limit. |
| `KIRO_ACCOUNT_SELECTION_STRATEGY` | `round_robin` | `round_robin` or `sticky`. |
| `KIRO_SESSION_AFFINITY_ENABLED` | `true` | Route same session hint to same account when possible. |
| `KIRO_SESSION_AFFINITY_TTL` | `10m` | Session affinity TTL. |
| `KIRO_BASE_COOLDOWN` | `60s` | Base cooldown for recoverable account errors. |
| `KIRO_QUOTA_RESET` | `1h` | Planned quota reset duration. |
| `KIRO_PROBABILISTIC_RETRY` | `0.1` | Chance to probe a cooling account early. |
| `KIRO_MAX_REQUEST_BODY_BYTES` | `10485760` | Max inbound request content length. |
| `KIRO_PREFERRED_ENDPOINT` | `codewhisperer` | Preferred upstream endpoint: `codewhisperer`, `amazonq`, or `amazonq-cli`. |

## Recommended Local Environment

```bash
export SERVER_PORT=8080
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=kiro_proxy
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=
export MYSQL_POOL_INITIAL_SIZE=8
export MYSQL_POOL_MAX_SIZE=80
export KIRO_ADMIN_SESSION_TTL=12h
# Optional legacy token. Prefer POST /auth/login for normal operation.
# export KIRO_ADMIN_TOKEN=change-me
export KIRO_ALLOW_ANONYMOUS_PROXY=false
export KIRO_RATE_LIMIT_PER_MINUTE=120

./gradlew :service:bootRun
```

On the first successful startup, the service creates a local admin account if no admin user exists:

```text
username: admin
password: <random password printed in startup logs once>
```

Use `POST /auth/login` to exchange that password for an `adm-...` session token, then call `POST /auth/password`.
Other admin APIs are blocked for first-login sessions until the password is changed.

## Recommended Production Defaults

- Save the first startup password immediately and change it with `POST /auth/password`.
- Use local admin login for normal administration.
- Set `KIRO_ADMIN_TOKEN` only if you need a static legacy token for automation or emergency access.
- Keep `KIRO_ALLOW_ANONYMOUS_PROXY=false`.
- Put the service behind a reverse proxy or gateway with TLS.
- Use a dedicated MySQL user with permissions only on the service database.
- Increase `MYSQL_POOL_MAX_SIZE` based on database capacity, not just request traffic.
- Monitor `/actuator/health` and `/actuator/prometheus`.
- Disable Swagger/OpenAPI in exposed production environments if needed:

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## Account Selection

`round_robin`:

- Successful requests advance to the next account.
- Better for distributing load across accounts.

`sticky`:

- Keeps using the same selected account when possible.
- Better for cache locality and session continuity.

Session affinity is applied before normal selection when a session hint is present.
