package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.Topic;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.TopicAlreadyExistsException;
import com.csl.kafkador.exception.TopicNotFoundException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service("TopicService")
@RequiredArgsConstructor
@Slf4j
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
            log.error("Failed to list topics for cluster {}", clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public Topic createTopic( String clusterId , Topic topic ) throws KafkaAdminApiException, TopicAlreadyExistsException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            NewTopic newTopic = new NewTopic(topic.getName(),
                    topic.getPartitions(),
                    topic.getReplicatorFactor());
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            KafkaFuture<Void> future = result.values().get(topic.getName());
            future.get();
            return topic;
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                throw new TopicAlreadyExistsException("A topic named '" + topic.getName() + "' already exists");
            }
            log.error("Failed to create topic {} on cluster {}", topic.getName(), clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create topic {} on cluster {}", topic.getName(), clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public void deleteTopic( String clusterId, String name) throws KafkaAdminApiException, TopicNotFoundException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            DeleteTopicsResult deleteTopicsResult = admin.deleteTopics(Collections.singleton(name));
            deleteTopicsResult.all().get();
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new TopicNotFoundException("Topic '" + name + "' does not exist");
            }
            log.error("Failed to delete topic {} on cluster {}", name, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete topic {} on cluster {}", name, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }


    public Topic getTopic( String clusterId, String name ) throws KafkaAdminApiException, TopicNotFoundException {

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
            throw new TopicNotFoundException("Topic '" + name + "' does not exist");
        } catch (ConnectionSessionExpiredException | TopicNotFoundException e){
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new TopicNotFoundException("Topic '" + name + "' does not exist");
            }
            log.error("Failed to fetch topic {} on cluster {}", name, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to fetch topic {} on cluster {}", name, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    public List<ConfigEntry> getBrokerConfiguration( String clusterId, String name ) throws KafkaAdminApiException {
        List<ConfigEntry> result = new ArrayList<>();
        Locale locale = LocaleContextHolder.getLocale();

        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            DescribeConfigsResult describeConfigsResult = admin.describeConfigs(Collections.singleton(configResource));
            describeConfigsResult.all().get().forEach((resource, config) -> {
                config.entries().forEach(c -> {
                    String documentation = c.documentation();
                    if( documentation == null ){
                        documentation = messageSource.getMessage("broker.documentation." + c.name(), null, null , locale);
                    }
                    result.add(new ConfigEntry( c.name(), c.value(), c.source().name(), c.isSensitive(), c.isReadOnly(),
                            c.type().name(), documentation, ViewHelper.getDocumentationLink(c.name())));
                });
            });
            result.sort(Comparator.comparing(ConfigEntry::name));
            return result;
        } catch (ConnectionSessionExpiredException e){
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch configuration for topic {} on cluster {}", name, clusterId, e);
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
            log.error("Failed to update configuration for topic {} on cluster {}", topicId, clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }

    }

}
