---
title: Consumer groups
description: Monitor lag and members, and safely reset offsets.
---

# Consumer groups

**Consumers** lists the consumer groups on the cluster. Select a group to see its state, members, assigned topics and
partitions, coordinator and total lag.

The **Topics** table shows the group's lag on each topic. Expand a topic to see, for each partition, the committed
offset, end offset, lag, consumer ID, host and client ID.

## Reset offsets

Resetting moves where the group resumes reading.

:::caution Stop the consumers first
Kafka only allows a reset while the group has no active members.
:::

1. Open the group and select **Reset offsets**.
2. Choose a **Topic** and **Partition**, or keep all of them.
3. Choose where to reset to:

   | Option | The group resumes at |
   |---|---|
   | **Earliest** | The oldest record each partition still holds |
   | **Latest** | The end of each partition, skipping anything unread |
   | **Date & time** | The first record written at or after that time, in your browser's time zone |
   | **Offset** | That offset on every chosen partition, kept within what each partition still holds |

4. Select **Preview** to see the current offset, new offset and lag afterwards for each partition.
5. Select **Reset** to apply what you previewed. The button shows how many partitions will change.
