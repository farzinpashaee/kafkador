package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

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

}
