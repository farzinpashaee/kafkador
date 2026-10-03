package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.domain.wrapper.AdminClusterWrapper;
import com.csl.kafkador.domain.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.DuplicatedClusterException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.agent.AgentService;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.KafkaHelper;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service("ConnectionService")
@RequiredArgsConstructor
public class ConnectionServiceImp implements ConnectionService {

    private final ClusterRepository clusterRepository;
    private final AgentService agentService;
    private final SessionHolder sessionHolder;
    private final SessionSettingsService sessionSettingsService;
    @Qualifier("ObserverConfigService")
    private final KafkadorConfigService<ObserverConfigDto,ObserverConfigDto> kafkadorConfigService;

    // How long a cached Admin may take to prove it can still reach its cluster before it is replaced.
    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private static final Duration ADMIN_CLOSE_TIMEOUT = Duration.ofSeconds(1);

    // Shared by every session. Each Admin owns a network thread that keeps polling (and, once the brokers are
    // unreachable, re-bootstrapping) until close() is called, so an entry must be closed whenever it is dropped.
    private final Map<String, AdminClusterWrapper> adminClientMap = new ConcurrentHashMap<>();

    public AdminClusterWrapper getAdminClient(String clusterId) throws ClusterNotFoundException {
        AdminClusterWrapper adminClusterWrapper = new AdminClusterWrapper();
        AdminClusterWrapper cached = adminClientMap.get(clusterId);
        if(cached != null) {
            try {
                KafkaFuture<String> clusterIdFuture = cached.getAdmin().describeCluster().clusterId();
                clusterIdFuture.get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return cached;
            } catch (Exception e){
                log.warn("Admin disconnected! Trying to reconnect... - " + e.getMessage());
                adminClientMap.remove(clusterId, cached);
                closeAdmin(cached);
            }
        }
        Optional<Cluster> clusterOptional = clusterRepository.findByClusterId(clusterId);
        if(!clusterOptional.isPresent())
            throw new ClusterNotFoundException("Cluster ID not found!");
        Cluster cluster = clusterOptional.get();
        Admin admin = Admin.create(KafkaHelper.getConnectionProperties(cluster.getHost(), cluster.getPort()));
        adminClusterWrapper.setAdmin(admin);
        adminClusterWrapper.setCluster(DtoMapper.clusterMapper(cluster));
        adminClientMap.put(cluster.getClusterId(),adminClusterWrapper);
        return adminClusterWrapper;
    }

    @Override
    public void delete(String id) throws ClusterNotFoundException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isPresent()){
            closeAdminClient(clusterOptional.get().getClusterId());
            clusterRepository.delete(clusterOptional.get());
        } else {
            throw new ClusterNotFoundException("Connection to cluster with given ID not found!");
        }
    }


    @Override
    public ConnectionDto create(ConnectionDto connection) throws KafkaAdminApiException, DuplicatedClusterException {
        Admin admin = null;
        Optional<Cluster> clusterOptional = clusterRepository.findByHostAndPort(connection.getHost(), connection.getPort());
        if(clusterOptional.isPresent()) throw new DuplicatedClusterException("There is a cluster created with this ip and port number!");

        try{
            admin = Admin.create(KafkaHelper.getConnectionProperties(connection.getHost(), connection.getPort()));
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            String clusterId = clusterIdFuture.get();
            Cluster cluster = new Cluster();
            cluster.setHost(connection.getHost());
            cluster.setPort(connection.getPort());
            cluster.setClusterId(clusterId);
            cluster.setName(connection.getName());
            clusterRepository.save(cluster);
            connection.setClusterId(clusterId);
            connection.setId(cluster.getId());
            return connection;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } finally {
            if(admin!=null) admin.close();
        }
    }

    @Override
    public ConnectionDto update(String id, ConnectionDto connection) throws ClusterNotFoundException, KafkaAdminApiException, DuplicatedClusterException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isEmpty()) throw new ClusterNotFoundException("Connection with given cluster ID not found!");
        Cluster cluster = clusterOptional.get();

        boolean endpointChanged = !cluster.getHost().equals(connection.getHost()) || !cluster.getPort().equals(connection.getPort());
        if(endpointChanged) {
            Optional<Cluster> duplicateOptional = clusterRepository.findByHostAndPort(connection.getHost(), connection.getPort());
            if(duplicateOptional.isPresent() && !duplicateOptional.get().getId().equals(cluster.getId()))
                throw new DuplicatedClusterException("There is a cluster created with this ip and port number!");

            Admin admin = null;
            try {
                admin = Admin.create(KafkaHelper.getConnectionProperties(connection.getHost(), connection.getPort()));
                KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
                String clusterId = clusterIdFuture.get();
                closeAdminClient(cluster.getClusterId());
                cluster.setClusterId(clusterId);
                cluster.setHost(connection.getHost());
                cluster.setPort(connection.getPort());
            } catch (Exception e) {
                throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
            } finally {
                if(admin!=null) admin.close();
            }
        }

        cluster.setName(connection.getName());
        clusterRepository.save(cluster);
        return DtoMapper.connectionMapper(cluster);
    }

    @Override
    public ConnectionDto connect(String id) throws ClusterNotFoundException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isEmpty()) throw new ClusterNotFoundException("Connection with given cluster ID not found!");
        ConnectionDto connection = DtoMapper.connectionMapper(clusterOptional.get());
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),connection);
        sessionSettingsService.apply(sessionHolder.getSession());
        if(!agentService.getAgents().isEmpty()) connection.setAgentEnabled(true);
        return connection;
    }

    @Override
    public ConnectionDto disconnect() throws ClusterNotFoundException {
        ConnectionDto connection = (ConnectionDto) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),null);
        // Stop the cluster's Admin so it doesn't keep polling in the background; any other session still using
        // this cluster gets a fresh one from getAdminClient() on its next call.
        if(connection != null) closeAdminClient(connection.getClusterId());
        return connection;
    }

    private void closeAdminClient(String clusterId) {
        if(clusterId == null) return;
        closeAdmin(adminClientMap.remove(clusterId));
    }

    private void closeAdmin(AdminClusterWrapper wrapper) {
        if(wrapper == null || wrapper.getAdmin() == null) return;
        try {
            wrapper.getAdmin().close(ADMIN_CLOSE_TIMEOUT);
        } catch (Exception e) {
            log.warn("Failed to close Kafka admin client - " + e.getMessage());
        }
    }

    @PreDestroy
    void closeAllAdminClients() {
        adminClientMap.keySet().forEach(this::closeAdminClient);
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
