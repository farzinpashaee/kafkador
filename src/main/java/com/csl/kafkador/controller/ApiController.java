package com.csl.kafkador.controller;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.service.ClusterService;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ConnectionServiceBySession;
import com.csl.kafkador.service.TopicService;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.TopicListing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

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
        return clusterService.getClusterDetails( new RequestContext(session) );
    }

    @GetMapping("/topic")
    public Collection<TopicListing> getTopics( HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopics( new RequestContext(session) );
    }

    @PostMapping("/topic")
    public void createTopic(@RequestBody Topic request, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.createTopic(new RequestContext<Topic>(session).setBody(request));
    }

    @DeleteMapping("/topic/{id}")
    public void deleteTopic(@PathVariable String name, HttpSession session ) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.deleteTopic(new RequestContext<String>(session).setBody(name));
    }

    @GetMapping("/topic/{name}")
    public Topic getTopic(@PathVariable String name, HttpSession session) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopic(new RequestContext<String>(session).setBody(name));
    }

    @PostMapping("/connection")
    public Connection createConnection(@RequestBody Connection connection, HttpSession session) throws KafkadorException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
        return connectionService.createConnection(new RequestContext<Connection>(session).setBody(connection));
    }

    @GetMapping("/connection")
    public List<Connection> getConnections(HttpSession session) {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
        return connectionService.getConnections(new RequestContext(session));
    }

    @GetMapping("/connect")
    public Connection connect(@RequestParam String id, HttpSession session) throws ConnectionNotFoundException {
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());
        return connectionService.connect( new RequestContext<String>(session).setBody(id) );
    }

}
