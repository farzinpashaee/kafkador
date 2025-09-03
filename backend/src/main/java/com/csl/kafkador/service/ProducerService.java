package com.csl.kafkador.service;

import com.csl.kafkador.dto.Event;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.exception.KafkadorException;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

public interface ProducerService<K,V> {

    Properties getProperties();
    Event<K,V> produce(String topic, Event<K,V> event) throws KafkadorException;
    Event<K,V> produce( String topic, K key, V value );

}
