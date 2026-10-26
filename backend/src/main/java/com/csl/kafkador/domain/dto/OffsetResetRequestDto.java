package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.OffsetResetTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** Which of a consumer group's offsets to move, and where to. */
@Data
@Accessors(chain = true)
public class OffsetResetRequestDto {

    /** The topic to reset; none resets every topic the group has committed offsets on. */
    private String topic;

    /** Partitions of {@link #topic} to reset; none means all of them. */
    private List<Integer> partitions = new ArrayList<>();

    @NotNull(message = "Choose where to reset the offsets to")
    private OffsetResetTarget target;

    /** TIMESTAMP: epoch milliseconds. */
    private Long timestamp;

    /** OFFSET: the offset to move to. */
    @PositiveOrZero(message = "The offset can't be negative")
    private Long offset;

    /** Only work out the new offsets, without changing anything. */
    private boolean dryRun = true;

}
