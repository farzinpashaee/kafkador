package com.csl.kafkador.service;

import com.csl.kafkador.component.ClusterCredentialStore;
import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.domain.wrapper.AdminClusterWrapper;
import com.csl.kafkador.domain.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.ClusterCredentialsRequiredException;
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
import org.apache.kafka.common.errors.AuthenticationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private final ClusterCredentialStore credentialStore;

    // How long a cached Admin may take to prove it can still reach its cluster before it is replaced.
    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private static final Duration ADMIN_CLOSE_TIMEOUT = Duration.ofSeconds(1);
    // How long connecting waits for the cluster to answer. A SASL or SSL listener doesn't reject a plaintext
    // client, it just drops the connection, so a wait that runs out is what "needs credentials" looks like.
    static final int PROBE_TIMEOUT_MS = 10_000;
    static final String NO_RESPONSE_MESSAGE = "The cluster didn't accept an unauthenticated connection. "
            + "It may require credentials, or it may be unreachable.";

    // Shared by every session. Each Admin owns a network thread that keeps polling (and, once the brokers are
    // unreachable, re-bootstrapping) until close() is called, so an entry must be closed whenever it is dropped.
    private final Map<String, AdminClusterWrapper> adminClientMap = new ConcurrentHashMap<>();

    /**
     * The Admin for the cluster, using this session's credentials for it if it connected with any. Cached per
     * cluster and credentials, so sessions share an unauthenticated client but never an authenticated one.
     */
    public AdminClusterWrapper getAdminClient(String clusterId) throws ClusterNotFoundException {
        AdminClusterWrapper adminClusterWrapper = new AdminClusterWrapper();
        ClusterCredentialsDto credentials = sessionCredentials(clusterId);
        String cacheKey = adminCacheKey(clusterId, credentials);
        AdminClusterWrapper cached = adminClientMap.get(cacheKey);
        if(cached != null) {
            try {
                KafkaFuture<String> clusterIdFuture = cached.getAdmin().describeCluster().clusterId();
                clusterIdFuture.get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return cached;
            } catch (Exception e){
                log.warn("Admin disconnected! Trying to reconnect... - " + e.getMessage());
                adminClientMap.remove(cacheKey, cached);
                closeAdmin(cached);
            }
        }
        Optional<Cluster> clusterOptional = clusterRepository.findByClusterId(clusterId);
        if(!clusterOptional.isPresent())
            throw new ClusterNotFoundException("Cluster ID not found!");
        Cluster cluster = clusterOptional.get();
        Admin admin = Admin.create(clientProperties(cluster.getHost(), cluster.getPort(), credentials));
        adminClusterWrapper.setAdmin(admin);
        adminClusterWrapper.setCluster(DtoMapper.clusterMapper(cluster));
        adminClientMap.put(cacheKey, adminClusterWrapper);
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
    public ConnectionDto create(ConnectionDto connection) throws KafkaAdminApiException, DuplicatedClusterException, ClusterCredentialsRequiredException {
        Optional<Cluster> clusterOptional = clusterRepository.findByHostAndPort(connection.getHost(), connection.getPort());
        if(clusterOptional.isPresent()) throw new DuplicatedClusterException("There is a cluster created with this ip and port number!");

        ClusterCredentialsDto credentials = connection.getCredentials();
        String clusterId = probeClusterId(connection.getHost(), connection.getPort(), credentials);
        Cluster cluster = new Cluster();
        cluster.setHost(connection.getHost());
        cluster.setPort(connection.getPort());
        cluster.setClusterId(clusterId);
        cluster.setName(connection.getName());
        clusterRepository.save(cluster);
        // Remember them for this session, so connecting right after adding doesn't ask again.
        credentialStore.put(sessionId(), clusterId, credentials);
        connection.setCredentials(null);
        connection.setClusterId(clusterId);
        connection.setId(cluster.getId());
        return connection;
    }

    @Override
    public ConnectionDto update(String id, ConnectionDto connection) throws ClusterNotFoundException, KafkaAdminApiException, DuplicatedClusterException, ClusterCredentialsRequiredException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isEmpty()) throw new ClusterNotFoundException("Connection with given cluster ID not found!");
        Cluster cluster = clusterOptional.get();

        boolean endpointChanged = !cluster.getHost().equals(connection.getHost()) || !cluster.getPort().equals(connection.getPort());
        if(endpointChanged) {
            Optional<Cluster> duplicateOptional = clusterRepository.findByHostAndPort(connection.getHost(), connection.getPort());
            if(duplicateOptional.isPresent() && !duplicateOptional.get().getId().equals(cluster.getId()))
                throw new DuplicatedClusterException("There is a cluster created with this ip and port number!");

            ClusterCredentialsDto credentials = connection.getCredentials() != null
                    ? connection.getCredentials() : sessionCredentials(cluster.getClusterId());
            String clusterId = probeClusterId(connection.getHost(), connection.getPort(), credentials);
            closeAdminClient(cluster.getClusterId());
            credentialStore.put(sessionId(), clusterId, credentials);
            cluster.setClusterId(clusterId);
            cluster.setHost(connection.getHost());
            cluster.setPort(connection.getPort());
        }

        cluster.setName(connection.getName());
        clusterRepository.save(cluster);
        return DtoMapper.connectionMapper(cluster);
    }

    @Override
    public ConnectionDto connect(String id, ClusterCredentialsDto credentials)
            throws ClusterNotFoundException, KafkaAdminApiException, ClusterCredentialsRequiredException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isEmpty()) throw new ClusterNotFoundException("Connection with given cluster ID not found!");
        Cluster cluster = clusterOptional.get();
        ClusterCredentialsDto effective = credentials != null ? credentials : sessionCredentials(cluster.getClusterId());
        probeClusterId(cluster.getHost(), cluster.getPort(), effective);
        credentialStore.put(sessionId(), cluster.getClusterId(), effective);

        ConnectionDto connection = DtoMapper.connectionMapper(cluster);
        connection.setAuthenticated(effective != null);
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),connection);
        sessionSettingsService.apply(sessionHolder.getSession());
        if(!agentService.getAgents().isEmpty()) connection.setAgentEnabled(true);
        return connection;
    }

    /**
     * Asks the cluster for its id with the given credentials (or none), as a check that it accepts this client.
     * A rejected login or a cluster that doesn't answer at all asks the user for (other) credentials.
     */
    String probeClusterId(String host, String port, ClusterCredentialsDto credentials)
            throws KafkaAdminApiException, ClusterCredentialsRequiredException {
        Properties properties = clientProperties(host, port, credentials);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT_MS));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(PROBE_TIMEOUT_MS));
        Admin admin = null;
        try {
            admin = Admin.create(properties);
            return admin.describeCluster().clusterId().get(PROBE_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw rethrow(probeFailure(e.getCause() == null ? e : e.getCause(), credentials));
        } catch (java.util.concurrent.TimeoutException e) {
            throw rethrow(probeFailure(new org.apache.kafka.common.errors.TimeoutException(e.getMessage()), credentials));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaAdminApiException("Interrupted while connecting to the cluster");
        } catch (Exception e) {
            throw rethrow(probeFailure(e, credentials));
        } finally {
            if (admin != null) closeAdmin(admin);
        }
    }

    private static KafkaAdminApiException rethrow(Exception failure) throws ClusterCredentialsRequiredException {
        if (failure instanceof ClusterCredentialsRequiredException credentialsRequired) throw credentialsRequired;
        return (KafkaAdminApiException) failure;
    }

    static Exception probeFailure(Throwable cause, ClusterCredentialsDto credentials) {
        if (cause instanceof AuthenticationException) {
            return new ClusterCredentialsRequiredException(credentials == null
                    ? "The cluster requires credentials: " + cause.getMessage()
                    : "The cluster rejected these credentials: " + cause.getMessage());
        }
        if (cause instanceof org.apache.kafka.common.errors.TimeoutException) {
            return new ClusterCredentialsRequiredException(credentials == null ? NO_RESPONSE_MESSAGE
                    : "The cluster didn't answer with these settings. Check the security protocol and SASL mechanism "
                    + "match the listener, and that the cluster is reachable.");
        }
        return new KafkaAdminApiException("Error initializing or using AdminClient: " + cause.getMessage());
    }

    @Override
    public ConnectionDto disconnect() throws ClusterNotFoundException {
        ConnectionDto connection = (ConnectionDto) sessionHolder.getSession().getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString());
        sessionHolder.getSession().setAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString(),null);
        // Stop the cluster's Admin so it doesn't keep polling in the background; any other session still using
        // this cluster gets a fresh one from getAdminClient() on its next call.
        if(connection != null) {
            credentialStore.remove(sessionId(), connection.getClusterId());
            closeAdminClient(connection.getClusterId());
        }
        return connection;
    }

    /** Closes every cached client of the cluster, whichever credentials it was created with. */
    private void closeAdminClient(String clusterId) {
        if(clusterId == null) return;
        adminClientMap.keySet().stream()
                .filter(key -> key.equals(clusterId) || key.startsWith(clusterId + "#"))
                .toList()
                .forEach(key -> closeAdmin(adminClientMap.remove(key)));
    }

    private void closeAdmin(AdminClusterWrapper wrapper) {
        if(wrapper == null) return;
        closeAdmin(wrapper.getAdmin());
    }

    private void closeAdmin(Admin admin) {
        if(admin == null) return;
        try {
            admin.close(ADMIN_CLOSE_TIMEOUT);
        } catch (Exception e) {
            log.warn("Failed to close Kafka admin client - " + e.getMessage());
        }
    }

    private static String adminCacheKey(String clusterId, ClusterCredentialsDto credentials) {
        return credentials == null ? clusterId : clusterId + "#" + credentials.fingerprint();
    }

    private static Properties clientProperties(String host, String port, ClusterCredentialsDto credentials) {
        Properties properties = KafkaHelper.getConnectionProperties(host, port);
        if (credentials != null) credentials.applyTo(properties);
        return properties;
    }

    /** This request's session id, or null outside a web request (e.g. a background job). */
    private String sessionId() {
        try {
            return sessionHolder.getSession().getId();
        } catch (Exception e) {
            return null;
        }
    }

    private ClusterCredentialsDto sessionCredentials(String clusterId) {
        return credentialStore.get(sessionId(), clusterId);
    }

    @PreDestroy
    void closeAllAdminClients() {
        adminClientMap.values().forEach(this::closeAdmin);
        adminClientMap.clear();
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
        if( Boolean.TRUE.equals(connection.getAuthenticated()) && sessionCredentials(connection.getClusterId()) == null )
            throw new ConnectionSessionExpiredException("Please connect again and enter the cluster's credentials.","/connect");
        return connection;
    }

    @Override
    public Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException {
        ConnectionDto connection = getActiveConnection();
        return clientProperties(connection.getHost(), connection.getPort(), sessionCredentials(connection.getClusterId()));
    }

    @Override
    public Properties getConnectionProperties(String host, String port) {
        String bootstrapServers = host + ":" + port ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

}
