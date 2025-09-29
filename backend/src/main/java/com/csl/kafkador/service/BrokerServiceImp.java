package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.dto.BrokerDto;
import com.csl.kafkador.exception.ClusterNotFoundException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.KafkadorException;
import com.csl.kafkador.record.ConfigEntry;
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
    public BrokerDto getDetail(String id) {
        return null;
    }

    public Map<Integer, Long> getReplicaSize(Map<Integer, Map<String, LogDirDescription>> map ){
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

    @Override
    public List<ConfigEntry> getConfigurations(String id) throws KafkaAdminApiException, ClusterNotFoundException {

        List<ConfigEntry> result = new ArrayList<>();
        List<ConfigResource> resources = new ArrayList<>();
        resources.add( new ConfigResource(ConfigResource.Type.BROKER,String.valueOf(id)));
        Locale locale = LocaleContextHolder.getLocale();

        Properties properties = connectionService.getConnectionProperties(id);
        try (Admin admin = Admin.create(properties)) {
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
