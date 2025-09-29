package com.csl.kafkador.service;

import com.csl.kafkador.exception.KafkadorConfigNotFoundException;

public interface KafkadorConfigService<T,I> {

    T get( String key ) throws KafkadorConfigNotFoundException;
    T save( I value );

}
