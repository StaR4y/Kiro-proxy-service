# API Reference

Base URL:

```text
http://127.0.0.1:8080
```

Interactive OpenAPI UI:

```text
GET /docs
```

OpenAPI JSON:

```text
GET /v3/api-docs
```

## Authentication

Proxy APIs require an API key unless `KIRO_ALLOW_ANONYMOUS_PROXY=true`.

Use either header:

```http
Authorization: Bearer sk-kiro-...
```

```http
X-Api-Key: sk-kiro-...
```

Admin APIs support two authentication modes.

Mode 1: login with a local admin user and pass the returned admin session token:

```http
Authorization: Bearer adm-...
```

Mode 2: legacy environment token, available when `KIRO_ADMIN_TOKEN` is configured:

```http
X-Admin-Token: change-me
```

The admin token can also be passed as:

```http
Authorization: Bearer change-me
```

On first startup, if no local admin user exists, the service creates:

```text
username: admin
password: <random password printed in startup logs once>
```

The generated password is not stored in plain text and is not printed again.

If `user.firstLogin=true` after login, the session can only call `POST /auth/password`.
Other admin APIs return `403 FIRST_LOGIN_PASSWORD_CHANGE_REQUIRED` until the password is changed.

## Response Envelope

Admin APIs return a common envelope:

```json
{
  "success": true,
  "data": {},
  "timestamp": "2026-05-30T15:00:00Z"
}
```

Error response:

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Missing API key"
  },
  "timestamp": "2026-05-30T15:00:00Z"
}
```

Proxy APIs return OpenAI-compatible bodies where possible.

## Proxy APIs

### Health

```http
GET /health
GET /v1/health
```

Response:

```json
{
  "status": "ok",
  "service": "kiro-proxy-service",
  "uptime_ms": 12345
}
```

### List Models

```http
GET /v1/models
GET /models
```

Response:

```json
{
  "object": "list",
  "data": [
    {
      "id": "claude-sonnet-4.5",
      "object": "model",
      "created": 0,
      "owned_by": "kiro",
      "name": "Claude Sonnet 4.5",
      "context_length": 200000,
      "max_output_tokens": 32000
    }
  ]
}
```

Current static model IDs:

- `claude-sonnet-4.5`
- `claude-sonnet-4`
- `claude-haiku-4.5`
- `gpt-4o`

### Chat Completions

```http
POST /v1/chat/completions
POST /chat/completions
```

Request:

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "You are a concise assistant."
    },
    {
      "role": "user",
      "content": "Say hello in one sentence."
    }
  ],
  "temperature": 0.7,
  "top_p": 1,
  "max_tokens": 1024,
  "stream": false,
  "conversation_id": "optional-session-id"
}
```

Supported request fields in this phase:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `model` | string | yes | Model mapping is applied before upstream request. |
| `messages` | array | yes | OpenAI-style `system`, `user`, `assistant`, `tool` roles. |
| `temperature` | number | no | Forwarded to Kiro inference config. |
| `top_p` | number | no | Forwarded to Kiro inference config. |
| `max_tokens` | number | no | Forwarded as `maxTokens`. |
| `stream` | boolean | no | Returns SSE format after upstream completion. |
| `conversation_id` | string | no | Used for stable conversation/session affinity. |
| `conversationId` | string | no | Same purpose as `conversation_id`. |
| `thread_id` | string | no | Used for session affinity. |
| `session_id` | string | no | Used for session affinity. |
| `tools` | array | no | Basic function tool schema conversion. |
| `thinking` | object | no | Forwarded as additional model request field. |

Supported session affinity headers:

- `x-claude-code-session-id`
- `x-opencode-session`
- `x-session-affinity`
- `x-conversation-id`

Non-stream response:

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "created": 1770000000,
  "model": "claude-sonnet-4.5",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30,
    "prompt_tokens_details": {
      "cached_tokens": 0
    },
    "completion_tokens_details": {
      "reasoning_tokens": 0
    }
  }
}
```

SSE response when `stream=true`:

```text
data: {"id":"chatcmpl-...","object":"chat.completion.chunk",...}

data: {"id":"chatcmpl-...","object":"chat.completion.chunk",...}

data: [DONE]
```

Important: `stream=true` currently writes a valid SSE response after the upstream response is complete. True upstream chunk passthrough is planned.

curl example:

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-kiro-REPLACE_ME' \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Say hello."}
    ],
    "max_tokens": 256
  }'
```

### Responses

```http
POST /v1/responses
POST /responses
```

String input request:

```json
{
  "model": "gpt-4o",
  "input": "Say hello.",
  "instructions": "You are concise.",
  "temperature": 0.7,
  "top_p": 1,
  "max_output_tokens": 1024,
  "stream": false,
  "previous_response_id": "optional-response-id"
}
```

Array input request:

```json
{
  "model": "gpt-4o",
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "input_text",
          "text": "Say hello."
        }
      ]
    }
  ]
}
```

Response:

```json
{
  "id": "resp-...",
  "object": "response",
  "created_at": 1770000000,
  "model": "claude-sonnet-4.5",
  "output": [
    {
      "type": "message",
      "id": "msg-...",
      "role": "assistant",
      "content": [
        {
          "type": "output_text",
          "text": "Hello."
        }
      ]
    }
  ],
  "usage": {
    "input_tokens": 10,
    "output_tokens": 20,
    "total_tokens": 30
  }
}
```

## Admin APIs

### Login

```http
POST /auth/login
```

Request:

```json
{
  "username": "admin",
  "password": "startup-random-password"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "adm-...",
    "expiresAt": "2026-05-31T04:00:00Z",
    "user": {
      "userId": "...",
      "username": "admin",
      "displayName": "Default Administrator",
      "role": "ADMIN",
      "enabled": true,
      "firstLogin": true
    }
  },
  "timestamp": "2026-05-30T16:00:00Z"
}
```

### Change Own Password

```http
POST /auth/password
```

This endpoint is allowed for first-login admin sessions.

Request:

```json
{
  "oldPassword": "current-password",
  "newPassword": "new-strong-password"
}
```

### List Admin Users

```http
GET /admin/users
```

### Create Admin User

```http
POST /admin/users
```

Request:

```json
{
  "username": "operator",
  "displayName": "Operator",
  "role": "ADMIN",
  "password": "optional-password"
}
```

When `password` is omitted, a random password is generated and returned once.

### Update Admin User

```http
PATCH /admin/users/{userId}
```

Request:

```json
{
  "displayName": "New Name",
  "role": "ADMIN",
  "enabled": true
}
```

### Reset Admin User Password

```http
POST /admin/users/{userId}/reset-password
```

Returns a new random password once.

### List Accounts

```http
GET /admin/accounts
```

curl:

```bash
curl http://127.0.0.1:8080/admin/accounts \
  -H 'Authorization: Bearer adm-...'
```

### Create Account

```http
POST /admin/accounts
```

Request:

```json
{
  "accountId": "optional-stable-id",
  "email": "user@example.com",
  "accessToken": "kiro-access-token",
  "refreshToken": "optional-refresh-token",
  "clientId": "optional-client-id",
  "clientSecret": "optional-client-secret",
  "region": "us-east-1",
  "authMethod": "idc",
  "provider": "BuilderID",
  "profileArn": "arn:aws:codewhisperer:us-east-1:638616132270:profile/AAAACCCCXXXX",
  "machineId": "optional-machine-id",
  "proxyUrl": "http://user:pass@host:port",
  "quotaLimit": 1000000
}
```

Required fields:

- `accessToken`

Notes:

- `accountId` is generated when omitted.
- `region` defaults to `us-east-1` when omitted.
- `proxyUrl` is stored but account-bound outbound proxy wiring is not complete in this phase.

### Batch Import Accounts

```http
POST /admin/accounts/import
```

Request:

```json
{
  "upsert": true,
  "accounts": [
    {
      "accountId": "kiro-main",
      "email": "user@example.com",
      "accessToken": "kiro-access-token",
      "refreshToken": "optional-refresh-token",
      "region": "us-east-1",
      "authMethod": "idc",
      "provider": "BuilderID",
      "profileArn": "arn:aws:codewhisperer:us-east-1:638616132270:profile/AAAACCCCXXXX",
      "machineId": "optional-machine-id",
      "proxyUrl": "http://user:pass@host:port",
      "quotaLimit": 1000000
    }
  ]
}
```

Response:

```json
{
  "success": true,
  "data": {
    "total": 1,
    "created": 1,
    "updated": 0,
    "skipped": 0,
    "failed": 0,
    "items": [
      {
        "index": 0,
        "accountId": "kiro-main",
        "email": "user@example.com",
        "status": "CREATED",
        "message": "Account created"
      }
    ]
  },
  "timestamp": "2026-05-30T16:00:00Z"
}
```

Import behavior:

- `upsert=false` or omitted skips duplicate `accountId` entries.
- `upsert=true` updates existing rows with the same `accountId`.
- Missing `accountId` creates a new generated account ID.
- Each item returns `CREATED`, `UPDATED`, `SKIPPED`, or `FAILED`.

### Update Account

```http
PATCH /admin/accounts/{accountId}
```

All fields are optional:

```json
{
  "email": "new@example.com",
  "accessToken": "new-token",
  "refreshToken": "new-refresh-token",
  "region": "us-east-1",
  "authMethod": "idc",
  "provider": "BuilderID",
  "profileArn": "arn:aws:codewhisperer:us-east-1:...",
  "machineId": "machine-id",
  "proxyUrl": "http://user:pass@host:port",
  "enabled": true,
  "quotaUsed": 0,
  "quotaLimit": 1000000,
  "quotaResetAt": "2026-05-31T00:00:00Z"
}
```

### Suspend Account

```http
POST /admin/accounts/{accountId}/suspend
```

Request:

```json
{
  "reason": "TEMPORARILY_SUSPENDED",
  "message": "blocked by upstream"
}
```

### Reset Account State

```http
POST /admin/accounts/{accountId}/reset
```

Clears:

- `errorCount`
- `quotaExhaustedAt`
- `suspendedAt`
- `suspendReason`
- `suspendMessage`

The account is set back to `enabled=true`.

### Delete Account

```http
DELETE /admin/accounts/{accountId}
```

### List API Keys

```http
GET /admin/api-keys
```

Secrets are never returned after creation. Only `keyPrefix` is shown.

### Create API Key

```http
POST /admin/api-keys
```

Request:

```json
{
  "name": "default",
  "creditsLimit": 1000
}
```

Response:

```json
{
  "success": true,
  "data": {
    "keyId": "32-char-id",
    "name": "default",
    "key": "sk-kiro-...",
    "keyPrefix": "sk-kiro-...",
    "enabled": true,
    "creditsLimit": 1000,
    "createdAt": "2026-05-30T15:00:00Z"
  },
  "timestamp": "2026-05-30T15:00:00Z"
}
```

Important: save `data.key` immediately. It is stored only as a SHA-256 hash and cannot be recovered later.

### Update API Key

```http
PATCH /admin/api-keys/{keyId}
```

Request:

```json
{
  "name": "new-name",
  "enabled": true,
  "creditsLimit": 2000
}
```

### Delete API Key

```http
DELETE /admin/api-keys/{keyId}
```

### List Model Mappings

```http
GET /admin/model-mappings
```

### Create Model Mapping

```http
POST /admin/model-mappings
```

Replace/alias example:

```json
{
  "name": "gpt aliases",
  "mappingType": "replace",
  "sourceModel": "gpt-*",
  "targetModels": ["claude-sonnet-4.5"],
  "priority": 10
}
```

Load balancing example:

```json
{
  "name": "sonnet balance",
  "mappingType": "loadbalance",
  "sourceModel": "claude-sonnet",
  "targetModels": ["claude-sonnet-4.5", "claude-sonnet-4"],
  "weights": [8, 2],
  "priority": 20,
  "apiKeyIds": ["optional-key-id"]
}
```

Fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `name` | string | yes | Rule display name. |
| `mappingType` | string | yes | `replace`, `alias`, or `loadbalance`. |
| `sourceModel` | string | yes | Exact model or wildcard pattern such as `gpt-*`. |
| `targetModels` | string array | yes | Candidate target models. |
| `weights` | integer array | no | Used only when length equals `targetModels.length`. |
| `priority` | integer | no | Lower number wins. Default is `100`. |
| `apiKeyIds` | string array | no | Empty means global rule. |

### Update Model Mapping

```http
PATCH /admin/model-mappings/{mappingId}
```

All fields are optional.

### Delete Model Mapping

```http
DELETE /admin/model-mappings/{mappingId}
```

### Recent Logs

```http
GET /admin/logs
```

Returns latest 100 request logs. Sensitive token-like text is redacted.

## Actuator Endpoints

Configured management endpoints:

```http
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
GET /actuator/prometheus
```

## Status Codes

| Status | Meaning |
| --- | --- |
| `400` | Invalid request body or missing required field. |
| `401` | Missing or invalid API key, admin session, or legacy admin token. |
| `403` | First-login admin session must change password before using admin APIs. |
| `409` | Duplicate admin username or conflicting resource state. |
| `413` | Request body exceeds `KIRO_MAX_REQUEST_BODY_BYTES`. |
| `429` | Rate limit exceeded or credits limit exceeded. |
| `500` | Unexpected server-side error. |
| `502` | Kiro upstream error. |
| `503` | No available account or no upstream endpoint configured. |

## Current Compatibility Notes

- OpenAI `chat.completions` text messages are supported.
- OpenAI `responses` text input is converted to chat internally.
- Basic function tool schemas are converted to Kiro tool specifications.
- Images, documents, prompt cache controls, and complete tool-result parity are not complete yet.
- Claude `/v1/messages` is not implemented yet.
