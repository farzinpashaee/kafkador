package com.csl.kafkador.controller;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.*;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.dto.ConnectionDto;
import com.csl.kafkador.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    ApplicationConfig applicationConfig;
    @Autowired
    ConnectionService connectionService;


    @GetMapping("/cluster")
    public ResponseEntity<GenericResponse<ClusterDto>> getCluster() throws KafkaAdminApiException, ClusterNotFoundException {
        ClusterService clusterService = (ClusterService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CLUSTER));
        ConnectionDto connection = connectionService.getActiveConnection();
        ClusterDto cluster = clusterService.getClusterDetails(connection.getId());
        return new GenericResponse.Builder<ClusterDto>()
                .data(cluster)
                .success(HttpStatus.OK);
    }


    @GetMapping("/broker/{id}")
    public ResponseEntity<GenericResponse<BrokerDto>> getBroker(@PathVariable String id ) throws KafkaAdminApiException {
        BrokerService brokerService = (BrokerService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        BrokerDto broker = brokerService.getDetail(id);
        return new GenericResponse.Builder<BrokerDto>()
                .data(broker)
                .success(HttpStatus.OK);
    }

    @GetMapping("/topic")
    public ResponseEntity<GenericResponse<Collection<Topic>>> getTopics( HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        return new GenericResponse.Builder<Collection<Topic>>()
                .data(topicService.getTopics())
                .success(HttpStatus.OK);
    }

    @GetMapping("/topic/{name}")
    public ResponseEntity<GenericResponse<Topic>> getTopic( @PathVariable String name ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        Topic topic = topicService.getTopic(name);
        return new GenericResponse.Builder<Topic>()
                .data(topic)
                .success(HttpStatus.OK);
    }


    @PostMapping("/topic")
    public void createTopic(@RequestBody Topic request, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        topicService.createTopic(new Request<Topic>(session).setBody(request));
    }

    @DeleteMapping("/topic/{id}")
    public void deleteTopic(@PathVariable String name, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        topicService.deleteTopic(new Request<String>(session).setBody(name));
    }

    @PostMapping("/connection")
    public ConnectionDto createConnection(@RequestBody ConnectionDto connection) throws KafkaAdminApiException {
        return connectionService.create(connection);
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
    public ResponseEntity<GenericResponse<Collection<ConsumerGroup>>> getConsumerGroup(HttpSession session) throws KafkaAdminApiException {
        ConsumerService consumersService = (ConsumerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONSUMER));
        return new GenericResponse.Builder<Collection<ConsumerGroup>>()
                .data(consumersService.getConsumersGroup())
                .success(HttpStatus.OK);
    }

    @GetMapping("/connect")
    public ResponseEntity<GenericResponse<ConnectionDto>> connect(@RequestParam String id, HttpSession session) throws ClusterNotFoundException {
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

}
