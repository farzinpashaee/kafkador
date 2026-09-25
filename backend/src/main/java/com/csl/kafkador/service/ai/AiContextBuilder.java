package com.csl.kafkador.service.ai;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.ConsumerGroup;
import com.csl.kafkador.domain.Partition;
import com.csl.kafkador.domain.Topic;
import com.csl.kafkador.domain.dto.BrokerDto;
import com.csl.kafkador.domain.dto.ClusterDto;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.domain.dto.ConnectorDto;
import com.csl.kafkador.domain.dto.ConnectorPluginDto;
import com.csl.kafkador.domain.dto.KafkaConnectConfigDto;
import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
import com.csl.kafkador.domain.dto.KsqlQueryDto;
import com.csl.kafkador.domain.dto.KsqlServerInfoDto;
import com.csl.kafkador.domain.dto.KsqlStreamDto;
import com.csl.kafkador.domain.dto.KsqlTableDto;
import com.csl.kafkador.domain.dto.SchemaDto;
import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.dto.SchemaRegistryDto;
import com.csl.kafkador.domain.dto.SchemaVersionDto;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.record.ConfigEntry;
import com.csl.kafkador.service.BrokerService;
import com.csl.kafkador.service.ClusterService;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ConsumerService;
import com.csl.kafkador.service.TopicService;
import com.csl.kafkador.service.connect.KafkaConnectService;
import com.csl.kafkador.service.ksqldb.KsqlDbService;
import com.csl.kafkador.service.registry.SchemaRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds the plain-text description of the connected environment that is handed to the LLM.
 * Strictly read-only: it only calls getters on the existing services, and it deliberately leaves
 * out secrets (sensitive broker/topic configs, connector configs, API keys).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContextBuilder {

    private static final long CACHE_TTL_MS = 20_000;
    private static final int MAX_BROKERS_WITH_CONFIG = 10;
    private static final int MAX_TOPICS = 200;
    private static final int MAX_GROUPS = 100;
    private static final int MAX_SUBJECTS = 200;
    private static final int MAX_KSQL_ITEMS = 100;
    private static final int MAX_CONNECTORS = 100;
    private static final int MAX_PLUGINS = 50;
    private static final int MAX_MENTIONS = 5;
    private static final int MIN_MENTION_LENGTH = 3;
    private static final int MAX_SCHEMA_CHARS = 4000;
    private static final int MAX_TRACE_CHARS = 200;

    private static final Set<String> BROKER_CONFIG_KEYS = Set.of(
            "listeners", "advertised.listeners", "listener.security.protocol.map", "inter.broker.listener.name",
            "controller.listener.names", "security.inter.broker.protocol", "process.roles", "node.id",
            "controller.quorum.voters", "authorizer.class.name", "num.partitions", "default.replication.factor",
            "min.insync.replicas", "offsets.topic.replication.factor", "auto.create.topics.enable",
            "delete.topic.enable", "unclean.leader.election.enable", "log.retention.hours", "log.retention.bytes",
            "log.segment.bytes", "message.max.bytes", "log.dirs", "num.network.threads", "num.io.threads");

    private static final Set<String> KEY_TOPIC_CONFIGS = Set.of(
            "cleanup.policy", "retention.ms", "retention.bytes", "min.insync.replicas", "max.message.bytes",
            "segment.bytes", "compression.type", "unclean.leader.election.enable");

    private final ApplicationContext applicationContext;
    private final ApplicationConfig applicationConfig;
    private final ConnectionService connectionService;

    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    private record Snapshot(long createdAt, String text, List<String> topicNames, List<String> subjectNames) {}

    public String build(String clusterId, String latestUserMessage) {
        Snapshot snapshot = cache.get(clusterId);
        if (snapshot == null || System.currentTimeMillis() - snapshot.createdAt() > CACHE_TTL_MS) {
            snapshot = buildSnapshot(clusterId);
            cache.put(clusterId, snapshot);
        }
        String mentioned = buildMentionedDetails(clusterId, latestUserMessage, snapshot);
        return mentioned.isEmpty() ? snapshot.text() : snapshot.text() + "\n" + mentioned;
    }

    private Snapshot buildSnapshot(String clusterId) {
        List<String> topicNames = new ArrayList<>();
        List<String> subjectNames = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        text.append(section("Kafkador setup", () -> setup(clusterId)));
        text.append(section("Cluster and brokers", () -> cluster(clusterId)));
        text.append(section("Topics", () -> topics(clusterId, topicNames)));
        text.append(section("Consumer groups", () -> consumerGroups(clusterId)));
        text.append(section("Schema Registry", () -> schemaRegistry(clusterId, subjectNames)));
        text.append(section("ksqlDB", () -> ksqlDb(clusterId)));
        text.append(section("Kafka Connect", () -> kafkaConnect(clusterId)));
        return new Snapshot(System.currentTimeMillis(), text.toString(), topicNames, subjectNames);
    }

    private String section(String title, Callable<String> body) {
        try {
            return "## " + title + "\n" + body.call() + "\n";
        } catch (ConfigNotFoundException e) {
            return "## " + title + "\nNot configured in Kafkador.\n";
        } catch (Exception e) {
            log.debug("AI context section '{}' unavailable: {}", title, e.getMessage());
            return "## " + title + "\nCurrently unavailable (the service could not be reached).\n";
        }
    }

    private String setup(String clusterId) {
        StringBuilder sb = new StringBuilder();
        ConnectionDto active = connectionService.getActiveConnection();
        sb.append("Active connection: ").append(active.getName()).append(" (")
                .append(active.getHost()).append(':').append(active.getPort()).append("), cluster id ")
                .append(clusterId).append('\n');
        List<ConnectionDto> connections = connectionService.getConnections();
        if (connections != null && !connections.isEmpty()) {
            sb.append("Saved connections: ").append(connections.stream()
                    .map(c -> c.getName() + " (" + c.getHost() + ':' + c.getPort() + ')')
                    .collect(Collectors.joining(", "))).append('\n');
        }
        SchemaRegistryConfigDto registry = schemaRegistryService().getConfig(clusterId);
        sb.append("Schema Registry URL: ").append(registry.isConfigured() ? registry.getUrl() : "not configured").append('\n');
        KsqlDbConfigDto ksql = ksqlDbService().getConfig(clusterId);
        sb.append("ksqlDB URL: ").append(ksql.isConfigured() ? ksql.getUrl() : "not configured").append('\n');
        KafkaConnectConfigDto connect = kafkaConnectService().getConfig(clusterId);
        sb.append("Kafka Connect URL: ").append(connect.isConfigured() ? connect.getUrl() : "not configured").append('\n');
        return sb.toString();
    }

    private String cluster(String clusterId) throws Exception {
        ClusterService clusterService = (ClusterService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CLUSTER));
        BrokerService brokerService = (BrokerService) applicationContext
                .getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.BROKER));
        ClusterDto cluster = clusterService.getClusterDetails(clusterId);
        StringBuilder sb = new StringBuilder();
        sb.append("Kafka cluster id: ").append(cluster.getClusterId()).append('\n');
        Collection<BrokerDto> brokers = cluster.getBrokers() == null ? List.of() : cluster.getBrokers();
        sb.append("Brokers (").append(brokers.size()).append("):\n");
        if (cluster.getController() != null) {
            sb.append("Controller: broker ").append(cluster.getController().getId()).append('\n');
        }
        int detailed = 0;
        for (BrokerDto broker : brokers) {
            sb.append("- broker ").append(broker.getId()).append(" at ").append(broker.getHost()).append(':')
                    .append(broker.getPort());
            if (broker.getRack() != null) sb.append(", rack ").append(broker.getRack());
            if (broker.getSize() != null) sb.append(", log size ~").append(broker.getSize()).append(" KB");
            sb.append('\n');
            if (detailed++ < MAX_BROKERS_WITH_CONFIG) {
                try {
                    for (ConfigEntry entry : brokerService.getConfigurations(clusterId, broker.getId())) {
                        if (BROKER_CONFIG_KEYS.contains(entry.name()) && !Boolean.TRUE.equals(entry.sensitive())
                                && entry.value() != null && !entry.value().isBlank()) {
                            sb.append("    ").append(entry.name()).append('=').append(entry.value()).append('\n');
                        }
                    }
                } catch (Exception e) {
                    log.debug("AI context: broker {} config unavailable: {}", broker.getId(), e.getMessage());
                    sb.append("    (configuration unavailable)\n");
                }
            }
        }
        return sb.toString();
    }

    private String topics(String clusterId, List<String> topicNames) throws Exception {
        Collection<Topic> topics = topicService().getTopics(clusterId);
        List<Topic> sorted = topics.stream().sorted(Comparator.comparing(Topic::getName)).toList();
        sorted.forEach(t -> topicNames.add(t.getName()));
        StringBuilder sb = new StringBuilder("Total topics: ").append(sorted.size()).append('\n');
        for (Topic topic : sorted.stream().limit(MAX_TOPICS).toList()) {
            List<Partition> partitions = topic.getPartitionDetails() == null ? List.of() : topic.getPartitionDetails();
            int replicationFactor = partitions.isEmpty() || partitions.get(0).getReplicas() == null
                    ? 0 : partitions.get(0).getReplicas().size();
            long underReplicated = partitions.stream()
                    .filter(p -> p.getReplicas() != null && p.getIsr() != null && p.getIsr().size() < p.getReplicas().size())
                    .count();
            sb.append("- ").append(topic.getName()).append(" | partitions=").append(topic.getPartitions())
                    .append(" | replication-factor=").append(replicationFactor);
            if (underReplicated > 0) sb.append(" | UNDER-REPLICATED partitions=").append(underReplicated);
            if (Boolean.TRUE.equals(topic.getIsInternal())) sb.append(" | internal");
            sb.append('\n');
        }
        appendOverflow(sb, sorted.size(), MAX_TOPICS);
        return sb.toString();
    }

    private String consumerGroups(String clusterId) throws Exception {
        Collection<ConsumerGroup> groups = consumerService().getConsumersGroup(clusterId);
        StringBuilder sb = new StringBuilder("Total consumer groups: ").append(groups.size()).append('\n');
        groups.stream().sorted(Comparator.comparing(ConsumerGroup::getId)).limit(MAX_GROUPS).forEach(g ->
                sb.append("- ").append(g.getId()).append(" | state=").append(g.getGroupState())
                        .append(" | type=").append(g.getType()).append(" | coordinator=").append(g.getCoordinator()).append('\n'));
        appendOverflow(sb, groups.size(), MAX_GROUPS);
        return sb.toString();
    }

    private String schemaRegistry(String clusterId, List<String> subjectNames) throws Exception {
        SchemaRegistryService service = schemaRegistryService();
        SchemaRegistryDto registry = service.getSubjects(clusterId);
        if (!registry.isConfigured()) throw new ConfigNotFoundException("Schema Registry not configured");
        List<SchemaDto> subjects = registry.getSubjects() == null ? List.of() : registry.getSubjects();
        subjects.forEach(s -> subjectNames.add(s.getName()));
        StringBuilder sb = new StringBuilder("Total subjects: ").append(subjects.size()).append('\n');
        try {
            sb.append("Global compatibility: ").append(service.getGlobalCompatibility(clusterId).getLevel()).append('\n');
        } catch (Exception e) {
            log.debug("AI context: global compatibility unavailable: {}", e.getMessage());
        }
        subjects.stream().limit(MAX_SUBJECTS).forEach(s -> sb.append("- ").append(s.getName()).append('\n'));
        appendOverflow(sb, subjects.size(), MAX_SUBJECTS);
        return sb.toString();
    }

    private String ksqlDb(String clusterId) throws Exception {
        KsqlDbService service = ksqlDbService();
        StringBuilder sb = new StringBuilder();
        KsqlServerInfoDto info = service.getServerInfo(clusterId);
        sb.append("Server: version ").append(info.getVersion()).append(", status ").append(info.getServerStatus())
                .append(", service id ").append(info.getKsqlServiceId()).append('\n');
        List<KsqlStreamDto> streams = service.getStreams(clusterId);
        sb.append("Streams (").append(streams.size()).append("):\n");
        streams.stream().limit(MAX_KSQL_ITEMS).forEach(s -> sb.append("- ").append(s.getName()).append(" -> topic ")
                .append(s.getTopic()).append(" (").append(s.getKeyFormat()).append('/').append(s.getValueFormat()).append(")\n"));
        List<KsqlTableDto> tables = service.getTables(clusterId);
        sb.append("Tables (").append(tables.size()).append("):\n");
        tables.stream().limit(MAX_KSQL_ITEMS).forEach(t -> sb.append("- ").append(t.getName()).append(" -> topic ")
                .append(t.getTopic()).append(" (").append(t.getKeyFormat()).append('/').append(t.getValueFormat()).append(")\n"));
        List<KsqlQueryDto> queries = service.getQueries(clusterId);
        sb.append("Persistent queries (").append(queries.size()).append("):\n");
        queries.stream().limit(MAX_KSQL_ITEMS).forEach(q -> sb.append("- ").append(q.getId()).append(" | ").append(q.getStatus())
                .append(" | sources=").append(q.getSources()).append(" | sinks=").append(q.getSinks()).append('\n'));
        return sb.toString();
    }

    private String kafkaConnect(String clusterId) throws Exception {
        KafkaConnectService service = kafkaConnectService();
        StringBuilder sb = new StringBuilder();
        List<ConnectorDto> connectors = service.getConnectors(clusterId);
        sb.append("Connectors (").append(connectors.size()).append("):\n");
        for (ConnectorDto connector : connectors.stream().limit(MAX_CONNECTORS).toList()) {
            int tasks = connector.getTasks() == null ? 0 : connector.getTasks().size();
            long running = connector.getTasks() == null ? 0
                    : connector.getTasks().stream().filter(t -> "RUNNING".equals(t.getState())).count();
            sb.append("- ").append(connector.getName()).append(" | type=").append(connector.getType())
                    .append(" | state=").append(connector.getState()).append(" | tasks running=")
                    .append(running).append('/').append(tasks).append('\n');
            if (connector.getTrace() != null && !connector.getTrace().isBlank()) {
                sb.append("    failure: ").append(firstLine(connector.getTrace(), MAX_TRACE_CHARS)).append('\n');
            }
        }
        List<ConnectorPluginDto> plugins = service.getPlugins(clusterId);
        sb.append("Installed plugins (").append(plugins.size()).append("): ")
                .append(plugins.stream().limit(MAX_PLUGINS).map(p -> p.getClassName() + " [" + p.getType() + ']')
                        .collect(Collectors.joining(", "))).append('\n');
        return sb.toString();
    }

    /** Extra detail for topics / schema subjects the user actually named in their latest question. */
    private String buildMentionedDetails(String clusterId, String message, Snapshot snapshot) {
        if (message == null || message.isBlank()) return "";
        String haystack = message.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        List<String> topics = mentioned(snapshot.topicNames(), haystack);
        for (String name : topics) {
            sb.append(section("Details for topic '" + name + "'", () -> topicDetails(clusterId, name)));
        }
        List<String> subjects = mentioned(snapshot.subjectNames(), haystack);
        for (String name : subjects) {
            sb.append(section("Latest schema for subject '" + name + "'", () -> subjectDetails(clusterId, name)));
        }
        return sb.toString();
    }

    private static List<String> mentioned(List<String> names, String haystackLowerCase) {
        return names.stream()
                .filter(n -> n.length() >= MIN_MENTION_LENGTH && haystackLowerCase.contains(n.toLowerCase(Locale.ROOT)))
                .limit(MAX_MENTIONS)
                .toList();
    }

    private String topicDetails(String clusterId, String name) throws Exception {
        Topic topic = topicService().getTopic(clusterId, name);
        StringBuilder sb = new StringBuilder("Partitions: ").append(topic.getPartitions()).append('\n');
        if (topic.getConfig() != null) {
            for (ConfigEntry entry : topic.getConfig()) {
                boolean overridden = "DYNAMIC_TOPIC_CONFIG".equals(entry.source());
                if ((overridden || KEY_TOPIC_CONFIGS.contains(entry.name())) && !Boolean.TRUE.equals(entry.sensitive())
                        && entry.value() != null) {
                    sb.append(entry.name()).append('=').append(entry.value())
                            .append(overridden ? " (overridden)" : "").append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String subjectDetails(String clusterId, String subject) throws Exception {
        SchemaVersionDto version = schemaRegistryService().getVersion(subject, "latest", clusterId);
        String schema = version.getSchema() == null ? "" : version.getSchema();
        if (schema.length() > MAX_SCHEMA_CHARS) schema = schema.substring(0, MAX_SCHEMA_CHARS) + "... (truncated)";
        return "Version " + version.getVersion() + ", id " + version.getId() + ", type " + version.getSchemaType() + '\n' + schema + '\n';
    }

    private static void appendOverflow(StringBuilder sb, int total, int max) {
        if (total > max) sb.append("(").append(total - max).append(" more not shown)\n");
    }

    private static String firstLine(String text, int max) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > max ? line.substring(0, max) + "..." : line;
    }

    private TopicService topicService() {
        return (TopicService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.TOPIC));
    }

    private ConsumerService consumerService() {
        return (ConsumerService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.CONSUMER));
    }

    private SchemaRegistryService schemaRegistryService() {
        return (SchemaRegistryService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.SCHEMA_REGISTRY));
    }

    private KsqlDbService ksqlDbService() {
        return (KsqlDbService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KSQL_DB));
    }

    private KafkaConnectService kafkaConnectService() {
        return (KafkaConnectService) applicationContext.getBean(applicationConfig.getServiceImplementation(KafkadorContext.Service.KAFKA_CONNECT));
    }

}
