package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service("ConsumerService")
public class ConsumerService {

    @Autowired
    KafkadorContext kafkadorContext;

    public Collection<ConsumerGroupListing> getConsumersGroup() throws KafkaAdminApiException {
        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            return consumersFuture.get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

}
