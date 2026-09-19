package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.CompatibilityCheckResultDto;
import com.csl.kafkador.domain.dto.CompatibilityConfigDto;
import com.csl.kafkador.domain.dto.SchemaDto;
import com.csl.kafkador.domain.dto.SchemaLocationDto;
import com.csl.kafkador.domain.dto.SchemaLookupResultDto;
import com.csl.kafkador.domain.dto.SchemaRegisterRequestDto;
import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import com.csl.kafkador.domain.dto.SchemaVersionDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.SchemaRegistryApiException;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper;
    private final KafkadorConfigService<String,Map.Entry<String,String>> kafkadorConfigService;
    private final ClusterRepository clusterRepository;

    @Override
    public SchemaRegistryDto getSubjects(String clusterId) {
        SchemaRegistryDto schemaRegistry = new SchemaRegistryDto();
        try {
            String url = resolveUrl(clusterId);
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

    @Override
    public List<Integer> getVersions(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            ResponseEntity<List<Integer>> response = restTemplate.exchange(
                    url + "/subjects/" + encode(subject) + "/versions",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Integer>>() {}
            );
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (RestClientException e) {
            throw wrap(e, "Error listing versions for subject '" + subject + "'");
        }
    }

    @Override
    public SchemaVersionDto getVersion(String subject, String version, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = restTemplate.exchange(
                    url + "/subjects/" + encode(subject) + "/versions/" + version,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
            return toSchemaVersionDto(body);
        } catch (RestClientException e) {
            throw wrap(e, "Error fetching version '" + version + "' of subject '" + subject + "'");
        }
    }

    @Override
    public SchemaVersionDto registerSchema(String subject, SchemaRegisterRequestDto request, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schema", request.getSchema());
            if (request.getSchemaType() != null) body.put("schemaType", request.getSchemaType());
            restTemplate.exchange(
                    url + "/subjects/" + encode(subject) + "/versions",
                    HttpMethod.POST, new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
        } catch (RestClientException e) {
            throw wrap(e, "Error registering schema for subject '" + subject + "'");
        }
        return getVersion(subject, "latest", clusterId);
    }

    @Override
    public CompatibilityCheckResultDto checkCompatibility(String subject, SchemaRegisterRequestDto request, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schema", request.getSchema());
            if (request.getSchemaType() != null) body.put("schemaType", request.getSchemaType());
            URI uri = UriComponentsBuilder.fromHttpUrl(url + "/compatibility/subjects/" + encode(subject) + "/versions/latest")
                    .queryParam("verbose", "true")
                    .build(true)
                    .toUri();
            Map<String, Object> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (response == null) return new CompatibilityCheckResultDto().setCompatible(false);
            boolean compatible = Boolean.TRUE.equals(response.get("is_compatible"));
            return new CompatibilityCheckResultDto().setCompatible(compatible).setMessages(asStringList(response.get("messages")));
        } catch (HttpClientErrorException.NotFound e) {
            // No existing versions (or subject) to conflict with yet.
            return new CompatibilityCheckResultDto().setCompatible(true);
        } catch (RestClientException e) {
            throw wrap(e, "Error checking compatibility for subject '" + subject + "'");
        }
    }

    @Override
    public List<Integer> deleteSubject(String subject, boolean permanent, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(url + "/subjects/" + encode(subject))
                    .queryParam("permanent", permanent)
                    .build(true)
                    .toUri();
            ResponseEntity<List<Integer>> response = restTemplate.exchange(uri, HttpMethod.DELETE, null,
                    new ParameterizedTypeReference<List<Integer>>() {});
            return response.getBody() == null ? Collections.emptyList() : response.getBody();
        } catch (RestClientException e) {
            throw wrap(e, "Error deleting subject '" + subject + "'");
        }
    }

    @Override
    public void deleteVersion(String subject, String version, boolean permanent, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(url + "/subjects/" + encode(subject) + "/versions/" + version)
                    .queryParam("permanent", permanent)
                    .build(true)
                    .toUri();
            restTemplate.exchange(uri, HttpMethod.DELETE, null, Void.class);
        } catch (RestClientException e) {
            throw wrap(e, "Error deleting version '" + version + "' of subject '" + subject + "'");
        }
    }

    @Override
    public CompatibilityConfigDto getGlobalCompatibility(String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = restTemplate.exchange(url + "/config", HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return new CompatibilityConfigDto().setLevel(body == null ? null : asString(body.get("compatibilityLevel")));
        } catch (RestClientException e) {
            throw wrap(e, "Error fetching global compatibility level");
        }
    }

    @Override
    public CompatibilityConfigDto saveGlobalCompatibility(CompatibilityConfigDto config, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = Map.of("compatibility", config.getLevel());
            Map<String, Object> response = restTemplate.exchange(url + "/config", HttpMethod.PUT, new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return new CompatibilityConfigDto().setLevel(response == null ? config.getLevel() : asString(response.get("compatibility")));
        } catch (RestClientException e) {
            throw wrap(e, "Error saving global compatibility level");
        }
    }

    @Override
    public CompatibilityConfigDto getSubjectCompatibility(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = restTemplate.exchange(url + "/config/" + encode(subject), HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return new CompatibilityConfigDto().setLevel(body == null ? null : asString(body.get("compatibilityLevel")));
        } catch (HttpClientErrorException.NotFound e) {
            return new CompatibilityConfigDto().setLevel(null);
        } catch (RestClientException e) {
            throw wrap(e, "Error fetching compatibility level for subject '" + subject + "'");
        }
    }

    @Override
    public CompatibilityConfigDto saveSubjectCompatibility(String subject, CompatibilityConfigDto config, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> body = Map.of("compatibility", config.getLevel());
            Map<String, Object> response = restTemplate.exchange(url + "/config/" + encode(subject), HttpMethod.PUT, new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return new CompatibilityConfigDto().setLevel(response == null ? config.getLevel() : asString(response.get("compatibility")));
        } catch (RestClientException e) {
            throw wrap(e, "Error saving compatibility level for subject '" + subject + "'");
        }
    }

    @Override
    public void clearSubjectCompatibility(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            restTemplate.exchange(url + "/config/" + encode(subject), HttpMethod.DELETE, null, Void.class);
        } catch (RestClientException e) {
            throw wrap(e, "Error clearing compatibility override for subject '" + subject + "'");
        }
    }

    @Override
    public SchemaLookupResultDto lookupById(Integer id, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException {
        String url = resolveUrl(clusterId);
        try {
            Map<String, Object> schemaBody = restTemplate.exchange(url + "/schemas/ids/" + id, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

            List<Map<String, Object>> versions;
            try {
                ResponseEntity<List<Map<String, Object>>> versionsResponse = restTemplate.exchange(
                        url + "/schemas/ids/" + id + "/versions", HttpMethod.GET, null,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                versions = versionsResponse.getBody() == null ? Collections.emptyList() : versionsResponse.getBody();
            } catch (RestClientException e) {
                // Older registry versions don't expose this endpoint; the schema itself still resolved.
                versions = Collections.emptyList();
            }

            List<SchemaLocationDto> locations = versions.stream()
                    .map(v -> new SchemaLocationDto().setSubject(asString(v.get("subject"))).setVersion(asInt(v.get("version"))))
                    .collect(Collectors.toList());

            return new SchemaLookupResultDto()
                    .setId(id)
                    .setSchemaType(schemaBody == null ? null : asString(schemaBody.getOrDefault("schemaType", "AVRO")))
                    .setSchema(schemaBody == null ? null : asString(schemaBody.get("schema")))
                    .setLocations(locations);
        } catch (RestClientException e) {
            throw wrap(e, "Error looking up schema id " + id);
        }
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

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private SchemaVersionDto toSchemaVersionDto(Map<String, Object> body) {
        if (body == null) return new SchemaVersionDto();
        return new SchemaVersionDto()
                .setSubject(asString(body.get("subject")))
                .setVersion(asInt(body.get("version")))
                .setId(asInt(body.get("id")))
                .setSchemaType(asString(body.getOrDefault("schemaType", "AVRO")))
                .setSchema(asString(body.get("schema")));
    }

    /**
     * Wraps a failed Schema Registry call. For a 4xx response, the registry's own body
     * ({"error_code": ..., "message": "..."}) is almost always the actionable reason
     * (invalid schema syntax, incompatible change, unknown subject/version) and is safe
     * to show as-is. Anything else (connection refused, timeout, 5xx) is a plumbing
     * failure whose exception message can embed internal hosts/ports, so only the
     * caller-supplied context — never e.getMessage() — reaches the client; the raw
     * exception is still logged server-side for debugging.
     */
    private SchemaRegistryApiException wrap(RestClientException e, String context) {
        if (e instanceof HttpStatusCodeException) {
            String body = ((HttpStatusCodeException) e).getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                try {
                    Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
                    Object message = parsed.get("message");
                    if (message != null) return new SchemaRegistryApiException(message.toString());
                } catch (Exception parseFailure) {
                    log.debug("Could not parse Schema Registry error body: {}", body);
                }
            }
        }
        log.error("{}", context, e);
        return new SchemaRegistryApiException(context + ". Please try again.");
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        return ((List<Object>) value).stream().map(this::asString).collect(Collectors.toList());
    }

}
