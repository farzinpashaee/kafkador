package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.RecordSerde;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/** One record to produce from the Records tab. A null key or value is sent as null (a null value is a tombstone). */
@Data
@Accessors(chain = true)
public class TopicRecordProduceRequestDto {

    /** Null lets the producer pick the partition (by key hash, or spread when there is no key). */
    @Min(value = 0, message = "Partition must not be negative")
    private Integer partition;

    private String key;

    private String value;

    @NotNull
    private RecordSerde keySerde = RecordSerde.STRING;

    @NotNull
    private RecordSerde valueSerde = RecordSerde.STRING;

    @Size(max = 100, message = "At most 100 headers are allowed")
    private Map<@NotBlank(message = "Header names must not be blank") String, String> headers = new LinkedHashMap<>();

}
