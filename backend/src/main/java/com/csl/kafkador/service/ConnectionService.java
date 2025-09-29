package com.csl.kafkador.service;

import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.dto.ConnectionDto;
import com.csl.kafkador.exception.KafkaAdminApiException;

import java.util.List;
import java.util.Properties;

public interface ConnectionService {

    ConnectionDto create( ConnectionDto connectionDto ) throws KafkaAdminApiException;
    ConnectionDto connect( String clusterId ) throws ClusterNotFoundException;
    ConnectionDto disconnect() throws ClusterNotFoundException;
    List<ConnectionDto> getConnections();
    ConnectionDto getActiveConnection() throws ConnectionSessionExpiredException;
    Properties getActiveConnectionProperties() throws ConnectionSessionExpiredException;
    Properties getConnectionProperties(String host, String port);

}
