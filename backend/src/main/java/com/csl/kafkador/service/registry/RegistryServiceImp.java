package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.KafkadorConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("RegistryService")
@RequiredArgsConstructor
public class RegistryServiceImp implements RegistryService {

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String,Map.Entry<String,String>> kafkadorConfigService;

    List<SchemaDto> getSchemas(String clusterId) throws KafkadorConfigNotFoundException {
        List<SchemaDto> schemas = new ArrayList<>();
        // check if config already exists
        String url = kafkadorConfigService.get("kafkador.cluster.schema-registry.url",clusterId);

        return schemas;
    }

}
