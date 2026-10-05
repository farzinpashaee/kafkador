package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.BrokerOverviewDto;
import com.csl.kafkador.domain.dto.ClusterDto;
import com.csl.kafkador.domain.dto.ClusterOverviewDto;
import com.csl.kafkador.domain.dto.ObserverConfigDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.csl.kafkador.util.ClusterOverviewCalculator;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.KafkaHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.FeatureMetadata;
import org.apache.kafka.clients.admin.FinalizedVersionRange;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.QuorumInfo;
import org.apache.kafka.clients.admin.SupportedVersionRange;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service("ClusterService")
@RequiredArgsConstructor
public class ClusterServiceImp implements ClusterService {

    private static final long OVERVIEW_TIMEOUT_SECONDS = 15;
    private static final String METADATA_VERSION_FEATURE = "metadata.version";

    private final ClusterRepository clusterRepository;
    private final ConnectionService connectionService;
    @Qualifier("ObserverKafkadorConfigService")
    private final KafkadorConfigService<ObserverConfigDto,ObserverConfigDto> kafkadorConfigService;

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
        cluster.setClusterId(clusterId);
        cluster.setName(name);
        cluster.setHost(host);
        cluster.setPort(port);
        clusterRepository.save(cluster);
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
            clusterDetails.setClusterId(clusterIdFuture.get());
            clusterDetails.setBrokers(nodes.stream().map(i -> DtoMapper.clusterNodeMapper(i,sizeMap)).collect(Collectors.toList()));
            clusterDetails.setController(DtoMapper.clusterNodeMapper(clusterControllerFuture.get(),sizeMap));
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return clusterDetails;
    }

    @Override
    public ClusterOverviewDto getClusterOverview(String id) throws ClusterNotFoundException, KafkaAdminApiException {
        try {
            Admin admin = connectionService.getAdminClient(id).getAdmin();
            DescribeClusterResult cluster = admin.describeCluster();
            Collection<Node> nodes = await(cluster.nodes());
            Node controller = await(cluster.controller());

            Set<String> topicNames = await(admin.listTopics(new ListTopicsOptions().listInternal(true)).names());
            Collection<TopicDescription> topics = topicNames.isEmpty() ? List.of()
                    : await(admin.describeTopics(topicNames).allTopicNames()).values();

            ClusterOverviewDto overview = ClusterOverviewCalculator.calculate(nodes, topics, describeLogDirs(admin, nodes));

            // KRaft clusters answer DescribeQuorum and report the real active controller; ZooKeeper clusters don't
            // support it and describeCluster's controller is the active one.
            String activeControllerId = controller == null || controller.isEmpty() ? null : controller.idString();
            try {
                QuorumInfo quorum = await(admin.describeMetadataQuorum().quorumInfo());
                overview.setControllerType("KRaft");
                activeControllerId = String.valueOf(quorum.leaderId());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnsupportedVersionException) overview.setControllerType("ZooKeeper");
                else log.warn("Could not describe the metadata quorum of cluster {}: {}", id, e.getMessage());
            } catch (Exception e) {
                log.warn("Could not describe the metadata quorum of cluster {}: {}", id, e.getMessage());
            }
            overview.setActiveControllerId(activeControllerId);
            for (BrokerOverviewDto broker : overview.getBrokers()) {
                broker.setActiveController(broker.getId().equals(activeControllerId));
            }

            overview.setVersion(clusterVersion(admin, nodes));
            return overview;
        } catch (ConnectionSessionExpiredException | ClusterNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    private Map<Integer, Map<String, LogDirDescription>> describeLogDirs(Admin admin, Collection<Node> nodes) {
        try {
            return await(admin.describeLogDirs(nodes.stream().map(Node::id).toList()).allDescriptions());
        } catch (Exception e) {
            log.warn("Could not describe broker log dirs, disk usage will be unavailable: {}", e.getMessage());
            return null;
        }
    }

    // Release names of metadata.version feature levels, starting at level 7 (3.3-IV3, the oldest level a KRaft
    // cluster can run), from org.apache.kafka.server.common.MetadataVersion. Newer levels show as "metadata.version N".
    private static final int FIRST_METADATA_VERSION_LEVEL = 7;
    private static final String[] METADATA_VERSION_NAMES = {
            "3.3-IV3", "3.4-IV0", "3.5-IV0", "3.5-IV1", "3.5-IV2", "3.6-IV0", "3.6-IV1", "3.6-IV2",
            "3.7-IV0", "3.7-IV1", "3.7-IV2", "3.7-IV3", "3.7-IV4", "3.8-IV0", "3.9-IV0",
            "4.0-IV0", "4.0-IV1", "4.0-IV2", "4.0-IV3", "4.1-IV0", "4.1-IV1", "4.2-IV0", "4.2-IV1"
    };

    static String metadataVersionName(int level) {
        int index = level - FIRST_METADATA_VERSION_LEVEL;
        return index >= 0 && index < METADATA_VERSION_NAMES.length ? METADATA_VERSION_NAMES[index] : "metadata.version " + level;
    }

    /**
     * The inter-broker protocol version brokers report (e.g. "3.9-IV0"). Kafka 4 removed that setting, so fall back
     * to the finalized metadata.version, and then - for clusters that don't finalize it, like Confluent Platform 8
     * which uses its own confluent.metadata.version - to the newest metadata.version the brokers' software supports.
     */
    private String clusterVersion(Admin admin, Collection<Node> nodes) {
        if (nodes.isEmpty()) return null;
        try {
            ConfigResource broker = new ConfigResource(ConfigResource.Type.BROKER, nodes.iterator().next().idString());
            Config config = await(admin.describeConfigs(List.of(broker)).all()).get(broker);
            org.apache.kafka.clients.admin.ConfigEntry ibp = config == null ? null : config.get("inter.broker.protocol.version");
            if (ibp != null && ibp.value() != null && !ibp.value().isBlank()) return ibp.value();
        } catch (Exception e) {
            log.warn("Could not read the broker protocol version: {}", e.getMessage());
        }
        try {
            FeatureMetadata features = await(admin.describeFeatures().featureMetadata());
            FinalizedVersionRange finalized = features.finalizedFeatures().get(METADATA_VERSION_FEATURE);
            if (finalized != null) return metadataVersionName(finalized.maxVersionLevel());
            SupportedVersionRange supported = features.supportedFeatures().get(METADATA_VERSION_FEATURE);
            if (supported != null) return metadataVersionName(supported.maxVersion());
        } catch (Exception e) {
            log.warn("Could not read the cluster's finalized features: {}", e.getMessage());
        }
        return null;
    }

    private static <T> T await(KafkaFuture<T> future) throws Exception {
        return future.get(OVERVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }


}
