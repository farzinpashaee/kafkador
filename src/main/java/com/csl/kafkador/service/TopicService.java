package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
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


    public Collection<TopicListing> getTopics( Request request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            KafkaFuture<Collection<TopicListing>> topicsFuture = admin.listTopics().listings();
            return topicsFuture.get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public Topic createTopic(Request<Topic> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
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
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public void deleteTopic(Request<String> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            DeleteTopicsResult deleteTopicsResult = admin.deleteTopics(Collections.singleton(request.getBody()));
            deleteTopicsResult.all().get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public Topic getTopic( Request<String> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
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

        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return null;
    }

}
