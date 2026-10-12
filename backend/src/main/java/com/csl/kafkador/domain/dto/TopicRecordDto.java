package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** One record as shown on the Records tab, with its key and value decoded by the chosen serdes. */
@Data
@Accessors(chain = true)
public class TopicRecordDto {

    private int partition;
    private long offset;
    private long timestamp;
    private String timestampType;
    private String key;
    private String value;
    // Serialized sizes in bytes; -1 for a null key/value
    private int keySize;
    private int valueSize;
    private List<Header> headers;

    public record Header(String key, String value) {
    }

}
