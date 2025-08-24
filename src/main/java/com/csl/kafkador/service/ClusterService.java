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
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

            Collection<Node> nodes = clusterNodesFuture.get();
            List<Integer> brokerIds = nodes.stream().map(Node::id).toList();
            Map<Integer, Long> sizeMap = getBrokerReplicaSize(admin.describeLogDirs(brokerIds).allDescriptions().get());

            clusterDetails.setId(clusterIdFuture.get());
            clusterDetails.setNodes(nodes.stream().map( i -> DtoMapper.clusterNodeMapper(i,sizeMap)).collect(Collectors.toList()));
            clusterDetails.setController(DtoMapper.clusterNodeMapper(clusterControllerFuture.get(),sizeMap));
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return clusterDetails;
    }

    public Map<Integer, Long> getBrokerReplicaSize( Map<Integer, Map<String, LogDirDescription>> map ){
        Map<Integer, Long> brokerBytes = new LinkedHashMap<>();

        for (var brokerEntry : map.entrySet()) {
            int brokerId = brokerEntry.getKey();
            long total = 0L;
            for (LogDirDescription ldd : brokerEntry.getValue().values()) {
                if (ldd.error() != null && !(ldd.error() instanceof org.apache.kafka.common.errors.ApiException) )
                    continue;
                for (ReplicaInfo ri : ldd.replicaInfos().values()) {
                    total += ri.size();
                }
            }
            brokerBytes.put(brokerId, total);
        }
        return brokerBytes;
    }

}
