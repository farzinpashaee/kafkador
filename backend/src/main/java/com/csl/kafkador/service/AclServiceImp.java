package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.AclBindingDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeAclsResult;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service("AclService")
@RequiredArgsConstructor
@Slf4j
public class AclServiceImp implements AclService {

    private final ConnectionService connectionService;

    public List<AclBindingDto> getAclBindings(String clusterId) throws ClusterNotFoundException, KafkaAdminApiException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            AclBindingFilter filter = new AclBindingFilter(
                    new ResourcePatternFilter(ResourceType.ANY, null, PatternType.ANY),
                    new AccessControlEntryFilter(null, null, AclOperation.ANY, AclPermissionType.ANY)
            );

            DescribeAclsResult result = admin.describeAcls(filter);
            Collection<AclBinding> acls = result.values().get();

            return acls.stream().map(this::toDto).collect(Collectors.toList());
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    private AclBindingDto toDto(AclBinding binding) {
        return new AclBindingDto()
                .setResourceType(binding.pattern().resourceType().name())
                .setResourceName(binding.pattern().name())
                .setPatternType(binding.pattern().patternType().name())
                .setPrincipal(binding.entry().principal())
                .setHost(binding.entry().host())
                .setOperation(binding.entry().operation().name())
                .setPermissionType(binding.entry().permissionType().name());
    }

}
