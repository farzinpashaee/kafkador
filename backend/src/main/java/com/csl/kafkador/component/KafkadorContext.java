package com.csl.kafkador.component;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
        CONNECTION("Connection", "ConnectionService"),
        TOPIC("topic", "TopicService"),
        CLUSTER("cluster", "ClusterService"),
        BROKER("broker", "BrokerService"),
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
