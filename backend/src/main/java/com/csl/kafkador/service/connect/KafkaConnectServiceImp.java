package com.csl.kafkador.service.connect;

import com.csl.kafkador.domain.dto.ConnectorCreateRequestDto;
import com.csl.kafkador.domain.dto.ConnectorDto;
import com.csl.kafkador.domain.dto.ConnectorPluginDto;
import com.csl.kafkador.domain.dto.ConnectorTaskDto;
import com.csl.kafkador.domain.dto.KafkaConnectConfigDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KafkaConnectApiException;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("KafkaConnectService")
@RequiredArgsConstructor
public class KafkaConnectServiceImp implements KafkaConnectService {

    private static final String CONFIG_KEY = "kafkador.kafka-connect.url";
    private static final String DEFAULT_PORT = "8083";

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;
    private final ClusterRepository clusterRepository;

    @Override
    public KafkaConnectConfigDto getConfig(String clusterId) {
        KafkaConnectConfigDto config = new KafkaConnectConfigDto();
        try {
            config.setUrl(resolveUrl(clusterId));
            config.setConfigured(true);
        } catch (ConfigNotFoundException e) {
            log.warn(e.toString());
        }
        return config;
    }

    /**
     * Returns the configured URL, or — if none is saved yet — probes the cluster's own
     * host on the default Kafka Connect port and, if a worker answers there, persists
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
                .filter(this::isConnectReachable)
                .orElse(null);
    }

    private boolean isConnectReachable(String candidateUrl) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    candidateUrl + "/",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Kafka Connect auto-discovery at {} failed: {}", candidateUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public KafkaConnectConfigDto saveConfig(String url, String clusterId) {
        String saved = kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(CONFIG_KEY, url), clusterId);
        return new KafkaConnectConfigDto().setUrl(saved).setConfigured(true);
    }

    @Override
    public List<ConnectorPluginDto> getPlugins(String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url + "/connector-plugins",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> plugins = response.getBody() == null ? Collections.emptyList() : response.getBody();
            return plugins.stream()
                    .map(p -> new ConnectorPluginDto()
                            .setClassName(asString(p.get("class")))
                            .setType(asString(p.get("type")))
                            .setVersion(asString(p.get("version"))))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error listing connector plugins: " + e.getMessage());
        }
    }

    @Override
    public List<ConnectorDto> getConnectors(String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            URI expandUri = UriComponentsBuilder.fromHttpUrl(url + "/connectors")
                    .queryParam("expand", "status")
                    .queryParam("expand", "info")
                    .build()
                    .toUri();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    expandUri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody() == null ? Collections.emptyMap() : response.getBody();
            return body.values().stream()
                    .map(v -> toConnectorDto(asMap(v)))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error listing connectors: " + e.getMessage());
        }
    }

    @Override
    public ConnectorDto getConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        return fetchConnectorDetail(name, url);
    }

    private ConnectorDto fetchConnectorDetail(String name, String url) throws KafkaConnectApiException {
        try {
            Map<String, Object> info = restTemplate.exchange(
                    url + "/connectors/" + name,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
            Map<String, Object> status = restTemplate.exchange(
                    url + "/connectors/" + name + "/status",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
            Map<String, Object> merged = new LinkedHashMap<>();
            if (info != null) merged.putAll(info);
            if (status != null) merged.put("status", status);
            return toConnectorDto(merged);
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error fetching connector '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Normalizes both shapes Kafka Connect returns into one DTO: the collection endpoint
     * nests each connector's own info under "info" (alongside a sibling "status"), while
     * a direct GET /connectors/{name} response has those same fields at the top level.
     */
    private ConnectorDto toConnectorDto(Map<String, Object> entry) {
        Map<String, Object> info = entry.containsKey("info") ? asMap(entry.get("info")) : entry;
        Map<String, Object> status = asMap(entry.get("status"));
        Map<String, Object> connectorStatus = asMap(status.get("connector"));

        Map<String, Object> configRaw = asMap(info.get("config"));
        Map<String, String> config = configRaw.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> asString(e.getValue()), (a, b) -> a, LinkedHashMap::new));

        List<Map<String, Object>> tasksRaw = asMapList(status.get("tasks"));
        List<ConnectorTaskDto> tasks = tasksRaw.stream()
                .map(t -> new ConnectorTaskDto()
                        .setId(asInt(t.get("id")))
                        .setState(asString(t.get("state")))
                        .setWorkerId(asString(t.get("worker_id")))
                        .setTrace(asString(t.get("trace"))))
                .collect(Collectors.toList());

        String name = asString(entry.getOrDefault("name", info.get("name")));
        String type = asString(info.getOrDefault("type", status.get("type")));

        return new ConnectorDto()
                .setName(name)
                .setType(type)
                .setState(asString(connectorStatus.get("state")))
                .setWorkerId(asString(connectorStatus.get("worker_id")))
                .setTrace(asString(connectorStatus.get("trace")))
                .setTasks(tasks)
                .setConfig(config);
    }

    @Override
    public ConnectorDto createConnector(ConnectorCreateRequestDto request, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = Map.of("name", request.getName(), "config", request.getConfig());
            restTemplate.exchange(
                    url + "/connectors",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error creating connector '" + request.getName() + "': " + e.getMessage());
        }
        return fetchConnectorDetail(request.getName(), url);
    }

    @Override
    public ConnectorDto updateConnectorConfig(String name, Map<String, String> config, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            restTemplate.exchange(
                    url + "/connectors/" + name + "/config",
                    HttpMethod.PUT,
                    new HttpEntity<>(config),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error updating connector '" + name + "': " + e.getMessage());
        }
        return fetchConnectorDetail(name, url);
    }

    @Override
    public void pauseConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        putAction(name, "pause", clusterId);
    }

    @Override
    public void resumeConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        putAction(name, "resume", clusterId);
    }

    @Override
    public void restartConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            restTemplate.postForEntity(url + "/connectors/" + name + "/restart", null, Void.class);
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error restarting connector '" + name + "': " + e.getMessage());
        }
    }

    private void putAction(String name, String action, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            restTemplate.put(url + "/connectors/" + name + "/" + action, null);
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error updating connector '" + name + "': " + e.getMessage());
        }
    }

    @Override
    public void deleteConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException {
        String url = resolveUrl(clusterId);
        try {
            restTemplate.delete(url + "/connectors/" + name);
        } catch (RestClientException e) {
            throw new KafkaConnectApiException("Error deleting connector '" + name + "': " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : Collections.emptyList();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

}
