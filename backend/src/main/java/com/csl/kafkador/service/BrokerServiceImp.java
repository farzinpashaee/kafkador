package com.csl.kafkador.service;

import com.csl.kafkador.domain.dto.BrokerDto;
import com.csl.kafkador.exception.*;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.apache.kafka.common.Node;

import java.util.*;

@Slf4j
@Service("BrokerService")
@RequiredArgsConstructor
public class BrokerServiceImp implements BrokerService {

    private final ConnectionService connectionService;
    private final MessageSource messageSource;

    @Override
    public BrokerDto getDetail(String clusterId, String brokerId) throws KafkaAdminApiException,BrokerNotFoundException {
        BrokerDto response = new BrokerDto();
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            DescribeClusterResult clusterResult = admin.describeCluster();
            Collection<Node> nodes = clusterResult.nodes().get();
            Optional<Node> brokerOptional = nodes.stream().filter( n -> n.id() == Integer.parseInt(brokerId) ).findFirst();
            if( brokerOptional.isPresent() ){
                Node broker = brokerOptional.get();
                response.setId(brokerId);
                response.setHost(broker.host());
                response.setPort(broker.port());
                response.setRack(broker.rack());
                response.setConfig(getConfigurations(clusterId,brokerId));
            } else {
                throw new BrokerNotFoundException("Broker with given ID not found!");
            }
        } catch (ConnectionSessionExpiredException | BrokerNotFoundException e ){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return response;
    }

    @Override
    public List<ConfigEntry> getConfigurations(String clusterId, String brokerId) throws KafkaAdminApiException {

        List<ConfigEntry> result = new ArrayList<>();
        List<ConfigResource> resources = new ArrayList<>();
        resources.add( new ConfigResource(ConfigResource.Type.BROKER,String.valueOf(brokerId)));
        Locale locale = LocaleContextHolder.getLocale();

        try{
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
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
}
