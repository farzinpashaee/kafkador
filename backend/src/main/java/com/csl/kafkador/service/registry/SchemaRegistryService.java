package com.csl.kafkador.service.registry;

import com.csl.kafkador.exception.ConfigNotFoundException;

import java.util.List;

public interface SchemaRegistryService {

    List<String> getSubjects(String clusterId) throws ConfigNotFoundException;
}
