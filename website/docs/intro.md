---
slug: /
title: Introduction
sidebar_label: Introduction
description: Kafkador is a web console for Apache Kafka and Confluent Platform.
---

# Kafkador

Kafkador is a web console for **Apache Kafka** and **Confluent Platform**. See what your clusters are doing and manage
them from the browser, from brokers and topics down to single records, consumer offsets, ACLs, Schema Registry, Kafka
Connect and ksqlDB.

![The Kafkador dashboard](/img/screenshots/dashboard.jpg)

:::note Beta
Kafkador is at version **1.0.0-beta.2**. Whoever signs in can use every feature, because there are no read-only users
yet. See [Running in production](./security/production.md) before you open it to other people.
:::

## What you can do

- **Clusters and brokers:** check partition health, view broker details, and edit configuration where Kafka allows it.
- **Topics:** create, configure and delete topics, and see their partitions, replicas and consumers.
- **Records:** browse from any position, filter, follow new records live, export, and produce records with headers.
- **Consumer groups:** see members, assignments and lag, and reset offsets after previewing the change.
- **Schema Registry:** decode Avro, Protobuf and JSON Schema records, compare versions, and set compatibility.
- **Kafka Connect and ksqlDB:** manage connectors, streams, tables and queries, and run statements.
- **ACLs:** list, create, edit and delete bindings.
- **AI assistant:** optionally ask read-only questions about the connected cluster.

## Compatibility

Kafkador works with Apache Kafka 2.1 or newer and with Confluent Platform. It uses the Kafka 4.0 client and supports
clusters secured with TLS and SASL (PLAIN and SCRAM).

## Choose how to run it

| Option | Best for |
|---|---|
| [Docker](./install/docker.md) | Servers and shared use. This is the quickest way to start. |
| [Desktop app](./install/desktop.md) | One person on their own Windows or Mac computer. |
| [Jar](./install/jar.md) | Machines with Java and no Docker, or building from source. |

Ready to try it? Follow the [quick start](./getting-started/quick-start.md).

## License

Kafkador is source-available under the
[PolyForm Shield License 1.0.0](https://github.com/farzinpashaee/kafkador/blob/main/LICENSE). You may use, change and
share it for free, including at work, but not to offer a product or service that competes with Kafkador. Because of that
limit, it isn't "open source" in the OSI sense.
