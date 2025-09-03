package com.csl.kafkador.component;

import com.csl.kafkador.model.Connection;
import lombok.Data;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class KafkadorContext {

    private Map<SessionAttribute,Object> attributeMap = new HashMap();

    public void setAttribute( KafkadorContext.SessionAttribute attribute, Object object ){
        attributeMap.put(attribute,object);
    }

    public static enum SessionAttribute {
        CONNECTIONS,
        ACTIVE_CONNECTION
    }

    public static enum Service {
        CONNECTION("Connection", "ConnectionServiceByConfig"),
        TOPIC("topic", "TopicService"),
        CLUSTER("cluster", "ClusterService"),
        CONSUMER("consumer", "ConsumerService"),
        PRODUCER("producer", "ProducerService");

        private String key;
        private String defaultImplementation;
        Service( String key, String defaultImplementation){
            this.key = key;
            this.defaultImplementation = defaultImplementation;
        }

        public String getKey(){
            return key;
        }

        public String getDefaultImplementation(){
            return defaultImplementation;
        }

    }

}
