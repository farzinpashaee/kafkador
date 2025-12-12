package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaDto;
import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("SchemaRegistryService")
@RequiredArgsConstructor

public class SchemaRegistryServiceImp implements SchemaRegistryService {

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String,Map.Entry<String,String>> kafkadorConfigService;

    @Override
    public SchemaRegistryDto getSubjects(String clusterId) {
        String url = null;
        SchemaRegistryDto schemaRegistry = new SchemaRegistryDto();
        try {
            url = kafkadorConfigService.get("kafkador.schema-registry.url",clusterId);
            schemaRegistry.setConfigured(true);
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url + "/subjects",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            schemaRegistry.setSubjects(
                    response.getBody().stream()
                            .map(i -> {
                                SchemaDto dto = new SchemaDto();
                                SchemaDto schemaDto = dto.setName(i);
                                return dto;
                            })
                            .collect(Collectors.toList())
            );
        } catch (ConfigNotFoundException e) {
            log.warn(e.toString());
        }
        return schemaRegistry;
    }


}
