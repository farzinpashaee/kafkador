package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.AclBindingDto;
import com.csl.kafkador.exception.AuthorizerNotConfiguredException;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeAclsResult;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.errors.SecurityDisabledException;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service("AclService")
@RequiredArgsConstructor
@Slf4j
public class AclServiceImp implements AclService {

    private final ConnectionService connectionService;

    public List<AclBindingDto> getAclBindings(String clusterId) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
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
            throw translateFailure(e);
        }
    }

    @Override
    public AclBindingDto createAclBinding(String clusterId, AclBindingDto binding) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            AclBinding aclBinding = new AclBinding(
                    new ResourcePattern(
                            ResourceType.valueOf(binding.getResourceType()),
                            binding.getResourceName(),
                            PatternType.valueOf(binding.getPatternType())
                    ),
                    new AccessControlEntry(
                            binding.getPrincipal(),
                            binding.getHost(),
                            AclOperation.valueOf(binding.getOperation()),
                            AclPermissionType.valueOf(binding.getPermissionType())
                    )
            );
            admin.createAcls(List.of(aclBinding)).all().get();
            return binding;
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw translateFailure(e);
        }
    }

    @Override
    public void deleteAclBinding(String clusterId, AclBindingDto binding) throws ClusterNotFoundException, KafkaAdminApiException, AuthorizerNotConfiguredException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            AclBindingFilter filter = new AclBindingFilter(
                    new ResourcePatternFilter(
                            ResourceType.valueOf(binding.getResourceType()),
                            binding.getResourceName(),
                            PatternType.valueOf(binding.getPatternType())
                    ),
                    new AccessControlEntryFilter(
                            binding.getPrincipal(),
                            binding.getHost(),
                            AclOperation.valueOf(binding.getOperation()),
                            AclPermissionType.valueOf(binding.getPermissionType())
                    )
            );
            admin.deleteAcls(List.of(filter)).all().get();
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw translateFailure(e);
        }
    }

    /**
     * Kafka reports a missing ACL authorizer as a SecurityDisabledException (usually
     * wrapped in an ExecutionException from the admin future) rather than a normal
     * "bad request" — surface that as its own exception so the UI can point the user
     * at the fix instead of a generic "something went wrong". Callers do `throw translateFailure(e)`.
     */
    private KafkaAdminApiException translateFailure(Exception e) throws AuthorizerNotConfiguredException {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof SecurityDisabledException) {
            throw new AuthorizerNotConfiguredException(
                    "No ACL authorizer is configured on this Kafka broker, so ACLs can't be listed, created, or deleted yet.");
        }
        return new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
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
