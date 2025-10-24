package com.csl.kafkador.service;

import com.csl.kafkador.exception.ClusterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeAclsResult;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.springframework.stereotype.Service;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourceType;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Service("AclService")
@RequiredArgsConstructor
public class AclServiceImp implements AclService {

    private final ConnectionService connectionService;

    public void getAclBindings(String clusterId) throws ClusterNotFoundException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            AclBindingFilter filter = new AclBindingFilter(
                    new ResourcePatternFilter(ResourceType.ANY, null, PatternType.LITERAL),
                    new AccessControlEntryFilter(null, null, AclOperation.ANY, AclPermissionType.ANY)
            );

            DescribeAclsResult result = admin.describeAcls(filter);
            Collection<AclBinding> acls = result.values().get(5, TimeUnit.SECONDS); // timeout as appropriate

            for (AclBinding b : acls) {
                System.out.println("Resource: " + b.pattern());
                System.out.println("  Principal: " + b.entry().principal());
                System.out.println("  Host: " + b.entry().host());
                System.out.println("  Operation: " + b.entry().operation());
                System.out.println("  Permission: " + b.entry().permissionType());
                System.out.println("-----");
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }

}
