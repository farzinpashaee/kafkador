package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.RequestContext;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Properties;


@Service("ConnectionServiceByConfig")
public class ConnectionByConfig implements ConnectionService {

    @Autowired
    ApplicationConfig applicationConfig;

    @Override
    public Connection connect(RequestContext<String> request) throws ConnectionNotFoundException {
        Optional<Connection> optionalConnection = applicationConfig.getConnections().stream()
                .filter(i -> i.getId().equals(request.getBody()) ).findFirst();
        if( optionalConnection.isPresent() ){
            request.getHttpSession().setAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString(),optionalConnection.get());
            return optionalConnection.get();
        } else {
            throw new ConnectionNotFoundException("Connection with given ID not found!");
        }
    }

    @Override
    public Connection getActiveConnection(RequestContext request) throws ConnectionSessionExpiredException {
        Connection connection = (Connection) request.getHttpSession().getAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString());
        if( connection == null ) throw new ConnectionSessionExpiredException("No Active connection found!","/connect");
        return connection;
    }

    @Override
    public Properties getActiveConnectionProperties(RequestContext request) throws ConnectionSessionExpiredException {
        Connection connection = getActiveConnection(request);
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ;
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }

    @Override
    public Connection createConnection(RequestContext<Connection> request) throws KafkadorException {
        throw new KafkadorException("Service implementation is not available");
    }

    @Override
    public List<Connection> getConnections(RequestContext request) {
        return applicationConfig.getConnections();
    }


}
