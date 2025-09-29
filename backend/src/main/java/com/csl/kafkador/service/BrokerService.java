package com.csl.kafkador.service;

import com.csl.kafkador.dto.BrokerDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.record.ConfigEntry;
import org.apache.kafka.clients.admin.LogDirDescription;

import java.util.List;
import java.util.Map;

public interface BrokerService {

    BrokerDto getDetail(String id);
    List<ConfigEntry> getConfigurations(String id ) throws KafkaAdminApiException, ClusterNotFoundException;

}
