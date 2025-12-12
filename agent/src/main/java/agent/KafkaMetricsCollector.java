package agent;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KafkaMetricsCollector {

    private final MBeanServerConnection mbean;
    private final HttpSender sender;

    private final AgentConfig agentConfig;

    public KafkaMetricsCollector(AgentConfig agentConfig) {
        this.mbean = ManagementFactory.getPlatformMBeanServer();
        this.sender = new HttpSender();
        this.agentConfig = agentConfig;
    }

    private Object get(String obj, String attr) throws Exception {
        return mbean.getAttribute(new ObjectName(obj), attr);
    }

    private double getRate(String mbeanName) {
        try {
            return (double) get(mbeanName, "OneMinuteRate");
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long getValue(String mbeanName) {
        try {
            return (long) get(mbeanName, "Value");
        } catch (Exception e) {
            return 0L;
        }
    }

    private Object getAttr(String mbeanName, String attr) {
        try {
            return get(mbeanName, attr);
        } catch (Exception e) {
            return null;
        }
    }

    public void collectAndSend( AgentConfig agentConfig ) throws Exception {

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> metrics = new HashMap<>();

        ObjectName query = new ObjectName("kafka.server:type=app-info,*");
        Set<ObjectName> names = mbean.queryNames(query, null);
        ObjectName appInfo = names.stream().findFirst().orElse(null);

        String brokerId = null;
        String clusterId = null;
        String version = null;
        String commitId = null;

        if (appInfo != null) {
            String on = appInfo.toString();
            brokerId  = (String) getAttr(on, "broker.id");
            clusterId = (String) getAttr(on, "cluster.id");
            version   = (String) getAttr(on, "version");
            commitId  = (String) getAttr(on, "commit.id");
        }

        // Fallbacks from config if JMX doesn’t have them
        if (brokerId == null)  brokerId  = agentConfig.getBrokerId();
        if (clusterId == null) clusterId = agentConfig.getClusterId();

        requestBody.put("brokerId",brokerId);
        requestBody.put("clusterId",clusterId);
        requestBody.put("version",version);
        requestBody.put("commitId",commitId);

        // BROKER THROUGHPUT
        metrics.put("messagesInPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec"));

        metrics.put("bytesInPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"));

        metrics.put("bytesOutPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"));

        metrics.put("bytesRejectedPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=BytesRejectedPerSec"));

        metrics.put("failedFetchRequestsPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=FailedFetchRequestsPerSec"));

        metrics.put("failedProduceRequestsPerSec",
                getRate("kafka.server:type=BrokerTopicMetrics,name=FailedProduceRequestsPerSec"));

        // REPLICA MANAGER
        metrics.put("underReplicatedPartitions",
                getValue("kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions"));

        metrics.put("underMinIsrPartitionCount",
                getValue("kafka.server:type=ReplicaManager,name=UnderMinIsrPartitionCount"));

        metrics.put("leaderCount",
                getValue("kafka.server:type=ReplicaManager,name=LeaderCount"));

        metrics.put("partitionCount",
                getValue("kafka.server:type=ReplicaManager,name=PartitionCount"));

        metrics.put("offlineReplicaCount",
                getValue("kafka.server:type=ReplicaManager,name=OfflineReplicaCount"));

        metrics.put("isrShrinksPerSec",
                getRate("kafka.server:type=ReplicaManager,name=IsrShrinksPerSec"));

        metrics.put("isrExpandsPerSec",
                getRate("kafka.server:type=ReplicaManager,name=IsrExpandsPerSec"));

        // CONTROLLER METRICS
        metrics.put("activeControllerCount",
                getValue("kafka.controller:type=KafkaController,name=ActiveControllerCount"));

        metrics.put("offlinePartitionsCount",
                getValue("kafka.controller:type=KafkaController,name=OfflinePartitionsCount"));

        metrics.put("preferredReplicaImbalanceCount",
                getValue("kafka.controller:type=KafkaController,name=PreferredReplicaImbalanceCount"));

        metrics.put("uncleanLeaderElectionsPerSec",
                getRate("kafka.controller:type=ControllerStats,name=UncleanLeaderElectionsPerSec"));

        metrics.put("leaderElectionRateAndTimeMs",
                getRate("kafka.controller:type=ControllerStats,name=LeaderElectionRateAndTimeMs"));


        // REQUEST METRICS (IMPORTANT APIs)
        metrics.put("produceRequestPerSec",
                getRate("kafka.network:type=RequestMetrics,name=RequestsPerSec,request=Produce"));

        metrics.put("fetchConsumerRequestPerSec",
                getRate("kafka.network:type=RequestMetrics,name=RequestsPerSec,request=FetchConsumer"));

        metrics.put("fetchFollowerRequestPerSec",
                getRate("kafka.network:type=RequestMetrics,name=RequestsPerSec,request=FetchFollower"));

        metrics.put("metadataRequestPerSec",
                getRate("kafka.network:type=RequestMetrics,name=RequestsPerSec,request=Metadata"));

        // REQUEST QUEUE / NETWORK PROCESSORS
        metrics.put("requestQueueSize",
                getValue("kafka.network:type=RequestChannel,name=RequestQueueSize"));

        metrics.put("networkProcessorIdlePercent",
                getValue("kafka.network:type=NetworkProcessor,name=IdlePercent"));

        // JVM MEMORY
        CompositeData heap =
                (CompositeData) getAttr("java.lang:type=Memory", "HeapMemoryUsage");
        CompositeData nonHeap =
                (CompositeData) getAttr("java.lang:type=Memory", "NonHeapMemoryUsage");

        if (heap != null) {
            metrics.put("heapUsed", heap.get("used"));
            metrics.put("heapCommitted", heap.get("committed"));
            metrics.put("heapMax", heap.get("max"));
        }

        if (nonHeap != null) {
            metrics.put("nonHeapUsed", nonHeap.get("used"));
            metrics.put("nonHeapCommitted", nonHeap.get("committed"));
            metrics.put("nonHeapMax", nonHeap.get("max"));
        }

        // JVM CPU
        metrics.put("processCpuLoad",
                getAttr("java.lang:type=OperatingSystem", "ProcessCpuLoad"));

        metrics.put("systemCpuLoad",
                getAttr("java.lang:type=OperatingSystem", "SystemCpuLoad"));

        metrics.put("systemLoadAverage",
                getAttr("java.lang:type=OperatingSystem", "SystemLoadAverage"));

        // JVM THREADS
        metrics.put("threadCount",
                getAttr("java.lang:type=Threading", "ThreadCount"));

        metrics.put("daemonThreadCount",
                getAttr("java.lang:type=Threading", "DaemonThreadCount"));

        // GARBAGE COLLECTION
        metrics.put("gcYoungCollectionCount",
                getAttr("java.lang:type=GarbageCollector,name=G1 Young Generation", "CollectionCount"));

        metrics.put("gcYoungCollectionTime",
                getAttr("java.lang:type=GarbageCollector,name=G1 Young Generation", "CollectionTime"));

        metrics.put("gcOldCollectionCount",
                getAttr("java.lang:type=GarbageCollector,name=G1 Old Generation", "CollectionCount"));

        metrics.put("gcOldCollectionTime",
                getAttr("java.lang:type=GarbageCollector,name=G1 Old Generation", "CollectionTime"));

        requestBody.put("metrics",metrics);
        sender.sendJson(agentConfig.getEndpoint(), requestBody);
    }
}