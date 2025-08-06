package com.csl.kafkador.config;


import com.csl.kafkador.model.Connection;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafkador")
public class ApplicationConfig {

    private String url;
    private String connectionServiceImplementation;
    private List<Connection> connections = new ArrayList<>();

}
