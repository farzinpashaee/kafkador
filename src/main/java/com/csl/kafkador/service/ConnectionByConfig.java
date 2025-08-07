package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Request;
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

    @Autowired
    SessionHolder sessionHolder;

    @Override
    public Connection connect(Request<String> request) throws ConnectionNotFoundException {
        Optional<Connection> optionalConnection = applicationConfig.getConnections().stream()
                .filter(i -> i.getId().equals(request.getBody()) ).findFirst();
        if( optionalConnection.isPresent() ){
            sessionHolder.getSession().setAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString(),optionalConnection.get());
            return optionalConnection.get();
        } else {
            throw new ConnectionNotFoundException("Connection with given ID not found!");
        }
    }

    @Override
    public Connection getActiveConnection() throws ConnectionSessionExpiredException {
        Connection connection = (Connection) sessionHolder.getSession().getAttribute(KafkadorContext.Attribute.ACTIVE_CONNECTION.toString());
        if( connection == null ) throw new ConnectionSessionExpiredException("No Active connection found!","/connect");
        return connection;
    }

    @Override
    public Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException {
        Connection connection = getActiveConnection();
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ;
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }

    @Override
    public Connection createConnection(Request<Connection> request) throws KafkadorException {
        throw new KafkadorException("Service implementation is not available");
    }

    @Override
    public List<Connection> getConnections() {
        return applicationConfig.getConnections();
    }


}
