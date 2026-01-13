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
import com.csl.kafkador.service.registry.SchemaRegistryService;
import com.csl.kafkador.service.search.SearchService;
import com.csl.kafkador.util.MetricEnum;
import com.csl.kafkador.util.TimeUnitEnum;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

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


    @GetMapping("/broker/{id}")
    public ResponseEntity<GenericResponse<BrokerDto>> getBroker(@PathVariable String id ) throws KafkaAdminApiException, BrokerNotFoundException {
        BrokerService brokerService = (BrokerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        ConnectionDto connection = connectionService.getActiveConnection();
        BrokerDto broker = brokerService.getDetail(connection.getClusterId(), id);
        return new GenericResponse.Builder<BrokerDto>()
                .data(broker)
                .success(HttpStatus.OK);
    }

    @PostMapping("/broker/{id}/config")
    public void updateBrokerConfig(@PathVariable String id, @RequestBody ConfigEntry configEntry) throws KafkaAdminApiException, BrokerNotFoundException {
        BrokerService brokerService = (BrokerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        ConnectionDto connection = connectionService.getActiveConnection();
        brokerService.updateConfig(connection.getClusterId(), id, configEntry);
    }

    @GetMapping("/topic")
    public ResponseEntity<GenericResponse<Collection<Topic>>> getTopics( HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        return new GenericResponse.Builder<Collection<Topic>>()
                .data(topicService.getTopics(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @PostMapping("/topic/{id}/config")
    public void updateTopicConfig(@PathVariable String id, @RequestBody ConfigEntry configEntry) throws KafkaAdminApiException, BrokerNotFoundException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        topicService.updateConfig(connection.getClusterId(), id, configEntry);
    }

    @GetMapping("/topic/{name}")
    public ResponseEntity<GenericResponse<Topic>> getTopic( @PathVariable String name ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        Topic topic = topicService.getTopic(connection.getClusterId(), name);
        return new GenericResponse.Builder<Topic>()
                .data(topic)
                .success(HttpStatus.OK);
    }


    @PostMapping("/topic")
    public void createTopic(@RequestBody Topic topic) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        ConnectionDto connection = connectionService.getActiveConnection();
        topicService.createTopic(connection.getClusterId(),topic);
    }

    @DeleteMapping("/topic/{id}")
    public void deleteTopic(@PathVariable String name, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        topicService.deleteTopic(new Request<String>(session).setBody(name));
    }

    @PostMapping("/connection")
    public ConnectionDto createConnection(@RequestBody ConnectionDto connection) throws KafkaAdminApiException, DuplicatedClusterException {
        return connectionService.create(connection);
    }

    @DeleteMapping("/connection/{id}")
    public void createConnection(@PathVariable String id) throws ClusterNotFoundException {
        connectionService.delete(id);
    }

    @GetMapping("/connection")
    public ResponseEntity<GenericResponse<List<ConnectionDto>>> getConnections(HttpSession session) {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        return new GenericResponse.Builder<List<ConnectionDto>>()
                .data(connectionService.getConnections())
                .success(HttpStatus.OK);
    }

    @GetMapping("/consumer-group")
    public ResponseEntity<GenericResponse<Collection<ConsumerGroup>>> getConsumerGroup(HttpSession session)
            throws KafkaAdminApiException {
        ConsumerService consumersService = (ConsumerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONSUMER));
        ConnectionDto connection = connectionService.getActiveConnection();
        return new GenericResponse.Builder<Collection<ConsumerGroup>>()
                .data(consumersService.getConsumersGroup(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping("/metric/{metric}/{entityId}")
    public ResponseEntity<GenericResponse<MetricChartDto>> getMetrics(@PathVariable MetricEnum metric,
                                                                      @PathVariable String entityId,
                                                                      @RequestParam Long start,
                                                                      @RequestParam Long end,
                                                                      @RequestParam TimeUnitEnum sampleDuration )
            throws KafkaAdminApiException, ClusterNotFoundException {
        MetricChartDto metricChart = metricService.getChart( new MetricChartDto()
                .setMetricEnum(metric)
                .setStart(start)
                .setEnd(end)
                .setSampleDuration(sampleDuration)
                .setId(entityId));
        return new GenericResponse.Builder<MetricChartDto>()
                .data(metricChart)
                .success(HttpStatus.OK);
    }

    @GetMapping("/connect")
    public ResponseEntity<GenericResponse<ConnectionDto>> connect(@RequestParam String id, HttpSession session)
            throws ClusterNotFoundException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        ConnectionDto connection =  connectionService.connect( id );
        return new GenericResponse.Builder<ConnectionDto>()
                .data(connection)
                .success(HttpStatus.OK);
    }

    @GetMapping("/disconnect")
    public ResponseEntity<GenericResponse<Void>> disconnect() throws ClusterNotFoundException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        connectionService.disconnect();
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }


    @GetMapping(value = "/alert")
    public ResponseEntity<GenericResponse<List<AlertDto>>> getAlerts() throws KafkadorException {
        AlertService alertService = (AlertService) applicationContext
                .getBean("AlertService");
        Sort sort = Sort.by(Sort.Direction.DESC, "creationDateTime");
        Pageable pageable = PageRequest.of(0, 10, sort);
        return new GenericResponse.Builder<List<AlertDto>>()
                .data(alertService.getAlerts(pageable))
                .success(HttpStatus.OK);
    }

    @GetMapping(value = "/alert/{id}")
    public ResponseEntity<GenericResponse<AlertDto>> getAlert(@PathVariable Integer id) throws AlertNotFoundException {
        AlertService alertService = (AlertService) applicationContext
                .getBean("AlertService");
        return new GenericResponse.Builder<AlertDto>()
                .data(alertService.getAlert(id))
                .success(HttpStatus.OK);
    }

    @PostMapping("/produce/{topic}")
    public Event produce(@RequestBody Event<String,String> event, @PathVariable String topic) throws KafkadorException {
        ProducerService producerService = (ProducerService) applicationContext
                .getBean("SimpleProducerService");
        return producerService.produce(topic, event);
    }

    @GetMapping(value = "/consume/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consume(@PathVariable String topic) throws KafkadorException {
        ConsumerService consumersService = (ConsumerService) applicationContext
                .getBean("ConsumerService");
        return consumersService.consume(topic);
    }

    @GetMapping(value = "/acl")
    public void acl() throws ClusterNotFoundException {
        AclServiceImp aclService = (AclServiceImp) applicationContext
                .getBean("AclService");
        ConnectionDto connection = connectionService.getActiveConnection();
        aclService.getAclBindings(connection.getClusterId());
    }


    @GetMapping(value = "/schema-registry/subject")
    public ResponseEntity<GenericResponse<SchemaRegistryDto>> getSubjects() {
        ConnectionDto connection = connectionService.getActiveConnection();
        SchemaRegistryService schemaRegistryService = (SchemaRegistryService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.SCHEMA_REGISTRY));
        return new GenericResponse.Builder<SchemaRegistryDto>()
                .data(schemaRegistryService.getSubjects(connection.getClusterId()))
                .success(HttpStatus.OK);
    }

    @GetMapping(value = "/search")
    public ResponseEntity<GenericResponse<List<SearchResult>>> getAlert(@RequestParam String query) throws AlertNotFoundException {
        SearchService searchService = (SearchService) applicationContext
                .getBean("SearchService");
        return new GenericResponse.Builder<List<SearchResult>>()
                .data(searchService.search(query))
                .success(HttpStatus.OK);
    }

    @GetMapping(value = "/metric/chart/{id}")
    public ResponseEntity<GenericResponse<String>> getMetricChart(@PathVariable String id) throws ConfigurationRequiredException{
        AgentService agentService = (AgentService) applicationContext
                .getBean("AgentService");
        List<Agent> agents = agentService.getAgents();
        if(agents.size() == 0) throw new ConfigurationRequiredException("APM agent configuration required");
        return new GenericResponse.Builder<String>()
                .data("xxxx")
                .success(HttpStatus.OK);
    }

    @PostMapping(value = "/apm/metric/ingest")
    public ResponseEntity<GenericResponse<String>> ingest(@RequestBody ApmMetricIngestDto apmMetricIngest) throws AlertNotFoundException {
        System.out.println(apmMetricIngest);
        return new GenericResponse.Builder<String>()
                .data("OK")
                .success(HttpStatus.OK);
    }
}
