package com.csl.kafkador.component;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class KafkadorContext {

    private Map<String,Object> session = new HashMap();

    public Properties getKafkaAdminApiConfig(){
        String bootstrapServers = "192.168.2.139:9092"; // Replace with your Kafka broker address

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return  props;
    }

}
