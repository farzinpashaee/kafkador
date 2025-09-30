package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.dto.ConnectionDto;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.KafkaHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Service("ConnectionService")
@RequiredArgsConstructor
public class ConnectionServiceImp implements ConnectionService {

    private final ClusterRepository clusterRepository;
    private final SessionHolder sessionHolder;
    @Qualifier("ObserverConfigService")
    private final ConfigService<ObserverConfigDto,ObserverConfigDto.ObserverCluster> configService;

    private HashMap<String, Admin> adminClientMap = new HashMap<>();

    public Admin getAdminClient(String id) throws ClusterNotFoundException {
        if(adminClientMap.containsKey(id)) {
            try {
                KafkaFuture<String> clusterIdFuture = adminClientMap.get(id).describeCluster().clusterId();
                clusterIdFuture.get();
                return adminClientMap.get(id);
            } catch (Exception e){
                log.warn("Admin disconnected! Trying to reconnect... - " + e.getMessage());
            }
        }
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(!clusterOptional.isPresent())
            throw new ClusterNotFoundException("Cluster ID not found!");
        Cluster cluster = clusterOptional.get();
        Admin admin = Admin.create(KafkaHelper.getConnectionProperties(cluster.getHost(), cluster.getPort()));
        adminClientMap.put(cluster.getId(),admin);
        return admin;
    }

    @Override
    public ConnectionDto create(ConnectionDto connection) throws KafkaAdminApiException {
        try{
            Admin admin = Admin.create(KafkaHelper.getConnectionProperties(connection.getHost(), connection.getPort()));
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            String clusterId = clusterIdFuture.get();
            Cluster cluster = new Cluster();
            cluster.setHost(connection.getHost());
            cluster.setPort(connection.getPort());
            cluster.setId(clusterId);
            cluster.setName(connection.getName());
            clusterRepository.save(cluster);

            ObserverConfigDto.ObserverCluster observerCluster = new ObserverConfigDto.ObserverCluster();
            observerCluster.setClusterId(clusterId);
            observerCluster.setEnabled(true);
            observerCluster.setObservers(ObserverConfigDto.defaultObserverConfig());
            configService.save(observerCluster);
            return connection.setId(clusterId);
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    @Override
    public ConnectionDto connect(String id) throws ClusterNotFoundException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(!clusterOptional.isPresent())
            throw new ClusterNotFoundException("Connection with given cluster ID not found!");
        ConnectionDto connection = DtoMapper.connectionMapper(clusterOptional.get());
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
        return clusterRepository.findAll().stream()
                .map( i -> DtoMapper.connectionMapper(i) )
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
