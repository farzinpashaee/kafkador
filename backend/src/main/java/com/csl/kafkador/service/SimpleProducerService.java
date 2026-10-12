package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.Event;
import com.csl.kafkador.exception.KafkadorException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service("SimpleProducerService")
@RequiredArgsConstructor
public class SimpleProducerService implements ProducerService<String, String> {

    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final ApplicationConfig applicationConfig;
    private final ApplicationContext applicationContext;

    @Override
    public Properties getProperties() {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        Properties properties = connectionService.getActiveConnectionProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        return properties;
    }

    @Override
    public Event<String, String> produce(String topic, Event<String, String> event) throws KafkadorException {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(getProperties())) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getKey(), event.getValue());
            // Wait for the broker's acknowledgement so a failed send reaches the caller instead of only the log.
            producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return event;
        } catch (ExecutionException e) {
            throw new KafkadorException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        } catch (Exception e) {
            throw new KafkadorException(e.getMessage());
        }
    }

    @Override
    public Event<String, String> produce(String topic, String key, String value) {
        return null;
    }
}
