package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.ClusterDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;

import java.util.List;

public interface ClusterService {
    ClusterDto find(String id) throws ClusterNotFoundException;
    List<ClusterDto> findAll();
    ClusterDto save(String name, String host, String port) throws KafkaAdminApiException;
    String getClusterId(String host, String port) throws KafkaAdminApiException;
    ClusterDto getClusterDetails(String id) throws ClusterNotFoundException, KafkaAdminApiException;


}
