package agent;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

public class KafkaMetricsCollector {

    private final MBeanServerConnection mbean;
    private final HttpSender sender;
    private final String endpoint;

    public KafkaMetricsCollector(String endpoint) {
        this.mbean = ManagementFactory.getPlatformMBeanServer();
        this.sender = new HttpSender();
        this.endpoint = endpoint;
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

    public void collectAndSend() throws Exception {

        Map<String, Object> metrics = new HashMap<>();

        // Kafka throughput
        metrics.put("messagesInPerSec",
            getRate("kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec"));
        metrics.put("bytesInPerSec",
            getRate("kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec"));
        metrics.put("bytesOutPerSec",
            getRate("kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec"));

        // Kafka replica status
        metrics.put("underReplicatedPartitions",
            getValue("kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions"));
        metrics.put("offlinePartitions",
            getValue("kafka.controller:type=KafkaController,name=OfflinePartitionsCount"));

        // JVM Metrics
        CompositeData heap = (CompositeData) get("java.lang:type=Memory", "HeapMemoryUsage");
        metrics.put("heapUsed", heap.get("used"));
        metrics.put("heapMax", heap.get("max"));

        // Send JSON to endpoint
        sender.sendJson(endpoint, metrics);
    }
}