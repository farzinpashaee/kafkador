package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.dto.ConnectionDto;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Cluster;
import com.csl.kafkador.util.DtoMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@Service("ConnectionService")
@RequiredArgsConstructor
public class ConnectionServiceImp implements ConnectionService {

    private final ClusterService clusterService;
    private final SessionHolder sessionHolder;

    @Override
    public ConnectionDto create(ConnectionDto connection) throws KafkaAdminApiException {
        ClusterDto clusterDto = clusterService.save(connection.getHost(),connection.getPort());
        return DtoMapper.connectionMapper(clusterDto);
    }

    @Override
    public ConnectionDto connect(String id) throws ClusterNotFoundException {
        ClusterDto cluster = clusterService.find(id);
        ConnectionDto connection = DtoMapper.connectionMapper(cluster);
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),connection);
        return connection;
    }

    @Override
    public ConnectionDto disconnect() throws ClusterNotFoundException {
        ConnectionDto connection = (ConnectionDto) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),null);
        return connection;
    }

    @Override
    public List<ConnectionDto> getConnections() {
        return clusterService.findAll().stream()
                .map(DtoMapper::connectionMapper)
                .collect(Collectors.toList());
    }

    @Override
    public ConnectionDto getActiveConnection() throws ConnectionSessionExpiredException {
        ConnectionDto connection = (ConnectionDto) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        if( connection == null ) throw new ConnectionSessionExpiredException("No Active connection found!","/connect");
        return connection;
    }

    @Override
    public Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException {
        ConnectionDto connection = getActiveConnection();
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

    @Override
    public Properties getConnectionProperties(String host, String port) {
        String bootstrapServers = host + ":" + port ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

}
