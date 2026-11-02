---
title: Quick start
description: Run Kafkador with Docker and connect it to your first cluster.
---

# Quick start

This guide runs Kafkador with Docker and connects it to a Kafka cluster. You need
[Docker](https://docs.docker.com/get-docker/) with the Compose plugin.

## 1. Start Kafkador

```bash
curl -O https://raw.githubusercontent.com/farzinpashaee/kafkador/main/docker-compose.yml
echo "KAFKADOR_VERSION=1.0.0-beta.2" > .env
docker compose up -d
```

Only pre-releases are published so far, so there's no `latest` image yet. That's why `.env` sets the version.

## 2. Find the setup token

Open **http://localhost:8080**. The setup page asks for a one-time token, which stops anyone else from claiming a new
instance before you do. Get it from the log:

```bash
docker compose logs kafkador | grep -A1 "setup token"
```

## 3. Complete setup

Setup has two steps:

1. Choose a username and password for Kafkador's embedded database.
2. Choose who may use Kafkador: nobody needs to sign in, a Kafkador admin account, or LDAP accounts.

**No sign-in** is selected by default. Keep it only if nobody else can reach Kafkador. See
[First-run setup](./first-run-setup.md) for each option.

## 4. Connect to Kafka

1. Select **Connect to New Kafka Cluster**.
2. Enter a **Cluster Name**, **Host Address** and **Port Number**.
3. If the cluster uses SASL, turn on **This cluster requires authentication (SASL)** and enter the credentials.
4. Select **Connect & Save**.

:::caution `localhost` means the container
Inside Docker, `localhost` is the container itself. If Kafka runs on the same machine, use `host.docker.internal` as
the host. See [Connecting from Docker](../connect/from-docker.md).
:::

## Next steps

- [Browse records](../guides/records.md) in a topic.
- [Add Schema Registry, Kafka Connect and ksqlDB](../connect/ecosystem-services.md).
- [Prepare Kafkador for shared use](../security/production.md).
