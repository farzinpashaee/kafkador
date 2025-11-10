package com.csl.kafkador.service.registry;

import com.csl.kafkador.exception.KafkadorConfigNotFoundException;

import java.util.List;

public interface SchemaRegistryService {

    List<String> getSubjects(String clusterId) throws KafkadorConfigNotFoundException;
}
