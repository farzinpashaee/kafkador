package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.dto.Topic;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service("ConsumerService")
public class ConsumerService {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ApplicationConfig applicationConfig;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Collection<ConsumerGroupListing> getConsumersGroup( Request request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getConnectionServiceImplementation());

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            KafkaFuture<Collection<ConsumerGroupListing>> consumersFuture = admin.listConsumerGroups().all();
            return consumersFuture.get();
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public SseEmitter consume( String topic ){
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Long-lived connection
        executor.execute(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(1000); // simulate delay
                    emitter.send("SSE MVC - " + System.currentTimeMillis());
                }
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

}
