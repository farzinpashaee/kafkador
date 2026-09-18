package com.csl.kafkador.service.connect;

import com.csl.kafkador.domain.dto.ConnectorCreateRequestDto;
import com.csl.kafkador.domain.dto.ConnectorDto;
import com.csl.kafkador.domain.dto.ConnectorPluginDto;
import com.csl.kafkador.domain.dto.KafkaConnectConfigDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KafkaConnectApiException;

import java.util.List;
import java.util.Map;

public interface KafkaConnectService {

    KafkaConnectConfigDto getConfig(String clusterId);
    KafkaConnectConfigDto saveConfig(String url, String clusterId);
    List<ConnectorPluginDto> getPlugins(String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    List<ConnectorDto> getConnectors(String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    ConnectorDto getConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    ConnectorDto createConnector(ConnectorCreateRequestDto request, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    ConnectorDto updateConnectorConfig(String name, Map<String, String> config, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    void pauseConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    void resumeConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    void restartConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;
    void deleteConnector(String name, String clusterId) throws ConfigNotFoundException, KafkaConnectApiException;

}
