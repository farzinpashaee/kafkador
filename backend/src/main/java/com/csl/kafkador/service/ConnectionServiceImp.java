package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.exception.ConnectionNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.model.Connection;
import com.csl.kafkador.repository.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Service("ConnectionService")
@RequiredArgsConstructor
public class ConnectionServiceImp implements ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final ApplicationConfig applicationConfig;
    private final SessionHolder sessionHolder;

    @Override
    public Connection connect(Integer id) throws ConnectionNotFoundException {
        Optional<Connection> optionalConnection = connectionRepository.findById(id);
        if( optionalConnection.isPresent() ){
            sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),optionalConnection.get());
            String redirectAfterLogin = sessionHolder.getSession().getAttribute("redirectAfterLogin") == null ?
                    null : sessionHolder.getSession().getAttribute("redirectAfterLogin").toString();
            optionalConnection.get().setRedirectAfterLogin(redirectAfterLogin);
            return optionalConnection.get();
        } else {
            throw new ConnectionNotFoundException("Connection with given ID not found!");
        }
    }

    @Override
    public Connection disconnect() throws ConnectionNotFoundException {
        Connection connection =  (Connection) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),null);
        return connection;
    }

    @Override
    public List<Connection> getConnections() {
        return connectionRepository.findAll();
    }

    @Override
    public Connection getActiveConnection() throws ConnectionSessionExpiredException {
        Connection connection = (Connection) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        if( connection == null ) throw new ConnectionSessionExpiredException("No Active connection found!","/connect");
        return connection;
    }

    @Override
    public Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException {
        Connection connection = getActiveConnection();
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

    @Override
    public Connection createConnection(Connection connection) throws KafkadorException {
        return null;
    }
}
