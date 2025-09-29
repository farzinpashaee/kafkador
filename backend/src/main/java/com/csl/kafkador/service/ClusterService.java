package com.csl.kafkador.service;

import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Cluster;

import java.util.List;
import java.util.Properties;

public interface ClusterService {
    ClusterDto find(String id) throws ClusterNotFoundException;
    List<ClusterDto> findAll();
    ClusterDto save(String host, String port) throws KafkaAdminApiException;
    String getClusterId(String host, String port) throws KafkaAdminApiException;
    ClusterDto getClusterDetails(String id) throws ClusterNotFoundException, KafkaAdminApiException;


}
