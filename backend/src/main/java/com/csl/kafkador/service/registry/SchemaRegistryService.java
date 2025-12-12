package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import com.csl.kafkador.exception.ConfigNotFoundException;

import java.util.List;

public interface SchemaRegistryService {

    SchemaRegistryDto getSubjects(String clusterId);
}
