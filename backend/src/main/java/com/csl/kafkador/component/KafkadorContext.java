package com.csl.kafkador.component;

import com.csl.kafkador.domain.dto.ClusterDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.service.config.KafkadorConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkadorContext {

    private Map<SessionAttribute,Object> attributeMap = new HashMap();

    private final KafkadorConfigService kafkadorConfigService;

    @PostConstruct
    public void init(){
        // load config
    }

    public void setAttribute( KafkadorContext.SessionAttribute attribute, Object object ){
        attributeMap.put(attribute,object);
    }

    public static enum SessionAttribute {
        CONNECTIONS,
        ACTIVE_CONNECTION,
        CLUSTER
    }

    public static enum Service {
        CONNECTION("connection", "ConnectionService"),
        TOPIC("topic", "TopicService"),
        CLUSTER("cluster", "ClusterService"),
        BROKER("broker", "BrokerService"),
        SCHEMA_REGISTRY("schema-registry", "SchemaRegistryService"),
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
