package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TopicRecordSearchResultDto {

    private List<TopicRecordDto> records;
    /** Records read to find these, filtered out ones included. */
    private long scannedCount;
    /** True when the scan stopped at its record or time budget rather than at the end of the selected partitions. */
    private boolean scanLimitReached;

}
