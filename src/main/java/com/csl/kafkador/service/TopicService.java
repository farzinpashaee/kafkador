package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service("TopicsService")
public class TopicService {


    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ApplicationConfig applicationConfig;


    public Collection<TopicListing> getTopics( RequestContext request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties(request))) {
            KafkaFuture<Collection<TopicListing>> topicsFuture = admin.listTopics().listings();
            return topicsFuture.get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public Topic createTopic(RequestContext<Topic> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties(request))) {
            Topic topic = request.getBody();
            NewTopic newTopic = new NewTopic(topic.getName(),
                    topic.getPartitions(),
                    topic.getReplicatorFactor());
            // Optional: Add topic-specific configurations (e.g., cleanup policy)
            // newTopic.configs(Collections.singletonMap(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT));
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            KafkaFuture<Void> future = result.values().get(topic.getName());
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Failed to create topic: " + e.getMessage());
            }
            return topic;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public void deleteTopic(RequestContext<String> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties(request))) {
            DeleteTopicsResult deleteTopicsResult = admin.deleteTopics(Collections.singleton(request.getBody()));
            deleteTopicsResult.all().get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public Topic getTopic( RequestContext<String> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties(request))) {
            Topic topic = new Topic();
            Set<String> topicNames = new HashSet<>();
            topicNames.add(request.getBody());
            DescribeTopicsResult result = admin.describeTopics(topicNames);

            Map<String, TopicDescription> topicDescriptions = result.allTopicNames().get();
            TopicDescription topicDescription = topicDescriptions.get(request.getBody());
            if (topicDescription != null) {
                topic.setName(topicDescription.name());
                topic.setId(topicDescription.topicId().toString());
                topic.setPartitions(topicDescription.partitions().size());
                return topic;
            }

        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return null;
    }

}
