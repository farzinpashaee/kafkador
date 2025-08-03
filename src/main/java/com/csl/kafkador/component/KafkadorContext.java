package com.csl.kafkador.component;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class KafkadorContext {

    public Properties getKafkaAdminApiConfig(){
        String bootstrapServers = "localhost:9092"; // Replace with your Kafka broker address

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return  props;
    }

}
