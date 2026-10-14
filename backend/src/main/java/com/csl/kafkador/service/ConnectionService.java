package com.csl.kafkador.service;

import com.csl.kafkador.domain.wrapper.AdminClusterWrapper;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto;
import com.csl.kafkador.exception.InvalidClusterSecurityException;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.ClusterCredentialsRequiredException;
import com.csl.kafkador.exception.DuplicatedClusterException;
import com.csl.kafkador.exception.KafkaAdminApiException;

import java.util.List;
import java.util.Properties;

public interface ConnectionService {

    AdminClusterWrapper getAdminClient(String id) throws ClusterNotFoundException;
    ConnectionDto create( ConnectionDto connectionDto ) throws KafkaAdminApiException, DuplicatedClusterException, ClusterCredentialsRequiredException, InvalidClusterSecurityException;
    ConnectionDto update( String id, ConnectionDto connectionDto ) throws ClusterNotFoundException, KafkaAdminApiException, DuplicatedClusterException, ClusterCredentialsRequiredException, InvalidClusterSecurityException;
    void delete( String id ) throws ClusterNotFoundException;
    /** Connects this session, checking the cluster accepts it; {@code credentials} only when the cluster asked for them. */
    ConnectionDto connect( String clusterId, ClusterCredentialsDto credentials ) throws ClusterNotFoundException, KafkaAdminApiException, ClusterCredentialsRequiredException, InvalidClusterSecurityException;
    ConnectionDto disconnect() throws ClusterNotFoundException;
    List<ConnectionDto> getConnections();
    ConnectionDto getActiveConnection() throws ConnectionSessionExpiredException;
    Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException;
    Properties getConnectionProperties(String host, String port);

    /** Settings > Security of the active cluster. */
    ClusterSecurityConfigDto getSecurityConfig() throws ConnectionSessionExpiredException;
    /** Saves the active cluster's security settings once the cluster has accepted a connection with them. */
    ClusterSecurityConfigDto saveSecurityConfig(ClusterSecurityConfigDto config) throws InvalidClusterSecurityException;
    ClusterSecurityConfigDto saveTruststore(byte[] content, String fileName, String type, String password) throws InvalidClusterSecurityException;
    ClusterSecurityConfigDto removeTruststore() throws ConnectionSessionExpiredException;

}
