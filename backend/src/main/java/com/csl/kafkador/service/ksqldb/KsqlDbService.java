package com.csl.kafkador.service.ksqldb;

import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
import com.csl.kafkador.domain.dto.KsqlQueryDto;
import com.csl.kafkador.domain.dto.KsqlServerInfoDto;
import com.csl.kafkador.domain.dto.KsqlSourceDefinitionDto;
import com.csl.kafkador.domain.dto.KsqlStatementRequestDto;
import com.csl.kafkador.domain.dto.KsqlStatementResultDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KsqlDbApiException;
import com.csl.kafkador.exception.KsqlStatementException;
import com.csl.kafkador.util.KsqlStatements.SourceType;

import java.util.List;

public interface KsqlDbService {

    KsqlDbConfigDto getConfig(String clusterId);
    /** Whether a ksqlDB URL is saved for the cluster; unlike getConfig, never probes for a server. */
    boolean isConfigured(String clusterId);
    KsqlDbConfigDto saveConfig(String url, String clusterId);
    KsqlServerInfoDto getServerInfo(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlStreamDto> getStreams(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlTableDto> getTables(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlQueryDto> getQueries(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    void terminateQuery(String queryId, String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    KsqlSourceDefinitionDto getSourceDefinition(SourceType type, String name, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException;
    KsqlStatementResultDto createSource(SourceType type, KsqlStatementRequestDto request, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException;
    KsqlStatementResultDto updateSource(SourceType type, String name, KsqlStatementRequestDto request, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException;
    KsqlStatementResultDto dropSource(SourceType type, String name, boolean deleteTopic, String clusterId)
            throws ConfigNotFoundException, KsqlDbApiException, KsqlStatementException;

}
