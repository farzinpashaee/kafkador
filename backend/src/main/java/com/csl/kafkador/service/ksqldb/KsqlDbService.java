package com.csl.kafkador.service.ksqldb;

import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
import com.csl.kafkador.domain.dto.KsqlQueryDto;
import com.csl.kafkador.domain.dto.KsqlServerInfoDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.KsqlDbApiException;

import java.util.List;

public interface KsqlDbService {

    KsqlDbConfigDto getConfig(String clusterId);
    KsqlDbConfigDto saveConfig(String url, String clusterId);
    KsqlServerInfoDto getServerInfo(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlStreamDto> getStreams(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlTableDto> getTables(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    List<KsqlQueryDto> getQueries(String clusterId) throws ConfigNotFoundException, KsqlDbApiException;
    void terminateQuery(String queryId, String clusterId) throws ConfigNotFoundException, KsqlDbApiException;

}
