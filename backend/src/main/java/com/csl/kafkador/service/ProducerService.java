package com.csl.kafkador.service;

import com.csl.kafkador.domain.Event;
import com.csl.kafkador.exception.KafkadorException;

import java.util.Properties;

public interface ProducerService<K,V> {

    Properties getProperties();
    Event<K,V> produce(String topic, Event<K,V> event) throws KafkadorException;
    Event<K,V> produce( String topic, K key, V value );

}
