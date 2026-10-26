package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.OffsetResetResultDto.Change;
import com.csl.kafkador.domain.options.OffsetResetTarget;
import org.apache.kafka.common.TopicPartition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Works out where a consumer group's offsets go, from what each partition holds; the Kafka calls are the caller's.
 * Follows kafka-consumer-groups --reset-offsets: a point in time with no record at or after it means the end, and an
 * offset outside the partition is moved to its nearest end.
 */
public final class OffsetResetPlanner {

    private OffsetResetPlanner() {
    }

    /** What one partition holds, and where the group is on it. */
    public record PartitionOffsets(Long current, long earliest, long latest, Long atTimestamp) {}

    /**
     * @param offsets per partition; {@code atTimestamp} is only read for TIMESTAMP, and is null when no record was
     *                written at or after it
     */
    public static List<Change> plan(OffsetResetTarget target, Long offset, Map<TopicPartition, PartitionOffsets> offsets) {
        return offsets.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<TopicPartition, PartitionOffsets> e) -> e.getKey().topic())
                        .thenComparingInt(e -> e.getKey().partition()))
                .map(e -> change(target, offset, e.getKey(), e.getValue()))
                .toList();
    }

    private static Change change(OffsetResetTarget target, Long offset, TopicPartition tp, PartitionOffsets p) {
        long newOffset;
        String note = null;
        switch (target) {
            case EARLIEST -> newOffset = p.earliest();
            case LATEST -> newOffset = p.latest();
            case TIMESTAMP -> {
                newOffset = p.atTimestamp() == null ? p.latest() : p.atTimestamp();
                if (p.atTimestamp() == null) note = "Nothing written since then; moved to the end";
            }
            case OFFSET -> {
                newOffset = Math.max(p.earliest(), Math.min(p.latest(), offset));
                if (offset < p.earliest()) note = "Offset " + offset + " was deleted; moved to the earliest, " + p.earliest();
                else if (offset > p.latest()) note = "Offset " + offset + " is past the end; moved to the end, " + p.latest();
            }
            default -> throw new IllegalArgumentException("Unknown reset target " + target);
        }
        return new Change()
                .setTopic(tp.topic())
                .setPartition(tp.partition())
                .setCurrentOffset(p.current())
                .setNewOffset(newOffset)
                .setEndOffset(p.latest())
                .setLagAfter(Math.max(0, p.latest() - newOffset))
                .setNote(note);
    }

}
