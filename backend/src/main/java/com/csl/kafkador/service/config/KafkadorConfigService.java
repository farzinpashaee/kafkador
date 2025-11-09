package com.csl.kafkador.service.config;

import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.KafkadorConfigNotFoundException;

import java.util.Map;

public interface KafkadorConfigService<T,I> {

    T get( String key, String clusterId ) throws KafkadorConfigNotFoundException;
    T save( I value, String clusterId );
    Map<String, KafkadorConfig> get(String clusterId);

}
