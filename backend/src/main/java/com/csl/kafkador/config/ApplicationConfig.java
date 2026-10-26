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

    private Map<String,Service> services = new HashMap<String,Service>();
    private List<ConnectionDto> connections = new ArrayList<>();
    private Session session = new Session();
    private Cors cors = new Cors();

    public String getServiceImplementation(KafkadorContext.Service service){
        if( !services.containsKey(service.getKey()) )
            return service.getDefaultImplementation();
        return services.get(service.getKey()).getImplementation();
    }

    @Data
    public static class Service {
        String implementation;
    }

    @Data
    public static class Cors {
        /** Other origins allowed to call the API from a browser; none by default (the dev profile adds the Angular dev server). */
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class Session {
        /** Idle time before a connection session expires; the value saved in Settings overrides it. */
        private int timeoutMinutes = 30;
    }

}
