package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaDto;
import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("SchemaRegistryService")
@RequiredArgsConstructor

public class SchemaRegistryServiceImp implements SchemaRegistryService {

    private static final String CONFIG_KEY = "kafkador.schema-registry.url";
    private static final String DEFAULT_PORT = "8081";

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String,Map.Entry<String,String>> kafkadorConfigService;
    private final ClusterRepository clusterRepository;

    @Override
    public SchemaRegistryDto getSubjects(String clusterId) {
        String url = null;
        SchemaRegistryDto schemaRegistry = new SchemaRegistryDto();
        try {
            url = resolveUrl(clusterId);
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

    @Override
    public SchemaRegistryConfigDto getConfig(String clusterId) {
        SchemaRegistryConfigDto config = new SchemaRegistryConfigDto();
        try {
            config.setUrl(resolveUrl(clusterId));
            config.setConfigured(true);
        } catch (ConfigNotFoundException e) {
            log.warn(e.toString());
        }
        return config;
    }

    @Override
    public SchemaRegistryConfigDto saveConfig(String url, String clusterId) {
        String saved = kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(CONFIG_KEY, url), clusterId);
        return new SchemaRegistryConfigDto().setUrl(saved).setConfigured(true);
    }

    /**
     * Returns the configured URL, or — if none is saved yet — probes the cluster's own
     * host on the default Schema Registry port and, if a server answers there, persists
     * that as the config so the user isn't asked to set it up manually.
     */
    private String resolveUrl(String clusterId) throws ConfigNotFoundException {
        try {
            return kafkadorConfigService.get(CONFIG_KEY, clusterId);
        } catch (ConfigNotFoundException e) {
            String discovered = discoverUrl(clusterId);
            if (discovered == null) throw e;
            kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(CONFIG_KEY, discovered), clusterId);
            return discovered;
        }
    }

    private String discoverUrl(String clusterId) {
        return clusterRepository.findByClusterId(clusterId)
                .map(Cluster::getHost)
                .map(host -> "http://" + host + ":" + DEFAULT_PORT)
                .filter(this::isSchemaRegistryReachable)
                .orElse(null);
    }

    private boolean isSchemaRegistryReachable(String candidateUrl) {
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    candidateUrl + "/subjects",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Schema Registry auto-discovery at {} failed: {}", candidateUrl, e.getMessage());
            return false;
        }
    }

}
