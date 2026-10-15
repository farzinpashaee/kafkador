package com.csl.kafkador.service.ksqldb;

import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
import com.csl.kafkador.domain.dto.KsqlQueryDto;
import com.csl.kafkador.domain.dto.KsqlServerInfoDto;
import com.csl.kafkador.domain.dto.KsqlSourceDefinitionDto;
import com.csl.kafkador.domain.dto.KsqlStatementRequestDto;
import com.csl.kafkador.domain.dto.KsqlStatementResultDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KsqlDbApiException;
import com.csl.kafkador.exception.KsqlStatementException;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.csl.kafkador.util.KsqlStatements;
import com.csl.kafkador.util.KsqlStatements.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    private final ObjectMapper objectMapper;

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

    @Override
    public KsqlSourceDefinitionDto getSourceDefinition(SourceType type, String name, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException {
        Map<String, Object> entity = executeUserStatement("DESCRIBE " + KsqlStatements.quoteIdentifier(name) + ";",
                Collections.emptyMap(), clusterId);
        Map<String, Object> description = asMap(entity.get("sourceDescription"));
        if (description.isEmpty()) throw new KsqlStatementException(lower(type) + " " + name + " does not exist");
        String actualType = asString(description.get("type"));
        if (actualType != null && !actualType.equalsIgnoreCase(type.name())) {
            throw new KsqlStatementException(name + " is a " + actualType.toLowerCase(Locale.ROOT) + ", not a " + lower(type));
        }
        return new KsqlSourceDefinitionDto()
                .setName(asString(description.get("name")))
                .setType(actualType)
                .setTopic(asString(description.get("topic")))
                .setKeyFormat(asString(description.get("keyFormat")))
                .setValueFormat(asString(description.get("valueFormat")))
                .setStatement(asString(description.get("statement")))
                .setReadQueries(queryIds(description.get("readQueries")))
                .setWriteQueries(queryIds(description.get("writeQueries")));
    }

    @Override
    public KsqlStatementResultDto createSource(SourceType type, KsqlStatementRequestDto request, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException {
        String statement = KsqlStatements.requireCreate(type, request.getStatement());
        return statementResult(executeUserStatement(statement, streamsProperties(request), clusterId));
    }

    @Override
    public KsqlStatementResultDto updateSource(SourceType type, String name, KsqlStatementRequestDto request, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException {
        String statement = KsqlStatements.requireUpdate(type, name, request.getStatement());
        return statementResult(executeUserStatement(statement, streamsProperties(request), clusterId));
    }

    @Override
    public KsqlStatementResultDto dropSource(SourceType type, String name, boolean deleteTopic, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException {
        String statement = "DROP " + type.name() + " " + KsqlStatements.quoteIdentifier(name) + (deleteTopic ? " DELETE TOPIC" : "") + ";";
        return statementResult(executeUserStatement(statement, Collections.emptyMap(), clusterId));
    }

    private static Map<String, Object> streamsProperties(KsqlStatementRequestDto request) {
        return request.isFromEarliest() ? Map.of("ksql.streams.auto.offset.reset", "earliest") : Collections.emptyMap();
    }

    private KsqlStatementResultDto statementResult(Map<String, Object> entity) throws KsqlStatementException {
        Map<String, Object> commandStatus = asMap(entity.get("commandStatus"));
        String status = asString(commandStatus.get("status"));
        String message = asString(commandStatus.get("message"));
        if ("ERROR".equalsIgnoreCase(status)) {
            throw new KsqlStatementException(message == null ? "ksqlDB could not run the statement" : message);
        }
        String queryId = asString(commandStatus.get("queryId"));
        return new KsqlStatementResultDto().setStatus(status).setMessage(message)
                .setQueryId(queryId != null ? queryId : asString(entity.get("queryId")));
    }

    /** IDs of the queries in a DESCRIBE field, which lists them as objects or, on older servers, as plain ids. */
    private List<String> queryIds(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Object query : list) {
            String id = query instanceof Map ? asString(asMap(query).get("id")) : asString(query);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    /**
     * Runs a statement the user wrote. When ksqlDB rejects it (a 4xx), its own message says what's wrong, such as a
     * syntax error or queries still reading from a source, and is safe to show; anything else is a connection
     * problem whose details stay in the log.
     */
    private Map<String, Object> executeUserStatement(String statement, Map<String, Object> properties, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException {
        String url = resolveUrl(clusterId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(KSQL_MEDIA_TYPE);
            Map<String, Object> body = Map.of("ksql", statement, "streamsProperties", properties);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url + "/ksql",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> results = response.getBody();
            return results == null || results.isEmpty() ? Collections.emptyMap() : results.get(0);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new KsqlStatementException(errorMessage(e.getResponseBodyAsString()));
            }
            log.error("ksqlDB failed to run a statement", e);
            throw new KsqlDbApiException("Error calling ksqlDB: " + e.getStatusCode());
        } catch (RestClientException e) {
            log.error("ksqlDB failed to run a statement", e);
            throw new KsqlDbApiException("Error calling ksqlDB: " + e.getMessage());
        }
    }

    private String errorMessage(String body) {
        try {
            Object message = objectMapper.readValue(body, Map.class).get("message");
            if (message != null && !message.toString().isBlank()) return message.toString();
        } catch (Exception e) {
            log.debug("Could not parse ksqlDB error body: {}", body);
        }
        return "ksqlDB rejected the statement";
    }

    private static String lower(SourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
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
