package com.csl.kafkador.domain.dto;

/** Where the broker stored a produced record. */
public record TopicRecordProduceResultDto(int partition, long offset, long timestamp) {
}
