---
title: Connecting to a cluster
description: Add, switch, edit and delete Kafka cluster connections.
---

# Connecting to a cluster

A connection tells Kafkador how to reach one Kafka cluster. You can save several and switch between them.

## Add a connection

1. On the connect page, select **Connect to New Kafka Cluster**. If you already have connections, select **add a new
   one**.
2. Fill in the details:

   | Field | What to enter |
   |---|---|
   | **Cluster Name** | A friendly name, such as "Local Dev" or "Production" |
   | **Host Address** | The hostname or IP address of one broker |
   | **Port Number** | The broker's listener port. Kafka defaults to 9092 |

3. If the cluster needs a login, turn on **This cluster requires authentication (SASL)**. See
   [TLS and SASL](./tls-and-sasl.md).
4. Select **Connect & Save**.

## Reach the brokers it advertises

Kafka clients contact the address you enter, then connect to whatever address each broker *advertises*. If a broker
advertises `localhost:9092`, Kafkador can reach the first address and then fail to talk to the broker.

Make sure `advertised.listeners` on each broker holds an address that Kafkador can reach. This is the most common reason
a connection fails.

## Manage connections

- **Switch cluster:** on the connect page, select **Connect** beside a saved connection.
- **Edit or delete from the connect page:** open the arrow beside **Connect**.
- **Edit or delete while connected:** go to **Connections** in the side menu.

## How long a connection lasts

Your connection stays active for 30 minutes without use, and ends when you close the browser. To change the timeout, go
to **Settings → Generic → Session timeout**. It can be up to 24 hours, and a new value applies to every open session on
its next request.
