package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.ConsumerGroup;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.csl.kafkador.util.DtoMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service("ConsumerService")
@RequiredArgsConstructor
public class ConsumerService {

    private static final String DEFAULT_GROUP_ID_CONFIG_KEY = "kafkador.consumer.instance.id";

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;
    private final ConnectionService connectionService;
    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Returns the saved default consumer group id for the "Start Listening" quick-action
     * on the topic Test tab. The first call for a cluster generates and persists one;
     * every later call returns that same id, so repeat listens on any topic reuse it
     * unless the user explicitly picks a different group id from the Consumers page.
     */
    public String getOrCreateDefaultGroupId(String clusterId) {
        try {
            return kafkadorConfigService.get(DEFAULT_GROUP_ID_CONFIG_KEY, clusterId);
        } catch (ConfigNotFoundException e) {
            String generated = "kafkador-" + UUID.randomUUID();
            kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(DEFAULT_GROUP_ID_CONFIG_KEY, generated), clusterId);
            return generated;
        }
    }

    public Properties getProperties(String groupId) {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return properties;
    }

    public Collection<ConsumerGroup> getConsumersGroup(String clusterId) throws KafkaAdminApiException {

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            KafkaFuture<Map<String, ConsumerGroupDescription>> consumerDescribedFuture = admin.describeConsumerGroups(
                    consumersFuture.get().stream().map(i -> i.groupId()).collect(Collectors.toList())).all();

            return consumerDescribedFuture.get().entrySet().stream()
                    .map(i -> DtoMapper.consumerGroupDescriptionMapper(i.getValue()))
                    .collect(Collectors.toList());
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list consumer groups for cluster {}", clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public SseEmitter consume(String topic, String groupId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        AtomicBoolean running = new AtomicBoolean(true);

        emitter.onCompletion(() -> running.set(false));
        emitter.onError(e -> running.set(false));
        emitter.onTimeout(() -> {
            running.set(false);
            emitter.complete();
        });

        Properties properties = getProperties(groupId);
        executor.execute(() -> {
            log.info("Kafka consumer '{}' started. Listening to topic: {}", groupId, topic);
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList(topic));
                while (running.get()) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                        for (ConsumerRecord<String, String> record : records) {
                            emitter.send("Consumed message: key = " + record.key()
                                    + ", value = " + record.value()
                                    + ", offset = " + record.offset());
                        }
                    } catch (Exception e) {
                        log.error("Error while consuming from topic {}", topic, e);
                        emitter.completeWithError(e);
                        running.set(false);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to create Kafka consumer for topic {}", topic, e);
                emitter.completeWithError(e);
            }
            log.info("Kafka consumer stopped for topic: {}", topic);
        });

        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ConsumerService executor did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
