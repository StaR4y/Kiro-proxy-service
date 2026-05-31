# Account Import and Proxy Startup

This service becomes a reverse proxy as soon as the `service` module starts. There is no separate proxy worker.

## Start the Reverse Proxy

Create the database once:

```bash
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS kiro_proxy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Start with Gradle:

```bash
./scripts/start-proxy.sh
```

Start on another port:

```bash
SERVER_PORT=8081 ./scripts/start-proxy.sh
```

Start from the boot jar:

```bash
KIRO_RUN_MODE=jar ./scripts/start-proxy.sh
```

On first startup, read the generated `admin` password from the startup logs. The service prints it only once when the `admin_users` table is empty.

## Login as Admin

```bash
curl -X POST http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<startup-password>"}'
```

Export the returned `data.accessToken`:

```bash
export ADMIN_TOKEN="adm-..."
```

On first login, change the generated password before using other admin APIs:

```bash
curl -X POST http://127.0.0.1:8080/auth/password \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"oldPassword":"<startup-password>","newPassword":"<new-password>"}'
```

## Import One Account

Minimum payload:

```bash
curl -X POST http://127.0.0.1:8080/admin/accounts \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"email":"user@example.com","accessToken":"kiro-access-token","region":"us-east-1"}'
```

Only `accessToken` is required. `region` defaults to `us-east-1`.

## Batch Import Accounts

Edit the sample file:

```text
examples/accounts.import.json
```

Import it:

```bash
ADMIN_TOKEN="$ADMIN_TOKEN" ./scripts/import-accounts.sh
```

Import another file:

```bash
ADMIN_TOKEN="$ADMIN_TOKEN" ./scripts/import-accounts.sh /path/to/accounts.import.json
```

Request format. You can paste Kiro Account Manager JSON export directly:

```json
{
  "version": "kiro-account-manager",
  "exportedAt": 1760000000000,
  "upsert": true,
  "accounts": [
    {
      "id": "kiro-main",
      "email": "user@example.com",
      "idp": "BuilderId",
      "profileArn": "arn:aws:codewhisperer:us-east-1:638616132270:profile/AAAACCCCXXXX",
      "machineId": "optional-machine-id",
      "proxyUrl": "http://user:pass@host:port",
      "credentials": {
        "accessToken": "kiro-access-token",
        "refreshToken": "optional-refresh-token",
        "clientId": "optional-client-id",
        "clientSecret": "optional-client-secret",
        "region": "us-east-1",
        "authMethod": "IdC",
        "provider": "BuilderId"
      },
      "usage": {
        "limit": 1000000
      }
    }
  ],
  "groups": [],
  "tags": []
}
```

Import behavior:

- `upsert=false` or omitted: duplicate `accountId` entries are skipped.
- `upsert=true`: duplicate `accountId` entries update existing accounts.
- Kiro Account Manager `id` is treated as `accountId`.
- Missing `id/accountId`: the service generates one, so the item is always created as a new account.
- `credentials.accessToken` or top-level `accessToken` is required for import. Accounts with refresh credentials are refreshed in the background every hour by default; if Kiro still reports an invalid bearer token, the service refreshes once immediately and retries. IdC/BuilderId accounts also need `clientId` and `clientSecret`.
- Each item returns `CREATED`, `UPDATED`, `SKIPPED`, or `FAILED`.

## Test Kiro Accounts

```bash
curl -X POST http://127.0.0.1:8080/admin/accounts/test \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "onlyEnabled": true,
    "model": "simple-task",
    "prompt": "Reply OK in one short sentence.",
    "maxConcurrency": 3
  }'
```

Add `accountIds` to test specific accounts only:

```json
{
  "accountIds": ["kiro-main"],
  "onlyEnabled": true
}
```

## Create a Proxy API Key

```bash
curl -X POST http://127.0.0.1:8080/admin/api-keys \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"default","creditsLimit":1000}'
```

Save the returned `data.key`, then call the proxy with it:

```bash
export KIRO_PROXY_KEY="sk-..."

curl http://127.0.0.1:8080/v1/models \
  -H "Authorization: Bearer $KIRO_PROXY_KEY"
```

OpenAI-compatible clients should use:

```text
Base URL: http://127.0.0.1:8080/v1
API Key:  sk-...
```

Persist client API configuration in a console profile:

macOS:

```bash
PROFILE="${ZDOTDIR:-$HOME}/.zshrc"
cat >> "$PROFILE" <<'EOF'
export OPENAI_BASE_URL='http://127.0.0.1:8080/v1'
export OPENAI_API_KEY='sk-REPLACE_ME'
export KIRO_PROXY_PORT='8080'
EOF
export OPENAI_BASE_URL='http://127.0.0.1:8080/v1'
export OPENAI_API_KEY='sk-REPLACE_ME'
export KIRO_PROXY_PORT='8080'
```

Linux:

```bash
PROFILE="$HOME/.bashrc"; [ -n "$ZSH_VERSION" ] && PROFILE="${ZDOTDIR:-$HOME}/.zshrc"
cat >> "$PROFILE" <<'EOF'
export OPENAI_BASE_URL='http://127.0.0.1:8080/v1'
export OPENAI_API_KEY='sk-REPLACE_ME'
export KIRO_PROXY_PORT='8080'
EOF
export OPENAI_BASE_URL='http://127.0.0.1:8080/v1'
export OPENAI_API_KEY='sk-REPLACE_ME'
export KIRO_PROXY_PORT='8080'
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force (Split-Path $PROFILE) | Out-Null
@'
$env:OPENAI_BASE_URL = 'http://127.0.0.1:8080/v1'
$env:OPENAI_API_KEY = 'sk-REPLACE_ME'
$env:KIRO_PROXY_PORT = '8080'
'@ | Add-Content -Path $PROFILE -Encoding UTF8
$env:OPENAI_BASE_URL = 'http://127.0.0.1:8080/v1'
$env:OPENAI_API_KEY = 'sk-REPLACE_ME'
$env:KIRO_PROXY_PORT = '8080'
```

Chat completion test:

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $KIRO_PROXY_KEY" \
  -d '{
    "model": "claude-sonnet-4.5",
    "messages": [
      {"role": "user", "content": "Say hello in one sentence."}
    ],
    "max_tokens": 256
  }'
```
