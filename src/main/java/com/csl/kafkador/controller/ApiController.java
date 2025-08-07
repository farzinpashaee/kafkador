package com.csl.kafkador.controller;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.dto.Event;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.service.*;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
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
    public ClusterDetails getCluster( HttpSession session ) throws KafkaAdminApiException {
        ClusterService clusterService = (ClusterService) applicationContext.getBean("ClusterService");
        return clusterService.getClusterDetails( new Request(session) );
    }

    @GetMapping("/topic")
    public Collection<TopicListing> getTopics( HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopics( new Request(session) );
    }

    @PostMapping("/topic")
    public void createTopic(@RequestBody Topic request, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.createTopic(new Request<Topic>(session).setBody(request));
    }

    @DeleteMapping("/topic/{id}")
    public void deleteTopic(@PathVariable String name, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.deleteTopic(new Request<String>(session).setBody(name));
    }

    @GetMapping("/topic/{name}")
    public Topic getTopic(@PathVariable String name, HttpSession session) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopic(new Request<String>(session).setBody(name));
    }

    @PostMapping("/connection")
    public Connection createConnection(@RequestBody Connection connection, HttpSession session) throws KafkadorException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
        return connectionService.createConnection(new Request<Connection>(session).setBody(connection));
    }

    @GetMapping("/connection")
    public List<Connection> getConnections(HttpSession session) {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
        return connectionService.getConnections();
    }

    @GetMapping("/connect")
    public Connection connect(@RequestParam String id, HttpSession session) throws ConnectionNotFoundException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
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
