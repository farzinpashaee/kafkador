package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.kafka.common.config.TopicConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request payload for topic creation. Kept separate from the {@code Topic} read model
 * so the public write contract only exposes fields a client may legitimately set and
 * can be validated independently of how a topic is represented in responses.
 * Optional settings left null fall back to the broker defaults.
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

    /** Null uses the broker's default replication factor. */
    @Min(value = 1, message = "Replication factor must be at least 1")
    @Max(value = 32767, message = "Replication factor must not exceed 32767")
    private Short replicatorFactor;

    @Pattern(regexp = "delete|compact|compact,delete", message = "Cleanup policy must be 'delete', 'compact' or 'compact,delete'")
    private String cleanupPolicy;

    @Min(value = 1, message = "Min in sync replicas must be at least 1")
    @Max(value = 32767, message = "Min in sync replicas must not exceed 32767")
    private Integer minInSyncReplicas;

    /** -1 retains data forever. */
    @Min(value = -1, message = "Retention time must be -1 (forever) or a positive number of milliseconds")
    private Long retentionMs;

    /** Maximum size a partition may grow to before old segments are discarded; -1 means no limit. */
    @Min(value = -1, message = "Max partition size must be -1 (no limit) or a positive number of bytes")
    private Long retentionBytes;

    @Min(value = 1, message = "Maximum message size must be at least 1 byte")
    private Integer maxMessageBytes;

    /** Any other topic-level configs, by Kafka config name. Override the fields above on conflict. */
    @Size(max = 100, message = "Too many custom parameters")
    private Map<@NotBlank(message = "Custom parameter name is required") String,
            @NotNull(message = "Custom parameter value is required") String> configs;

    /** All topic-level configs this request sets, keyed by Kafka config name. */
    public Map<String, String> toTopicConfigs() {
        Map<String, String> result = new LinkedHashMap<>();
        putIfSet(result, TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicy);
        putIfSet(result, TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas);
        putIfSet(result, TopicConfig.RETENTION_MS_CONFIG, retentionMs);
        putIfSet(result, TopicConfig.RETENTION_BYTES_CONFIG, retentionBytes);
        putIfSet(result, TopicConfig.MAX_MESSAGE_BYTES_CONFIG, maxMessageBytes);
        if (configs != null) {
            configs.forEach((key, value) -> result.put(key.trim(), value.trim()));
        }
        return result;
    }

    private static void putIfSet(Map<String, String> target, String key, Object value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }

}
