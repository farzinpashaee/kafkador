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
    private Access access = new Access();

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

    /**
     * Who may use Kafkador, set up front instead of on the setup page (e.g. KAFKADOR_ACCESS_MODE). When the mode is
     * set it's applied at every startup, so Settings can't change it, and it's how a lost admin password is reset.
     */
    @Data
    public static class Access {
        /** none, database or ldap; empty leaves the choice to the setup page and Settings. */
        private String mode = "";
        private String adminUsername = "";
        private String adminPassword = "";
        private Ldap ldap = new Ldap();
    }

    @Data
    public static class Ldap {
        private String url = "";
        private String baseDn = "";
        private String userDnPattern = "";
        private String userSearchBase = "";
        private String userSearchFilter = "";
        private String managerDn = "";
        private String managerPassword = "";
    }

    @Data
    public static class Session {
        /** Idle time before a connection session expires; the value saved in Settings overrides it. */
        private int timeoutMinutes = 30;
    }

}
