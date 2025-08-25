package com.csl.kafkador.controller;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.*;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.service.*;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.producer.ProducerRecord;
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

    @GetMapping("/cluster")
    public ResponseEntity<GenericResponse<ClusterDetails>> getCluster(HttpSession session ) throws KafkaAdminApiException {
        ClusterService clusterService = (ClusterService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CLUSTER));
        ClusterDetails clusterDetails = clusterService.getClusterDetails();
        return new GenericResponse.Builder<ClusterDetails>()
                .data(clusterDetails)
                .success(HttpStatus.OK);
    }


    @GetMapping("/broker/{id}")
    public ResponseEntity<GenericResponse<Broker>> getBroker( @PathVariable String id ) throws KafkaAdminApiException {
        ClusterService clusterService = (ClusterService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CLUSTER));
        Broker broker = clusterService.getBrokerDetail(id);
        return new GenericResponse.Builder<Broker>()
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

    @GetMapping("/topic/{name}")
    public Topic getTopic(@PathVariable String name, HttpSession session) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
        return topicService.getTopic(new Request<String>(session).setBody(name));
    }

    @PostMapping("/connection")
    public Connection createConnection(@RequestBody Connection connection, HttpSession session) throws KafkadorException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        return connectionService.createConnection(new Request<Connection>(session).setBody(connection));
    }

    @GetMapping("/connection")
    public ResponseEntity<GenericResponse<List<Connection>>> getConnections(HttpSession session) {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        return new GenericResponse.Builder<List<Connection>>()
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
    public Connection connect(@RequestParam String id, HttpSession session) throws ConnectionNotFoundException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));
        return connectionService.connect( new Request<String>(session).setBody(id) );
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
