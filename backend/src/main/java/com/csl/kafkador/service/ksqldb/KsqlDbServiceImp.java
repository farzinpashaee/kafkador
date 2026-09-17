package com.csl.kafkador.service.ksqldb;

import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
import com.csl.kafkador.domain.dto.KsqlQueryDto;
import com.csl.kafkador.domain.dto.KsqlServerInfoDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KsqlDbApiException;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("KsqlDbService")
@RequiredArgsConstructor
public class KsqlDbServiceImp implements KsqlDbService {

    private static final String CONFIG_KEY = "kafkador.ksql-db.url";
    private static final String DEFAULT_PORT = "8088";
    private static final MediaType KSQL_MEDIA_TYPE = MediaType.valueOf("application/vnd.ksql.v1+json; charset=utf-8");

    private final RestTemplate restTemplate;
    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;
    private final ClusterRepository clusterRepository;

    @Override
    public KsqlDbConfigDto getConfig(String clusterId) {
        KsqlDbConfigDto config = new KsqlDbConfigDto();
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
     * host on the default ksqlDB port and, if a server answers there, persists that as
     * the config so the user isn't asked to set it up manually.
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
                .filter(this::isKsqlDbReachable)
                .orElse(null);
    }

    private boolean isKsqlDbReachable(String candidateUrl) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    candidateUrl + "/info",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("ksqlDB auto-discovery at {} failed: {}", candidateUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public KsqlDbConfigDto saveConfig(String url, String clusterId) {
        String saved = kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(CONFIG_KEY, url), clusterId);
        return new KsqlDbConfigDto().setUrl(saved).setConfigured(true);
    }

    @Override
    public KsqlServerInfoDto getServerInfo(String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        String url = resolveUrl(clusterId);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url + "/info",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            Map<String, Object> serverInfo = body == null ? Collections.emptyMap() : asMap(body.get("KsqlServerInfo"));
            return new KsqlServerInfoDto()
                    .setVersion(asString(serverInfo.get("version")))
                    .setKafkaClusterId(asString(serverInfo.get("kafkaClusterId")))
                    .setKsqlServiceId(asString(serverInfo.get("ksqlServiceId")))
                    .setServerStatus(asString(serverInfo.get("serverStatus")));
        } catch (RestClientException e) {
            throw new KsqlDbApiException("Error calling ksqlDB info endpoint: " + e.getMessage());
        }
    }

    @Override
    public List<KsqlStreamDto> getStreams(String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        Map<String, Object> entity = executeStatement("SHOW STREAMS;", clusterId);
        List<Map<String, Object>> streams = asMapList(entity.get("streams"));
        return streams.stream()
                .map(s -> new KsqlStreamDto()
                        .setName(asString(s.get("name")))
                        .setTopic(asString(s.get("topic")))
                        .setKeyFormat(asString(s.get("keyFormat")))
                        .setValueFormat(asString(s.get("valueFormat"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<KsqlTableDto> getTables(String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        Map<String, Object> entity = executeStatement("SHOW TABLES;", clusterId);
        List<Map<String, Object>> tables = asMapList(entity.get("tables"));
        return tables.stream()
                .map(t -> new KsqlTableDto()
                        .setName(asString(t.get("name")))
                        .setTopic(asString(t.get("topic")))
                        .setKeyFormat(asString(t.get("keyFormat")))
                        .setValueFormat(asString(t.get("valueFormat"))))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<KsqlQueryDto> getQueries(String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        Map<String, Object> entity = executeStatement("SHOW QUERIES;", clusterId);
        List<Map<String, Object>> queries = asMapList(entity.get("queries"));
        return queries.stream()
                .map(q -> new KsqlQueryDto()
                        .setId(asString(q.get("id")))
                        .setQueryType(asString(q.get("queryType")))
                        .setStatus(queryStatus(q))
                        .setSinks((List<String>) q.getOrDefault("sinks", Collections.emptyList()))
                        .setSources((List<String>) q.getOrDefault("sources", Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    public void terminateQuery(String queryId, String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        executeStatement("TERMINATE " + queryId + ";", clusterId);
    }

    private Map<String, Object> executeStatement(String statement, String clusterId) throws ConfigNotFoundException, KsqlDbApiException {
        String url = resolveUrl(clusterId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(KSQL_MEDIA_TYPE);
            Map<String, Object> body = Map.of("ksql", statement, "streamsProperties", Collections.emptyMap());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url + "/ksql",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> results = response.getBody();
            if (results == null || results.isEmpty()) return Collections.emptyMap();
            return results.get(0);
        } catch (RestClientException e) {
            throw new KsqlDbApiException("Error calling ksqlDB '" + statement + "': " + e.getMessage());
        }
    }

    private String queryStatus(Map<String, Object> query) {
        if (query.get("state") != null) return asString(query.get("state"));
        Map<String, Object> statusCount = asMap(query.get("statusCount"));
        return statusCount.isEmpty() ? null : String.join(", ", statusCount.keySet());
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

}
