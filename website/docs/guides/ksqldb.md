---
title: ksqlDB
description: Work with streams, tables and persistent queries, and run statements.
---

# ksqlDB

**KsqlDB** shows your ksqlDB server, its streams, tables and persistent queries. First,
[set the server's URL](../connect/ecosystem-services.md) in **Settings → KsqlDB**.

## Create a stream or table

1. Select **New Stream** or **New Table**.
2. Enter a **Name**, then choose what it's built from: **A topic**, or **A query (AS SELECT)**.
3. For a topic, choose the **Topic**, where to **Read from**, the **Key format**, **Value format** and **Partitions**.
4. Check the **Statement** that Kafkador builds, then select **Create**.

## Stop a query

Persistent queries keep running until you terminate them. Terminating one needs a confirmation.
