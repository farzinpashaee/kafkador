package com.csl.kafkador.config;


import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.domain.dto.ConnectionDto;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafkador")
public class ApplicationConfig {

    private String url;
    private Map<String,Service> services = new HashMap<String,Service>();
    private List<ConnectionDto> connections = new ArrayList<>();

    public String getServiceImplementation(KafkadorContext.Service service){
        if( !services.containsKey(service.getKey()) )
            return service.getDefaultImplementation();
        return services.get(service.getKey()).getImplementation();
    }

    @Data
    public static class Service {
        String implementation;
    }

}
