package com.csl.kafkador.controller;

import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.service.ClusterService;
import com.csl.kafkador.service.ConnectionService;
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

    @GetMapping("/cluster")
    public ClusterDetails getCluster() throws KafkaAdminApiException {
        ClusterService clusterService = (ClusterService) applicationContext.getBean("ClusterService");
        return clusterService.getClusterDetails();
    }

    @GetMapping("/topic")
    public Collection<TopicListing> getTopics() throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopics();
    }

    @PostMapping("/topic")
    public void createTopic(@RequestBody Topic request) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.createTopic(request);
    }

    @DeleteMapping("/topic/{name}")
    public void deleteTopic(@PathVariable String name) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        topicService.deleteTopic(name);
    }

    @GetMapping("/topic/{name}")
    public Topic getTopic(@PathVariable String name) throws KafkaAdminApiException {
        TopicService topicService = (TopicService) applicationContext.getBean("TopicsService");
        return topicService.getTopic(name);
    }

    @PostMapping("/connection")
    public Connection createConnection(@RequestBody Connection connection, HttpSession session) throws KafkaAdminApiException {
        ConnectionService connectionService = (ConnectionService) applicationContext.getBean("ConnectionService");
        return connectionService.createConnection(connection, session);
    }

    @GetMapping("/connection")
    public List<Connection> getConnections(HttpSession session) throws KafkaAdminApiException {
        ConnectionService connectionService = (ConnectionService) applicationContext.getBean("ConnectionService");
        return connectionService.getConnections(session);
    }

}
