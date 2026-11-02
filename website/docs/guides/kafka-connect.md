---
title: Kafka Connect
description: Create, configure and control connectors.
---

# Kafka Connect

**Connectors** lists your connectors and their state. First, [set the worker's URL](../connect/ecosystem-services.md) in
**Settings → Connectors**. That tab then also lists the connector plugins installed on the worker.

## Create a connector

1. On **Connectors**, select **New Connector**.
2. Enter the **Connector Name** and choose a **Plugin**.
3. Add the **Configuration** properties.
4. Select **Create Connector**.

## Manage a connector

From the list you can pause, resume, restart, edit or delete a connector. Editing changes its configuration, and you
finish with **Save Configuration**.

If a connector has failed, open its failure details to see what went wrong and the state of each task.
