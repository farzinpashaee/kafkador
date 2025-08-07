package com.csl.kafkador.service;

import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Event;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.exception.KafkadorException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service("SimpleProducerService")
public class SimpleProducerService implements ProducerService<String, String> {

    @Autowired
    SessionHolder sessionHolder;

    @Autowired
    ApplicationConfig applicationConfig;

    @Autowired
    ApplicationContext applicationContext;

    @Override
    public Properties getProperties() {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        return properties;
    }

    @Override
    public Event<String, String> produce(String topic, Event<String, String> event) throws KafkadorException {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(getProperties())) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getKey(), event.getValue());
            producer.send(record, (RecordMetadata metadata, Exception exception) -> {
                if (exception == null) {
                    System.out.printf("Message sent successfully to topic %s, partition %d, offset %d%n",
                            metadata.topic(), metadata.partition(), metadata.offset());
                } else {
                    exception.printStackTrace();
                }
            });
            producer.flush();
            return event;
        } catch (Exception e) {
            throw new KafkadorException(e.getMessage());
        }
    }

    @Override
    public Event<String, String> produce(String topic, String key, String value) {
        return null;
    }
}
