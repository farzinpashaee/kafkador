package com.csl.kafkador.service.registry;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import com.csl.kafkador.exception.KafkadorConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service("SchemaRegistryService")
@RequiredArgsConstructor
public class SchemaRegistryServiceImp implements SchemaRegistryService {

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String,Map.Entry<String,String>> kafkadorConfigService;

    @Override
    public List<String> getSubjects(String clusterId) throws KafkadorConfigNotFoundException {
        String url = kafkadorConfigService.get("kafkador.schema-registry.url",clusterId);
        ResponseEntity<List<String>> response = restTemplate.exchange(
                url + "/subjects",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
        );
        return response.getBody();
    }


}
