package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.dto.Request;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.util.DtoMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

@Service("ClusterService")
@RequiredArgsConstructor
public class ClusterService {

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;

    public ClusterDetails getClusterDetails() throws KafkaAdminApiException {

        ClusterDetails clusterDetails = new ClusterDetails();
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            KafkaFuture<Collection<Node>> clusterNodesFuture = admin.describeCluster().nodes();
            KafkaFuture<Node> clusterControllerFuture = admin.describeCluster().controller();
            clusterDetails.setId(clusterIdFuture.get());
            clusterDetails.setNodes(clusterNodesFuture.get().stream().map(DtoMapper::clusterNodeMapper).collect(Collectors.toList()));
            clusterDetails.setController(DtoMapper.clusterNodeMapper(clusterControllerFuture.get()));
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return clusterDetails;
    }

}
