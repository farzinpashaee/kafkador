package com.csl.kafkador.domain.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApmMetricIngestDto {

    private static final ObjectMapper mapper = new ObjectMapper();

    private String brokerId;
    private String clusterId;
    private String version;
    private String commitId;
    private ApmMetricIngestMetric metrics;

    @Data
    public static class ApmMetricIngestMetric{

        private String messagesInPerSec;
        private String bytesInPerSec;
        private String bytesOutPerSec;
        private String bytesRejectedPerSec;
        private String failedFetchRequestsPerSec;
        private String failedProduceRequestsPerSec;
        // ReplicaManager Metrics
        private String underReplicatedPartitions;
        private String underMinIsrPartitionCount;
        private String leaderCount;
        private String partitionCount;
        private String offlineReplicaCount;
        private String isrShrinksPerSec;
        private String isrExpandsPerSec;
        // Controller Metrics
        private String activeControllerCount;
        private String offlinePartitionsCount;
        private String preferredReplicaImbalanceCount;
        private String uncleanLeaderElectionsPerSec;
        private String leaderElectionRateAndTimeMs;
        // Request Metrics
        private String produceRequestPerSec;
        private String fetchConsumerRequestPerSec;
        private String fetchFollowerRequestPerSec;
        private String metadataRequestPerSec;
        // Request Queue / Network
        private String requestQueueSize;
        private String networkProcessorIdlePercent;
        // JVM Memory
        private String heapUsed;
        private String heapCommitted;
        private String heapMax;
        private String nonHeapUsed;
        private String nonHeapCommitted;
        private String nonHeapMax;
        // JVM CPU
        private String processCpuLoad;
        private String systemCpuLoad;
        private String systemLoadAverage;
        // JVM Threads
        private String threadCount;
        private String daemonThreadCount;
        // Garbage Collection
        private String gcYoungCollectionCount;
        private String gcYoungCollectionTime;
        private String gcOldCollectionCount;
        private String gcOldCollectionTime;
    }


    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }


}
