package com.csl.kafkador.service;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.Topic;
import com.csl.kafkador.domain.dto.TopicOverviewDto;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.exception.KafkaAdminApiException;
import com.csl.kafkador.exception.InvalidTopicConfigException;
import com.csl.kafkador.exception.TopicAlreadyExistsException;
import com.csl.kafkador.exception.TopicNotFoundException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.util.DtoMapper;
import com.csl.kafkador.util.TopicOverviewCalculator;
import com.csl.kafkador.util.ViewHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service("TopicService")
@RequiredArgsConstructor
@Slf4j
public class TopicService {

    private static final long OVERVIEW_TIMEOUT_SECONDS = 15;

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

    /**
     * Every topic, internal ones included, with partition health, message counts and sizes for the Topics page.
     * Offsets, configs and log dirs are each optional: when one can't be read its columns are left empty.
     */
    public List<TopicOverviewDto> getTopicsOverview(String clusterId) throws KafkaAdminApiException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            Set<String> names = await(admin.listTopics(new ListTopicsOptions().listInternal(true)).names());
            if (names.isEmpty()) return List.of();
            Collection<TopicDescription> topics = await(admin.describeTopics(names).allTopicNames()).values();

            List<TopicPartition> partitions = topics.stream()
                    .flatMap(t -> t.partitions().stream().map(p -> new TopicPartition(t.name(), p.partition())))
                    .toList();
            Map<TopicPartition, Long> earliest = offsets(admin, partitions, OffsetSpec.earliest());
            Map<TopicPartition, Long> latest = offsets(admin, partitions, OffsetSpec.latest());

            return TopicOverviewCalculator.calculate(topics, earliest, latest, cleanupPolicies(admin, names), logDirs(admin));
        } catch (ConnectionSessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to build the topics overview for cluster {}", clusterId, e);
            throw new KafkaAdminApiException("Error initializing or using AdminClient: " + e.getMessage());
        }
    }

    /** Offsets per partition; a partition that can't be read (e.g. no leader) is simply left out. */
    private Map<TopicPartition, Long> offsets(Admin admin, List<TopicPartition> partitions, OffsetSpec spec) {
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        partitions.forEach(tp -> request.put(tp, spec));
        ListOffsetsResult result = admin.listOffsets(request);
        Map<TopicPartition, Long> offsets = new HashMap<>();
        for (TopicPartition tp : partitions) {
            try {
                offsets.put(tp, await(result.partitionResult(tp)).offset());
            } catch (Exception e) {
                log.debug("No {} offset for {}: {}", spec.getClass().getSimpleName(), tp, e.getMessage());
            }
        }
        return offsets;
    }

    private Map<String, String> cleanupPolicies(Admin admin, Set<String> names) {
        try {
            List<ConfigResource> resources = names.stream().map(n -> new ConfigResource(ConfigResource.Type.TOPIC, n)).toList();
            Map<String, String> policies = new HashMap<>();
            await(admin.describeConfigs(resources).all()).forEach((resource, config) -> {
                org.apache.kafka.clients.admin.ConfigEntry policy = config.get("cleanup.policy");
                if (policy != null) policies.put(resource.name(), policy.value());
            });
            return policies;
        } catch (Exception e) {
            log.warn("Could not read topic cleanup policies: {}", e.getMessage());
            return null;
        }
    }

    private Map<Integer, Map<String, LogDirDescription>> logDirs(Admin admin) {
        try {
            List<Integer> brokers = await(admin.describeCluster().nodes()).stream().map(org.apache.kafka.common.Node::id).toList();
            return await(admin.describeLogDirs(brokers).allDescriptions());
        } catch (Exception e) {
            log.warn("Could not describe broker log dirs, topic sizes will be unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static <T> T await(KafkaFuture<T> future) throws Exception {
        return future.get(OVERVIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public Topic createTopic( String clusterId , Topic topic ) throws KafkaAdminApiException, TopicAlreadyExistsException, InvalidTopicConfigException {
        return createTopic(clusterId, topic, Collections.emptyMap());
    }

    /**
     * Creates the topic with the given topic-level configs. A null partition count or
     * replication factor on {@code topic} falls back to the broker default.
     */
    public Topic createTopic( String clusterId , Topic topic, Map<String, String> configs ) throws KafkaAdminApiException, TopicAlreadyExistsException, InvalidTopicConfigException {
        try {
            Admin admin = connectionService.getAdminClient(clusterId).getAdmin();
            NewTopic newTopic = new NewTopic(topic.getName(),
                    Optional.ofNullable(topic.getPartitions()),
                    Optional.ofNullable(topic.getReplicatorFactor()));
            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }
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
            if (e.getCause() instanceof InvalidConfigurationException
                    || e.getCause() instanceof InvalidReplicationFactorException
                    || e.getCause() instanceof InvalidPartitionsException
                    || e.getCause() instanceof PolicyViolationException) {
                throw new InvalidTopicConfigException(e.getCause().getMessage());
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
