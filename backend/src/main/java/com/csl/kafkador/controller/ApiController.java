package com.csl.kafkador.controller;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.*;
import com.csl.kafkador.domain.dto.*;
import com.csl.kafkador.domain.model.Agent;
import com.csl.kafkador.exception.*;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.service.*;
import com.csl.kafkador.service.agent.AgentService;
import com.csl.kafkador.service.alert.AlertService;
import com.csl.kafkador.service.ksqldb.KsqlDbService;
import com.csl.kafkador.service.registry.SchemaRegistryService;
import com.csl.kafkador.service.search.SearchService;
import com.csl.kafkador.util.MetricEnum;
import com.csl.kafkador.util.TimeUnitEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ApiController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;
    private final ConnectionService connectionService;
    private final MetricService metricService;

    @GetMapping("/cluster")
    public ResponseEntity<GenericResponse<ClusterDto>> getCluster() throws KafkaAdminApiException, ClusterNotFoundException {
        ClusterService clusterService = (ClusterService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CLUSTER));
        ConnectionDto connection = connectionService.getActiveConnection();
        ClusterDto cluster = clusterService.getClusterDetails(connection.getClusterId());
        return new GenericResponse.Builder<ClusterDto>()
                .data(cluster)
                .success(HttpStatus.OK);
    }

    @GetMapping("/brokers/{id}")
    public ResponseEntity<GenericResponse<BrokerDto>> getBroker(@PathVariable @NotBlank String id) throws KafkaAdminApiException, BrokerNotFoundException {
        BrokerService brokerService = (BrokerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        ConnectionDto connection = connectionService.getActiveConnection();
        BrokerDto broker = brokerService.getDetail(connection.getClusterId(), id);
        return new GenericResponse.Builder<BrokerDto>()
                .data(broker)
                .success(HttpStatus.OK);
    }

    @PutMapping("/brokers/{id}/config")
    public ResponseEntity<Void> updateBrokerConfig(@PathVariable @NotBlank String id, @Valid @RequestBody ConfigEntry configEntry) throws KafkaAdminApiException, BrokerNotFoundException {
        BrokerService brokerService = (BrokerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        ConnectionDto connection = connectionService.getActiveConnection();
        brokerService.updateConfig(connection.getClusterId(), id, configEntry);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics")
    public ResponseEntity<GenericResponse<List<Topic>>> getTopics(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                    @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) int size)
            throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        Collection<Topic> topics = topicService.getTopics(connection.getClusterId());
        return new GenericResponse.Builder<List<Topic>>()
                .data(paginate(topics, page, size))
                .success(HttpStatus.OK);
    }

    @PutMapping("/topics/{name}/config")
    public ResponseEntity<Void> updateTopicConfig(@PathVariable @NotBlank String name, @Valid @RequestBody ConfigEntry configEntry) throws KafkaAdminApiException, BrokerNotFoundException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        topicService.updateConfig(connection.getClusterId(), name, configEntry);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics/{name}")
    public ResponseEntity<GenericResponse<Topic>> getTopic(@PathVariable @NotBlank String name) throws KafkaAdminApiException, TopicNotFoundException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        Topic topic = topicService.getTopic(connection.getClusterId(), name);
        return new GenericResponse.Builder<Topic>()
                .data(topic)
                .success(HttpStatus.OK);
    }

    @PostMapping("/topics")
    public ResponseEntity<GenericResponse<Topic>> createTopic(@Valid @RequestBody TopicCreateRequestDto request)
            throws KafkaAdminApiException, TopicAlreadyExistsException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        Topic topic = new Topic()
                .setName(request.getName())
                .setPartitions(request.getPartitions())
                .setReplicatorFactor(request.getReplicatorFactor());
        Topic created = topicService.createTopic(connection.getClusterId(), topic);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{name}")
                .buildAndExpand(created.getName())
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        return new GenericResponse.Builder<Topic>()
                .data(created)
                .success(HttpStatus.CREATED, headers);
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> deleteTopic(@PathVariable @NotBlank String name) throws KafkaAdminApiException, TopicNotFoundException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        log.info("Deleting topic '{}' on cluster {}", name, connection.getClusterId());
        topicService.deleteTopic(connection.getClusterId(), name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections")
    public ResponseEntity<GenericResponse<ConnectionDto>> createConnection(@Valid @RequestBody ConnectionDto connection)
            throws KafkaAdminApiException, DuplicatedClusterException {
        ConnectionDto created = connectionService.create(connection);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.getClusterId())
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(location);
        return new GenericResponse.Builder<ConnectionDto>()
                .data(created)
                .success(HttpStatus.CREATED, headers);
    }

    @PutMapping("/connections/{id}")
    public ResponseEntity<GenericResponse<ConnectionDto>> updateConnection(@PathVariable @NotBlank String id, @Valid @RequestBody ConnectionDto connection)
            throws ClusterNotFoundException, KafkaAdminApiException, DuplicatedClusterException {
        ConnectionDto updated = connectionService.update(id, connection);
        return new GenericResponse.Builder<ConnectionDto>()
                .data(updated)
                .success(HttpStatus.OK);
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable @NotBlank String id) throws ClusterNotFoundException {
        log.info("Deleting connection {}", id);
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connections")
    public ResponseEntity<GenericResponse<List<ConnectionDto>>> getConnections() {
        return new GenericResponse.Builder<List<ConnectionDto>>()
                .data(connectionService.getConnections())
                .success(HttpStatus.OK);
    }

    @PostMapping("/connections/{id}/connect")
    public ResponseEntity<GenericResponse<ConnectionDto>> connect(@PathVariable @NotBlank String id) throws ClusterNotFoundException {
        ConnectionDto connection = connectionService.connect(id);
        return new GenericResponse.Builder<ConnectionDto>()
                .data(connection)
                .success(HttpStatus.OK);
    }

    @PostMapping("/connections/disconnect")
    public ResponseEntity<GenericResponse<Void>> disconnect() throws ClusterNotFoundException {
        connectionService.disconnect();
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }

    @GetMapping("/consumer-groups")
    public ResponseEntity<GenericResponse<List<ConsumerGroup>>> getConsumerGroups(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                                    @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) int size)
            throws KafkaAdminApiException {
        ConsumerService consumersService = (ConsumerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONSUMER));
        ConnectionDto connection = connectionService.getActiveConnection();
        Collection<ConsumerGroup> groups = consumersService.getConsumersGroup(connection.getClusterId());
        return new GenericResponse.Builder<List<ConsumerGroup>>()
                .data(paginate(groups, page, size))
                .success(HttpStatus.OK);
    }

    @GetMapping("/consumer-groups/default-id")
    public ResponseEntity<GenericResponse<String>> getDefaultConsumerGroupId() {
        ConsumerService consumersService = (ConsumerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONSUMER));
        ConnectionDto connection = connectionService.getActiveConnection();
        return new GenericResponse.Builder<String>()
                .data(consumersService.getOrCreateDefaultGroupId(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/metrics/{metric}/{entityId}")
    public ResponseEntity<GenericResponse<MetricChartDto>> getMetrics(@PathVariable MetricEnum metric,
                                                                       @PathVariable String entityId,
                                                                       @RequestParam Long start,
                                                                       @RequestParam Long end,
                                                                       @RequestParam TimeUnitEnum sampleDuration)
            throws KafkaAdminApiException, ClusterNotFoundException {
        MetricChartDto metricChart = metricService.getChart(new MetricChartDto()
                .setMetricEnum(metric)
                .setStart(start)
                .setEnd(end)
                .setSampleDuration(sampleDuration)
                .setId(entityId));
        return new GenericResponse.Builder<MetricChartDto>()
                .data(metricChart)
                .success(HttpStatus.OK);
    }

    @GetMapping("/alerts")
    public ResponseEntity<GenericResponse<List<AlertDto>>> getAlerts(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                       @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) int size) throws KafkadorException {
        AlertService alertService = (AlertService) applicationContext.getBean("AlertService");
        Sort sort = Sort.by(Sort.Direction.DESC, "creationDateTime");
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
        return new GenericResponse.Builder<List<AlertDto>>()
                .data(alertService.getAlerts(pageable))
                .success(HttpStatus.OK);
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<GenericResponse<AlertDto>> getAlert(@PathVariable Integer id) throws AlertNotFoundException {
        AlertService alertService = (AlertService) applicationContext.getBean("AlertService");
        return new GenericResponse.Builder<AlertDto>()
                .data(alertService.getAlert(id))
                .success(HttpStatus.OK);
    }

    @PostMapping("/topics/{topic}/messages")
    public ResponseEntity<GenericResponse<Event<String, String>>> produce(@Valid @RequestBody Event<String, String> event, @PathVariable @NotBlank String topic) throws KafkadorException {
        ProducerService producerService = (ProducerService) applicationContext.getBean("SimpleProducerService");
        Event<String, String> produced = producerService.produce(topic, event);
        return new GenericResponse.Builder<Event<String, String>>()
                .data(produced)
                .success(HttpStatus.ACCEPTED);
    }

    @GetMapping(value = "/topics/{topic}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consume(@PathVariable @NotBlank String topic,
                               @RequestParam(defaultValue = "kafkador") @NotBlank String groupId) throws KafkadorException {
        ConsumerService consumersService = (ConsumerService) applicationContext.getBean("ConsumerService");
        return consumersService.consume(topic, groupId);
    }

    @GetMapping("/acl")
    public ResponseEntity<GenericResponse<List<AclBindingDto>>> getAclBindings() throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
        AclService aclService = (AclService) applicationContext.getBean("AclService");
        ConnectionDto connection = connectionService.getActiveConnection();
        return new GenericResponse.Builder<List<AclBindingDto>>()
                .data(aclService.getAclBindings(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @PostMapping("/acl")
    public ResponseEntity<GenericResponse<AclBindingDto>> createAclBinding(@Valid @RequestBody AclBindingDto binding)
            throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
        AclService aclService = (AclService) applicationContext.getBean("AclService");
        ConnectionDto connection = connectionService.getActiveConnection();
        AclBindingDto created = aclService.createAclBinding(connection.getClusterId(), binding);
        return new GenericResponse.Builder<AclBindingDto>()
                .data(created)
                .success(HttpStatus.CREATED);
    }

    @DeleteMapping("/acl")
    public ResponseEntity<Void> deleteAclBinding(@Valid @RequestBody AclBindingDto binding)
            throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
        AclService aclService = (AclService) applicationContext.getBean("AclService");
        ConnectionDto connection = connectionService.getActiveConnection();
        aclService.deleteAclBinding(connection.getClusterId(), binding);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/schema-registry/subjects")
    public ResponseEntity<GenericResponse<SchemaRegistryDto>> getSubjects() {
        ConnectionDto connection = connectionService.getActiveConnection();
        SchemaRegistryService schemaRegistryService = (SchemaRegistryService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.SCHEMA_REGISTRY));
        return new GenericResponse.Builder<SchemaRegistryDto>()
                .data(schemaRegistryService.getSubjects(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/schema-registry/config")
    public ResponseEntity<GenericResponse<SchemaRegistryConfigDto>> getSchemaRegistryConfig() {
        ConnectionDto connection = connectionService.getActiveConnection();
        SchemaRegistryService schemaRegistryService = (SchemaRegistryService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.SCHEMA_REGISTRY));
        return new GenericResponse.Builder<SchemaRegistryConfigDto>()
                .data(schemaRegistryService.getConfig(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @PutMapping("/schema-registry/config")
    public ResponseEntity<GenericResponse<SchemaRegistryConfigDto>> saveSchemaRegistryConfig(@Valid @RequestBody SchemaRegistryConfigDto config) {
        ConnectionDto connection = connectionService.getActiveConnection();
        SchemaRegistryService schemaRegistryService = (SchemaRegistryService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.SCHEMA_REGISTRY));
        return new GenericResponse.Builder<SchemaRegistryConfigDto>()
                .data(schemaRegistryService.saveConfig(config.getUrl(), connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/ksqldb/config")
    public ResponseEntity<GenericResponse<KsqlDbConfigDto>> getKsqlDbConfig() {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<KsqlDbConfigDto>()
                .data(ksqlDbService.getConfig(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @PutMapping("/ksqldb/config")
    public ResponseEntity<GenericResponse<KsqlDbConfigDto>> saveKsqlDbConfig(@Valid @RequestBody KsqlDbConfigDto config) {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<KsqlDbConfigDto>()
                .data(ksqlDbService.saveConfig(config.getUrl(), connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/ksqldb/info")
    public ResponseEntity<GenericResponse<KsqlServerInfoDto>> getKsqlDbInfo() throws ConfigNotFoundException, KsqlDbApiException {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<KsqlServerInfoDto>()
                .data(ksqlDbService.getServerInfo(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/ksqldb/streams")
    public ResponseEntity<GenericResponse<List<KsqlStreamDto>>> getKsqlDbStreams() throws ConfigNotFoundException, KsqlDbApiException {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<List<KsqlStreamDto>>()
                .data(ksqlDbService.getStreams(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/ksqldb/tables")
    public ResponseEntity<GenericResponse<List<KsqlTableDto>>> getKsqlDbTables() throws ConfigNotFoundException, KsqlDbApiException {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<List<KsqlTableDto>>()
                .data(ksqlDbService.getTables(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/ksqldb/queries")
    public ResponseEntity<GenericResponse<List<KsqlQueryDto>>> getKsqlDbQueries() throws ConfigNotFoundException, KsqlDbApiException {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        return new GenericResponse.Builder<List<KsqlQueryDto>>()
                .data(ksqlDbService.getQueries(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @PostMapping("/ksqldb/queries/{id}/terminate")
    public ResponseEntity<Void> terminateKsqlDbQuery(@PathVariable @NotBlank @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "Invalid query id") String id)
            throws ConfigNotFoundException, KsqlDbApiException {
        ConnectionDto connection = connectionService.getActiveConnection();
        KsqlDbService ksqlDbService = (KsqlDbService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
        ksqlDbService.terminateQuery(id, connection.getClusterId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<GenericResponse<List<SearchResult>>> search(@RequestParam @NotBlank String query,
                                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) int size) {
        SearchService searchService = (SearchService) applicationContext.getBean("SearchService");
        List<SearchResult> results = searchService.search(query);
        return new GenericResponse.Builder<List<SearchResult>>()
                .data(paginate(results, page, size))
                .success(HttpStatus.OK);
    }

    @GetMapping("/metrics/chart/{id}")
    public ResponseEntity<GenericResponse<String>> getMetricChart(@PathVariable String id) throws ConfigurationRequiredException {
        AgentService agentService = (AgentService) applicationContext.getBean("AgentService");
        List<Agent> agents = agentService.getAgents();
        if (agents.isEmpty()) throw new ConfigurationRequiredException("APM agent configuration required");
        return new GenericResponse.Builder<String>()
                .data("xxxx")
                .success(HttpStatus.OK);
    }

    @PostMapping("/apm/metrics/ingest")
    public ResponseEntity<GenericResponse<String>> ingest(@Valid @RequestBody ApmMetricIngestDto apmMetricIngest) {
        log.debug("Received APM metric ingest payload: {}", apmMetricIngest);
        return new GenericResponse.Builder<String>()
                .data("OK")
                .success(HttpStatus.ACCEPTED);
    }

    private <T> List<T> paginate(Collection<T> items, int page, int size) {
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        return items.stream()
                .skip((long) page * boundedSize)
                .limit(boundedSize)
                .collect(Collectors.toList());
    }
}
