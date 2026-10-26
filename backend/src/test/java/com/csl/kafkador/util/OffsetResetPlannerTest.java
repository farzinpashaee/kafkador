package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.OffsetResetResultDto.Change;
import com.csl.kafkador.domain.options.OffsetResetTarget;
import com.csl.kafkador.util.OffsetResetPlanner.PartitionOffsets;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OffsetResetPlannerTest {

    private static final TopicPartition P0 = new TopicPartition("orders", 0);
    private static final TopicPartition P1 = new TopicPartition("orders", 1);
    /** Partition 0 holds offsets 100 to 500, the group at 300; partition 1 holds 0 to 50, no committed offset. */
    private static final Map<TopicPartition, PartitionOffsets> OFFSETS = Map.of(
            P1, new PartitionOffsets(null, 0, 50, null),
            P0, new PartitionOffsets(300L, 100, 500, 250L));

    @Test
    void movesToTheEarliestOrLatestOffsetWithTheLagThatLeaves() {
        assertThat(OffsetResetPlanner.plan(OffsetResetTarget.EARLIEST, null, OFFSETS))
                .extracting(Change::getPartition, Change::getCurrentOffset, Change::getNewOffset, Change::getLagAfter)
                .containsExactly(tuple(0, 300L, 100L, 400L), tuple(1, null, 0L, 50L));
        assertThat(OffsetResetPlanner.plan(OffsetResetTarget.LATEST, null, OFFSETS))
                .extracting(Change::getNewOffset, Change::getLagAfter)
                .containsExactly(tuple(500L, 0L), tuple(50L, 0L));
    }

    @Test
    void movesToThePointInTimeOrTheEndWhenNothingWasWrittenSince() {
        List<Change> changes = OffsetResetPlanner.plan(OffsetResetTarget.TIMESTAMP, null, OFFSETS);

        assertThat(changes.get(0).getNewOffset()).isEqualTo(250L);
        assertThat(changes.get(0).getNote()).isNull();
        assertThat(changes.get(1).getNewOffset()).isEqualTo(50L);
        assertThat(changes.get(1).getNote()).contains("moved to the end");
    }

    @Test
    void keepsAnExactOffsetWithinWhatThePartitionHolds() {
        List<Change> changes = OffsetResetPlanner.plan(OffsetResetTarget.OFFSET, 40L, OFFSETS);

        assertThat(changes.get(0).getNewOffset()).isEqualTo(100L);
        assertThat(changes.get(0).getNote()).contains("was deleted").contains("100");
        assertThat(changes.get(1).getNewOffset()).isEqualTo(40L);
        assertThat(changes.get(1).getNote()).isNull();

        Change pastTheEnd = OffsetResetPlanner.plan(OffsetResetTarget.OFFSET, 9_000L, Map.of(P1, OFFSETS.get(P1))).get(0);
        assertThat(pastTheEnd.getNewOffset()).isEqualTo(50L);
        assertThat(pastTheEnd.getNote()).contains("past the end");
    }

}
