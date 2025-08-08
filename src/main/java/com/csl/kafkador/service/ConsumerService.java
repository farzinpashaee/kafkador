package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service("ConsumerService")
public class ConsumerService {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ApplicationConfig applicationConfig;

    private final ExecutorService executor = Executors.newCachedThreadPool();


    public Properties getProperties() {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkador");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // or "latest"

        return properties;
    }

    public Collection<ConsumerGroupListing> getConsumersGroup(Request request) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            return consumersFuture.get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public SseEmitter consume(String topic) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Long-lived connection
        Properties properties = getProperties();
        executor.execute(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList(topic));
                System.out.println("Kafka consumer started. Listening to topic: " + topic);
                while (true) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                        for (ConsumerRecord<String, String> record : records) {
                            emitter.send("Consumed message: key = " + record.key() + ", value = " + record.value() + ", offset = " + record.offset());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        return emitter;
    }

}
