package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.dto.BrokerDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.KafkaHelper;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service("BrokerService")
@RequiredArgsConstructor
public class BrokerServiceImp implements BrokerService {

    private final ConnectionService connectionService;
    private final MessageSource messageSource;

    @Override
    public BrokerDto getDetail(String clusterId, String brokerId) throws KafkaAdminApiException {
        BrokerDto broker = new BrokerDto();
        broker.setId(brokerId);
        broker.setConfig(getConfigurations(clusterId,brokerId));
        return broker;
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
