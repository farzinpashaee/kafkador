package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** The offsets a reset moves, or did move when it wasn't a dry run. */
@Data
@Accessors(chain = true)
public class OffsetResetResultDto {

    /** The offsets were changed, not just worked out. */
    private boolean applied;

    /** Why the reset can't be applied now, e.g. the group still has active members; null when it can. */
    private String blockedReason;

    private List<Change> changes = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class Change {
        private String topic;
        private int partition;
        /** The committed offset before the reset; null when the group has none on this partition. */
        private Long currentOffset;
        private long newOffset;
        private long endOffset;
        /** Records left to read after the reset. */
        private long lagAfter;
        /** A remark about this partition, e.g. that the asked-for offset was outside what it holds. */
        private String note;
    }

}
