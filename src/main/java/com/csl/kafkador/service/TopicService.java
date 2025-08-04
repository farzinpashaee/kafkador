package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service("TopicsService")
public class TopicService {

    @Autowired
    KafkadorContext kafkadorContext;

    public Collection<TopicListing> getTopics() throws KafkaAdminApiException {
        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            KafkaFuture<Collection<TopicListing>> topicsFuture = admin.listTopics().listings();
            return topicsFuture.get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public Topic createTopic(Topic request) throws KafkaAdminApiException {
        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            NewTopic newTopic = new NewTopic(request.getName(),
                    request.getPartitions(),
                    request.getReplicatorFactor());
            // Optional: Add topic-specific configurations (e.g., cleanup policy)
            // newTopic.configs(Collections.singletonMap(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT));
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            KafkaFuture<Void> future = result.values().get(request.getName());
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Failed to create topic: " + e.getMessage());
            }
            return request;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public void deleteTopic(String name) throws KafkaAdminApiException {
        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            DeleteTopicsResult deleteTopicsResult = admin.deleteTopics(Collections.singleton(name));
            deleteTopicsResult.all().get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public Topic getTopic(String name) throws KafkaAdminApiException {
        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            Topic topic = new Topic();
            Set<String> topicNames = new HashSet<>();
            topicNames.add(name);
            DescribeTopicsResult result = admin.describeTopics(topicNames);

            Map<String, TopicDescription> topicDescriptions = result.allTopicNames().get();
            TopicDescription topicDescription = topicDescriptions.get(name);
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
