package com.csl.kafkador.component;

import com.csl.kafkador.model.Connection;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class KafkadorContext {

    private Map<Attribute,Object> attributeMap = new HashMap();

    public void setAttribute( KafkadorContext.Attribute attribute, Object object ){
        attributeMap.put(attribute,object);
    }

    public static enum Attribute {
        CONNECTIONS,
        ACTIVE_CONNECTION
    }

}
