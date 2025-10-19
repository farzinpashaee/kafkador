package com.csl.kafkador.service;

import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.KafkaHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service("ClusterService")
@RequiredArgsConstructor
public class ClusterServiceImp implements ClusterService {

    private final ClusterRepository clusterRepository;
    private final ConnectionService connectionService;
    @Qualifier("ObserverKafkadorConfigService")
    private final ConfigService<ObserverConfigDto,ObserverConfigDto.ObserverCluster> configService;

    @Override
    public ClusterDto find(String id) throws ClusterNotFoundException {
        Optional<Cluster> clusterOptional = clusterRepository.findById(id);
        if(clusterOptional.isPresent()){
            return DtoMapper.clusterMapper(clusterOptional.get());
        } else {
            throw new ClusterNotFoundException("Connection with given ID not found!");
        }
    }

    @Override
    public List<ClusterDto> findAll() {
        return clusterRepository.findAll().stream()
                .map(DtoMapper::clusterMapper)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public ClusterDto save(String name, String host, String port) throws KafkaAdminApiException {
        String clusterId = getClusterId(host,port);
        Cluster cluster = new Cluster();
        cluster.setId(clusterId);
        cluster.setId(name);
        cluster.setHost(host);
        cluster.setPort(port);
        clusterRepository.save(cluster);

        ObserverConfigDto.ObserverCluster observerCluster = new ObserverConfigDto.ObserverCluster();
        observerCluster.setClusterId(clusterId);
        observerCluster.setEnabled(true);
        observerCluster.setObservers(ObserverConfigDto.defaultObserverConfig());
        configService.save(observerCluster);
        return DtoMapper.clusterMapper(cluster);
    }


    @Override
    public String getClusterId(String host, String port) throws KafkaAdminApiException {
        Properties properties = KafkaHelper.getConnectionProperties(host,port);
        try (Admin admin = Admin.create(properties)) {
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            return clusterIdFuture.get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    @Override
    public ClusterDto getClusterDetails(String id) throws ClusterNotFoundException, KafkaAdminApiException {
        ClusterDto clusterDetails;
        try{
            Admin admin = connectionService.getAdminClient(id).getAdmin();
            KafkaFuture<String> clusterIdFuture = admin.describeCluster().clusterId();
            KafkaFuture<Collection<Node>> clusterNodesFuture = admin.describeCluster().nodes();
            KafkaFuture<Node> clusterControllerFuture = admin.describeCluster().controller();

            Collection<Node> nodes = clusterNodesFuture.get();
            List<Integer> brokerIds = nodes.stream().map(Node::id).toList();
            Map<Integer, Long> sizeMap = KafkaHelper.getReplicaSize(admin.describeLogDirs(brokerIds).allDescriptions().get());

            clusterDetails = connectionService.getAdminClient(id).getCluster();
            clusterDetails.setId(clusterIdFuture.get());
            clusterDetails.setBrokers(nodes.stream().map(i -> DtoMapper.clusterNodeMapper(i,sizeMap)).collect(Collectors.toList()));
            clusterDetails.setController(DtoMapper.clusterNodeMapper(clusterControllerFuture.get(),sizeMap));
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return clusterDetails;
    }


}
