---
title: Topics
description: Create, inspect, configure and delete topics.
---

# Topics

**Topics** lists the topics in the cluster. Select **New Topic** to create one, or the delete icon beside a topic to
remove it. Select a topic to open its details.

## Topic tabs

| Tab | What's there |
|---|---|
| **Details** | Identity and partitioning, retention and cleanup policy, in-sync replicas and durability, and performance |
| **Records** | [Browse and produce records](./records.md) |
| **Configurations** | Every setting, with its documentation. Edit the ones Kafka allows |
| **Consumers** | The consumer groups reading this topic |
| **Metrics** | [Recent values](./overview.md#metrics) from the Confluent metrics topics |

## Change a configuration

1. Open the topic and select **Configurations**.
2. Use the **Editable** switch to show only settings you can change.
3. Select the edit icon beside a setting and enter the new value.

To read what a setting does, select the question mark beside its name.
