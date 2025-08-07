package com.csl.kafkador.dto;

import lombok.Data;

@Data
public class Event<K,V> {
    private K key;
    private V value;
}
