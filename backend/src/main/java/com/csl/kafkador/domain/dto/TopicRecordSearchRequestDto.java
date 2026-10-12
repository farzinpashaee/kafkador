package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.RecordSerde;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** What to read from a topic for the Records tab. */
@Data
@Accessors(chain = true)
public class TopicRecordSearchRequestDto {

    public enum SeekType { NEWEST, OLDEST, OFFSET, TIMESTAMP }

    @NotNull(message = "Seek type is required")
    private SeekType seekType = SeekType.NEWEST;

    /** Partitions to read; empty means all of them. */
    @Size(max = 10000)
    private List<@NotNull @Min(0) Integer> partitions = new ArrayList<>();

    /** Start offset for OFFSET, applied to every selected partition. */
    @Min(value = 0, message = "Offset must not be negative")
    private Long offset;

    /** Start time for TIMESTAMP, in epoch milliseconds. */
    @Min(value = 0, message = "Timestamp must not be negative")
    private Long timestamp;

    @NotNull
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 500, message = "Limit must not exceed 500")
    private Integer limit = 100;

    @NotNull
    private RecordSerde keySerde = RecordSerde.STRING;

    @NotNull
    private RecordSerde valueSerde = RecordSerde.STRING;

    @Size(max = 10, message = "At most 10 filters are allowed")
    private List<@Valid RecordFilterDto> filters = new ArrayList<>();

}
