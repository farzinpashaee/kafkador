# Running Kafkador with Docker

Kafkador ships as a single image, [`codestreamlab/kafkador`](https://hub.docker.com/r/codestreamlab/kafkador),
that serves both the web UI and the API on port **8080**. Images are built for `linux/amd64` and `linux/arm64`
(Intel/AMD servers, Apple Silicon, Raspberry Pi 4/5, AWS Graviton).

You only need [Docker](https://docs.docker.com/get-docker/) with the Compose plugin (`docker compose version`).

---

## Quick start

```bash
curl -O https://raw.githubusercontent.com/farzinpashaee/kafkador/main/docker-compose.yml
docker compose up -d
```

Open **http://localhost:8080**, add a connection to your Kafka cluster, and you're done.

<details>
<summary>Without Compose</summary>

```bash
docker run -d --name kafkador -p 8080:8080 \
  -v kafkador-data:/app/data \
  --add-host host.docker.internal:host-gateway \
  --restart unless-stopped \
  codestreamlab/kafkador:latest
```
</details>

---

## Connecting to Kafka

Kafkador runs inside a container, so **`localhost` means the container itself, not your machine.** Use one of
these as the connection host:

| Where Kafka runs | Host to enter in Kafkador |
|---|---|
| On the same machine (installed, or a container with a published port) | `host.docker.internal` |
| On another server | its IP or DNS name, e.g. `10.0.0.12` |
| In another Docker Compose project | the Kafka service name, after joining its network (see below) |

### Kafka advertised listeners

Kafka clients first contact the bootstrap address and then connect to whatever address each broker
*advertises*. If the broker advertises `localhost:9092`, Kafkador can reach the bootstrap server but then
fails to talk to the broker. Make sure `advertised.listeners` contains an address reachable **from the
container**, e.g. `PLAINTEXT://host.docker.internal:9092` or the server's real IP/hostname.

### Joining an existing Kafka Compose network

If Kafka runs in another Compose project, attach Kafkador to that project's network. Find its name with
`docker network ls`, then add this to `docker-compose.yml`:

```yaml
services:
  kafkador:
    # ...existing settings...
    networks: [ default, kafka-net ]

networks:
  kafka-net:
    external: true
    name: my-kafka_default   # the network name from "docker network ls"
```

Then use the Kafka service name (e.g. `kafka`) and its internal port as the connection host/port.

---

## Configuration

Create a `.env` file next to `docker-compose.yml`
(template: [`.env.example`](.env.example)):

| Variable | Default | Description |
|---|---|---|
| `KAFKADOR_VERSION` | `latest` | Image tag. Pin a release (`1.2.3`), a minor line (`1.2`) or a major (`1`). |
| `KAFKADOR_PORT` | `8080` | Host port the UI is served on. |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | JVM options, e.g. `-Xmx512m`. |

Any Spring Boot property can also be set as an environment variable in `docker-compose.yml`, for example:

```yaml
    environment:
      LOGGING_LEVEL_ROOT: WARN
```

Apply changes with `docker compose up -d`.

---

## Data, backup and restore

Connections, settings and sessions are stored in an H2 database inside the named volume `kafkador-data`
(mounted at `/app/data`). It survives restarts, upgrades and `docker compose down`. Only
`docker compose down -v` deletes it.

**Backup**

```bash
docker compose stop
docker run --rm -v kafkador_kafkador-data:/data -v "$PWD":/backup alpine tar czf /backup/kafkador-data.tgz -C /data .
docker compose start
```

**Restore**

```bash
docker compose stop
docker run --rm -v kafkador_kafkador-data:/data -v "$PWD":/backup alpine sh -c "rm -rf /data/* && tar xzf /backup/kafkador-data.tgz -C /data"
docker compose start
```

> The volume name is prefixed with the Compose project name (the folder name by default). Check it with
> `docker volume ls`.

To use a host folder instead of a named volume, replace `kafkador-data:/app/data` with e.g.
`./data:/app/data` and make the folder writable by the container user (UID `10001`):
`sudo chown -R 10001:10001 ./data`.

---

## Everyday commands

| Task | Command |
|---|---|
| Upgrade to the newest image | `docker compose pull && docker compose up -d` |
| View logs | `docker compose logs -f kafkador` |
| Status / health | `docker compose ps` |
| Stop | `docker compose down` |
| Stop and delete all data | `docker compose down -v` |

---

## For maintainers: publishing images

Images are built and pushed to Docker Hub by
[`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml) **only when a version tag is
pushed**. The backend and frontend test workflows run first; the image is published only if both pass.

### One-time setup

1. On Docker Hub, signed in as **codestreamlab**: *Account settings → Personal access tokens → Generate new
   token* with **Read & Write** access.
2. On GitHub: *Settings → Secrets and variables → Actions → New repository secret*:
   - `DOCKERHUB_USERNAME` = `codestreamlab`
   - `DOCKERHUB_TOKEN` = the token from step 1

### Releasing

```bash
git tag v1.0.0
git push origin v1.0.0
```

| Git tag | Image tags published |
|---|---|
| `v1.2.3` | `1.2.3`, `1.2`, `1`, `latest` |
| `v1.2.3-rc.1` | `1.2.3-rc.1` only |
| `v0.4.0` | `0.4.0`, `0.4`, `latest` |

Each image is multi-arch (`amd64` + `arm64`) and includes provenance attestations and an SBOM.

### Building locally

```bash
docker build -t kafkador:dev .
docker run --rm -p 8080:8080 kafkador:dev
```

The [`Dockerfile`](Dockerfile) builds the Angular app, bundles it into the Spring Boot jar (equivalent to
`./mvnw package -P with-frontend`) and runs it on a JRE 21 base as a non-root user.
