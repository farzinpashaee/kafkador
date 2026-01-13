package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.Request;
import com.csl.kafkador.domain.Topic;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service("TopicService")
@RequiredArgsConstructor
public class TopicService {

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;
    private final ConnectionService connectionService;
    private final MessageSource messageSource;

    public Collection<Topic> getTopics(String clusterId) throws KafkaAdminApiException {

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            KafkaFuture<Collection<TopicListing>> topicsFuture = admin.listTopics().listings();
            Collection<TopicListing> topicList = topicsFuture.get();
            DescribeTopicsResult describeTopicsResult = admin.describeTopics(topicList.stream().map(i->i.name()).collect(Collectors.toList()));
            Map<String, TopicDescription> topicDescriptions = describeTopicsResult.allTopicNames().get();

            return topicDescriptions.entrySet().stream().map( i -> DtoMapper.topicDescriptionMapper(i.getValue()) ).collect(Collectors.toList());
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public Topic createTopic( String clusterId , Topic topic ) throws KafkaAdminApiException {

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            NewTopic newTopic = new NewTopic(topic.getName(),
                    topic.getPartitions(),
                    topic.getReplicatorFactor());
            // Optional: Add topic-specific configurations (e.g., cleanup policy)
            // newTopic.configs(Collections.singletonMap(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT));
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            KafkaFuture<Void> future = result.values().get(topic.getName());
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Failed to create topic: " + e.getMessage());
            }
            return topic;
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public void deleteTopic(Request<String> request ) throws KafkaAdminApiException {

        ConnectionService connectionService = (ConnectionService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONNECTION));

        try (Admin admin = Admin.create(connectionService.getActiveConnectionProperties())) {
            DeleteTopicsResult deleteTopicsResult = admin.deleteTopics(Collections.singleton(request.getBody()));
            deleteTopicsResult.all().get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public Topic getTopic( String clusterId, String name ) throws KafkaAdminApiException {

        try{
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            Topic topic = new Topic();
            Set<String> topicNames = new HashSet<>();
            topicNames.add(name);
            DescribeTopicsResult result = admin.describeTopics(topicNames);

            Map<String, TopicDescription> topicDescriptions = result.allTopicNames().get();
            TopicDescription topicDescription = topicDescriptions.get(name);
            if (topicDescription != null) {
                topic.setName(topicDescription.name());
                topic.setId(topicDescription.topicId().toString());
                topic.setPartitions(topicDescription.partitions().size());
                topic.setConfig(getBrokerConfiguration(clusterId, topicDescription.name()));
                return topic;
            }

        } catch (ConnectionSessionExpiredException e){
            throw e;
        }  catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
        return null;
    }

    public List<ConfigEntry> getBrokerConfiguration( String clusterId, String name ) throws KafkaAdminApiException {
        List<ConfigEntry> result = new ArrayList<>();
        Locale locale = LocaleContextHolder.getLocale();

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            DescribeConfigsResult describeConfigsResult = admin.describeConfigs(Collections.singleton(configResource));
            describeConfigsResult.all().get().forEach((resource, config) -> {
                System.out.println("Configuration for " + resource.name() + ":");
                config.entries().forEach(c -> {
                    String documentation = c.documentation();
                    if( documentation == null ){
                        documentation = messageSource.getMessage("broker.documentation." + c.name(), null, null , locale);
                    }
                    result.add(new ConfigEntry( c.name(), c.value(), c.source().name(), c.isSensitive(), c.isReadOnly(),
                            c.type().name(), documentation, ViewHelper.getDocumentationLink(c.name())));
                });
            });
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

    public void updateConfig( String clusterId, String topicId, ConfigEntry configEntry ) throws KafkaAdminApiException {

        try{
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            ConfigResource brokerResource = new ConfigResource(ConfigResource.Type.TOPIC, topicId);
            List<AlterConfigOp> ops = List.of(
                    new AlterConfigOp(
                            new org.apache.kafka.clients.admin.ConfigEntry(configEntry.name(), configEntry.value()),
                            AlterConfigOp.OpType.SET
                    )
            );
            Map<ConfigResource, Collection<AlterConfigOp>> updateRequest = Map.of(brokerResource, ops);
            admin.incrementalAlterConfigs(updateRequest).all().get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }

    }

}
