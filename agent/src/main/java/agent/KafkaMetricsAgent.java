package agent;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KafkaMetricsAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[KafkaAgent] Starting Kafka Metrics Agent...");
        
        String endpoint = (agentArgs != null && !agentArgs.isBlank())
                ? agentArgs
                : "http://localhost:8080/metrics/ingest";

        KafkaMetricsCollector collector = new KafkaMetricsCollector(endpoint);

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> {
                    try {
                        collector.collectAndSend();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 0, 5, TimeUnit.SECONDS); // send every 5s
    }
}