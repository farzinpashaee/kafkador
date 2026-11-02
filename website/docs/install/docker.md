---
title: Docker
description: Run the Kafkador image with Docker Compose or docker run.
---

# Docker

Kafkador ships as a single image, [`codestreamlab/kafkador`](https://hub.docker.com/r/codestreamlab/kafkador), that
serves both the web UI and the API on port 8080. Images are built for `linux/amd64` and `linux/arm64`, which covers
Intel and AMD servers, Apple Silicon, Raspberry Pi 4 and 5, and AWS Graviton.

:::warning Protect your instance
Anyone who uses Kafkador can read and produce records, change topics, ACLs and consumer offsets, and use any credentials
saved in it. Read [Running in production](../security/production.md) before making it reachable by others.
:::

## With Docker Compose

```bash
curl -O https://raw.githubusercontent.com/farzinpashaee/kafkador/main/docker-compose.yml
echo "KAFKADOR_VERSION=1.0.0-beta.2" > .env
docker compose up -d
```

Open **http://localhost:8080** and complete [first-run setup](../getting-started/first-run-setup.md).

## With docker run

```bash
docker run -d --name kafkador -p 8080:8080 \
  -v kafkador-data:/app/data \
  --add-host host.docker.internal:host-gateway \
  --restart unless-stopped \
  codestreamlab/kafkador:1.0.0-beta.2
```

## Settings in `.env`

Create a `.env` file next to `docker-compose.yml`:

| Variable | Default | Description |
|---|---|---|
| `KAFKADOR_VERSION` | `latest` | Image tag. Pin a release (`1.2.3`), a minor line (`1.2`) or a major (`1`). |
| `KAFKADOR_PORT` | `8080` | Host port for the UI. `127.0.0.1:8080` publishes it on this machine only. |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | JVM options, such as `-Xmx512m`. |

You can set any Spring Boot property as an environment variable in `docker-compose.yml`:

```yaml
environment:
  LOGGING_LEVEL_ROOT: WARN
```

Apply changes with `docker compose up -d`. For Kafkador's own settings, see the
[configuration reference](../reference/configuration.md).

## Everyday commands

| Task | Command |
|---|---|
| Upgrade to the newest image of your tag | `docker compose pull && docker compose up -d` |
| View logs | `docker compose logs -f kafkador` |
| Check status and health | `docker compose ps` |
| Stop | `docker compose down` |
| Stop and delete all data | `docker compose down -v` |

The container reports healthy once `/actuator/health` answers UP. That check covers Kafkador's own database, not your
Kafka clusters.

## Image tags

| Git tag | Image tags published |
|---|---|
| `v1.2.3` | `1.2.3`, `1.2`, `1`, `latest` |
| `v1.2.3-rc.1` | `1.2.3-rc.1` only |

## Next steps

- [Connect to a cluster from the container](../connect/from-docker.md)
- [Back up your data](../reference/data-and-backup.md)
