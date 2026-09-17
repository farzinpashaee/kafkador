package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.AclBindingDto;
import com.csl.kafkador.exception.AuthorizerNotConfiguredException;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.KafkaAdminApiException;

import java.util.List;

public interface AclService {

    List<AclBindingDto> getAclBindings(String clusterId) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException;
    AclBindingDto createAclBinding(String clusterId, AclBindingDto binding) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException;
    void deleteAclBinding(String clusterId, AclBindingDto binding) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException;

}
