package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.exception.KafkaAdminApiException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service("ClusterService")
public class ClusterService {

    @Autowired
    KafkadorContext kafkadorContext;

    public ClusterDetails getClusterDetails() throws KafkaAdminApiException {

        ClusterDetails clusterDetails = new ClusterDetails();

        try (Admin admin = Admin.create(kafkadorContext.getKafkaAdminApiConfig())) {
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            KafkaFuture<Collection<Node>> clusterNodesFuture = admin.describeCluster().nodes();
            KafkaFuture<Node> clusterControllerFuture = admin.describeCluster().controller();
            clusterDetails.setId(clusterIdFuture.get());
            clusterDetails.setNodes(clusterNodesFuture.get());
            clusterDetails.setController(clusterControllerFuture.get());
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return clusterDetails;
    }

}
