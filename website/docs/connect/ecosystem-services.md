---
title: Schema Registry, Connect and ksqlDB
description: Point Kafkador at Schema Registry, Kafka Connect and ksqlDB, with authentication and a custom CA.
---

# Schema Registry, Connect and ksqlDB

Kafkador talks to these services over their REST APIs. Set up each one in **Settings** while connected to the cluster it
belongs to.

| Settings tab | URL field | Example |
|---|---|---|
| **Schema Registry** | Schema Registry URL | `http://localhost:8081` |
| **Connectors** | Kafka Connect Worker URL | `http://localhost:8083` |
| **KsqlDB** | ksqlDB Server URL | `http://localhost:8088` |

The [`localhost` rule](./from-docker.md) applies here too. When Kafkador runs in Docker, use an address that's reachable
from the container.

## Authentication

Each service has an **Authentication** setting, which managed services need:

- **Basic:** enter a **Username or API key** and a **Password or API secret**. Confluent Cloud takes an API key and
  secret, and Aiven takes a username and password.
- **Bearer:** enter a **Token**, which is sent as `Authorization: Bearer …` with every request.

## Custom CA certificate

Add a **CA certificate** only when the service's TLS certificate isn't signed by a public CA, such as Aiven's project
CA. Paste it, or select **Load from file**.

:::note
Once you set a CA certificate, Kafkador trusts only that CA for the service.
:::
