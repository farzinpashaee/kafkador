package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service("ConsumersService")
public class ConsumerService {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ApplicationConfig applicationConfig;

    public Collection<ConsumerGroupListing> getConsumersGroup( RequestContext request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties(request))) {
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            return consumersFuture.get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

}
