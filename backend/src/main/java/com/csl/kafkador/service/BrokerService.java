package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.BrokerDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.record.ConfigEntry;

import java.util.List;

public interface BrokerService {

    BrokerDto getDetail(String clusterId, String brokerId) throws KafkaAdminApiException;
    List<ConfigEntry> getConfigurations(String clusterId, String brokerId) throws KafkaAdminApiException, ClusterNotFoundException;

}
