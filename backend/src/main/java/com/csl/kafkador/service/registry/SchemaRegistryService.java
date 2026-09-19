package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.CompatibilityCheckResultDto;
import com.csl.kafkador.domain.dto.CompatibilityConfigDto;
import com.csl.kafkador.domain.dto.SchemaLookupResultDto;
import com.csl.kafkador.domain.dto.SchemaRegisterRequestDto;
import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import com.csl.kafkador.domain.dto.SchemaVersionDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.SchemaRegistryApiException;

import java.util.List;

public interface SchemaRegistryService {

    SchemaRegistryDto getSubjects(String clusterId);
    SchemaRegistryConfigDto getConfig(String clusterId);
    SchemaRegistryConfigDto saveConfig(String url, String clusterId);

    List<Integer> getVersions(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    SchemaVersionDto getVersion(String subject, String version, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    SchemaVersionDto registerSchema(String subject, SchemaRegisterRequestDto request, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    CompatibilityCheckResultDto checkCompatibility(String subject, SchemaRegisterRequestDto request, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    List<Integer> deleteSubject(String subject, boolean permanent, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    void deleteVersion(String subject, String version, boolean permanent, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;

    CompatibilityConfigDto getGlobalCompatibility(String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    CompatibilityConfigDto saveGlobalCompatibility(CompatibilityConfigDto config, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    CompatibilityConfigDto getSubjectCompatibility(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    CompatibilityConfigDto saveSubjectCompatibility(String subject, CompatibilityConfigDto config, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;
    void clearSubjectCompatibility(String subject, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;

    SchemaLookupResultDto lookupById(Integer id, String clusterId) throws ConfigNotFoundException, SchemaRegistryApiException;

}
