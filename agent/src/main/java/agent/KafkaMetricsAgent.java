package agent;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KafkaMetricsAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[KafkaAgent] Starting Kafka Metrics Agent...");

        AgentConfig agentConfig = new AgentConfig(agentArgs);
        KafkaMetricsCollector collector = new KafkaMetricsCollector(agentConfig);

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> {
                    try {
                        collector.collectAndSend( agentConfig );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 0, agentConfig.getPeriod(), TimeUnit.SECONDS); // send every 5s
    }
}