# Server One-Click Install

This installer is for Linux servers. It deploys the project with Docker Compose:

```text
Nginx / 1Panel reverse proxy
  -> 127.0.0.1:18080
  -> kiro-proxy-service container with embedded WebUI
  -> mysql container
```

The service is bound to `127.0.0.1` by default. Expose it through 1Panel or Nginx instead of opening the compose port directly.

## One-Line Install

```bash
curl -fsSL https://raw.githubusercontent.com/StaR4y/Kiro-proxy-service/main/scripts/install-server.sh | sudo bash
```

With a public domain:

```bash
curl -fsSL https://raw.githubusercontent.com/StaR4y/Kiro-proxy-service/main/scripts/install-server.sh \
  | sudo env PUBLIC_BASE_URL=https://kiro.example.com bash
```

The installer will:

- install `git`, `curl`, `openssl`, Docker, and Docker Compose when missing
- clone or update the repo under `/opt/kiro-proxy-service`
- create `.env` with generated MySQL passwords
- build the service image, including WebUI static files
- start MySQL and the service with Docker Compose
- wait for `/actuator/health`
- print recent logs so the first admin password can be saved

## Configuration Variables

Set variables before running the installer:

```bash
sudo env \
  INSTALL_DIR=/opt/kiro-proxy-service \
  HOST_HTTP_PORT=18080 \
  PUBLIC_BASE_URL=https://kiro.example.com \
  bash scripts/install-server.sh
```

Supported variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `REPO_URL` | `https://github.com/StaR4y/Kiro-proxy-service.git` | Git repository to deploy |
| `BRANCH` | `main` | Git branch to deploy |
| `INSTALL_DIR` | `/opt/kiro-proxy-service` | Server install directory |
| `HOST_HTTP_PORT` | `18080` | Local host port exposed by compose |
| `SERVER_PORT` | `8080` | Internal Spring Boot port |
| `PUBLIC_BASE_URL` | empty | Public HTTPS URL used in startup logs |
| `FORCE_ENV` | `false` | Regenerate `.env` even if it already exists |
| `INSTALL_DOCKER` | `true` | Install Docker when missing |

## 1Panel Reverse Proxy

After the installer finishes, create a 1Panel reverse proxy site:

```text
Proxy address: http://127.0.0.1:18080
Domain:        your domain
HTTPS:         enable in 1Panel
```

Then open:

```text
https://your-domain.example/#/login
```

## First Login

The initial admin user is:

```text
username: admin
password: printed once in first startup logs
```

The password is only printed when the admin table is created. Save it immediately, then log in and change it.

## Operations

```bash
cd /opt/kiro-proxy-service
docker compose logs -f service
docker compose restart service
docker compose down
```

Update:

```bash
cd /opt/kiro-proxy-service
git pull
./scripts/install-server.sh
```

Do not delete `./data/mysql` unless you want to reset the database.
