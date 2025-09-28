package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.dto.Broker;
import com.csl.kafkador.dto.ClusterDetails;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service("ClusterService")
@RequiredArgsConstructor
public class ClusterService {

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;
    private final MessageSource messageSource;

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
            clusterDetails.setBrokers(nodes.stream().map(i -> DtoMapper.clusterNodeMapper(i,sizeMap)).collect(Collectors.toList()));
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

    public Broker getBrokerDetail( String id ) throws KafkaAdminApiException {
        Broker broker = new Broker();
        broker.setId(id);
        broker.setConfig(getBrokerConfiguration(id));
        return broker;
    }

    public Map<ConfigResource, Config> getBrokerConfiguration( Collection<Node> nodes ){
        return null;
    }

    public List<ConfigEntry> getBrokerConfiguration( String id ) throws KafkaAdminApiException {
        List<ConfigEntry> result = new ArrayList<>();
        List<ConfigResource> resources = new ArrayList<>();
        resources.add( new ConfigResource(ConfigResource.Type.BROKER,String.valueOf(id)));
        Locale locale = LocaleContextHolder.getLocale();

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            Map<ConfigResource, Config> configMap = admin.describeConfigs(resources)
                    .all()
                    .get();
            Optional<Config> optionalConfig = configMap.entrySet().stream().map(Map.Entry::getValue).findFirst();
            if(optionalConfig.isPresent()){
                optionalConfig.get().entries().stream().forEach( c -> {
                    String documentation = c.documentation();
                    if( documentation == null ){
                        documentation = messageSource.getMessage("broker.documentation." + c.name(), null, null , locale);
                    }
                    result.add(new ConfigEntry( c.name(), c.value(), c.source().name(), c.isSensitive(), c.isReadOnly(),
                            c.type().name(), documentation, ViewHelper.getDocumentationLink(c.name())));
                });
            } else {
                throw new KafkadorException("Node Config not found!");
            }
            Collections.sort(result, (o1, o2) -> {
                return o1.name().compareTo(o2.name());
            });
            return result;
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public void getClusterHealthStatus() throws KafkaAdminApiException {

        ClusterDetails clusterDetails = new ClusterDetails();
        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {

            DescribeClusterResult clusterResult = admin.describeCluster();
            Collection<Node> nodes = clusterResult.nodes().get();
            Node controller = clusterResult.controller().get();

            System.out.println("✅ Cluster ID: " + clusterResult.clusterId().get());
            System.out.println("✅ Brokers (expected/actual): " + nodes.size() + " / " + nodes.size());
            System.out.println("✅ Controller Broker ID: " + controller.id());


        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }

    }


}
