package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request payload for topic creation. Kept separate from the {@code Topic} read model
 * so the public write contract only exposes fields a client may legitimately set and
 * can be validated independently of how a topic is represented in responses.
 */
@Data
@Accessors(chain = true)
public class TopicCreateRequestDto {

    @NotBlank(message = "Topic name is required")
    @Size(max = 249)
    @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "Topic name may only contain letters, numbers, '.', '_' and '-'")
    private String name;

    @NotNull(message = "Partition count is required")
    @Min(value = 1, message = "Partition count must be at least 1")
    @Max(value = 10000, message = "Partition count must not exceed 10000")
    private Integer partitions;

    @NotNull(message = "Replication factor is required")
    @Min(value = 1, message = "Replication factor must be at least 1")
    @Max(value = 32767, message = "Replication factor must not exceed 32767")
    private Short replicatorFactor;

}
