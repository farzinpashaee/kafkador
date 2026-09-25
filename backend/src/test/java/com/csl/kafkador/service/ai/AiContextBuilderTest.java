package com.csl.kafkador.service.ai;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.ConsumerGroup;
import com.csl.kafkador.domain.Partition;
import com.csl.kafkador.domain.Topic;
import com.csl.kafkador.domain.dto.BrokerDto;
import com.csl.kafkador.domain.dto.ClusterDto;
import com.csl.kafkador.domain.dto.CompatibilityConfigDto;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.domain.dto.ConnectorDto;
import com.csl.kafkador.domain.dto.ConnectorTaskDto;
import com.csl.kafkador.domain.dto.KafkaConnectConfigDto;
import com.csl.kafkador.domain.dto.KsqlDbConfigDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiContextBuilderTest {

    private static final String CLUSTER = "cluster-1";

    private ConnectionService connectionService;
    private ClusterService clusterService;
    private BrokerService brokerService;
    private TopicService topicService;
    private ConsumerService consumerService;
    private SchemaRegistryService schemaRegistryService;
    private KsqlDbService ksqlDbService;
    private KafkaConnectService kafkaConnectService;
    private AiContextBuilder builder;

    @BeforeEach
    void setUp() throws Exception {
        connectionService = mock(ConnectionService.class);
        clusterService = mock(ClusterService.class);
        brokerService = mock(BrokerService.class);
        topicService = mock(TopicService.class);
        consumerService = mock(ConsumerService.class);
        schemaRegistryService = mock(SchemaRegistryService.class);
        ksqlDbService = mock(KsqlDbService.class);
        kafkaConnectService = mock(KafkaConnectService.class);

        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean("ClusterService")).thenReturn(clusterService);
        when(context.getBean("BrokerService")).thenReturn(brokerService);
        when(context.getBean("TopicService")).thenReturn(topicService);
        when(context.getBean("ConsumerService")).thenReturn(consumerService);
        when(context.getBean("SchemaRegistryService")).thenReturn(schemaRegistryService);
        when(context.getBean("KsqlDbService")).thenReturn(ksqlDbService);
        when(context.getBean("KafkaConnectService")).thenReturn(kafkaConnectService);
        builder = new AiContextBuilder(context, new ApplicationConfig(), connectionService);

        when(connectionService.getActiveConnection())
                .thenReturn(new ConnectionDto().setName("local").setHost("kafka-1").setPort("9092").setClusterId(CLUSTER));
        when(connectionService.getConnections()).thenReturn(List.of(new ConnectionDto().setName("local").setHost("kafka-1").setPort("9092")));

        BrokerDto broker = new BrokerDto().setId("1").setHost("kafka-1").setPort(9092);
        when(clusterService.getClusterDetails(CLUSTER))
                .thenReturn(new ClusterDto().setClusterId("abc").setBrokers(List.of(broker)).setController(broker));
        when(brokerService.getConfigurations(CLUSTER, "1")).thenReturn(List.of(
                config("listeners", "PLAINTEXT://0.0.0.0:9092", false),
                config("advertised.listeners", "PLAINTEXT://kafka-1:9092", false),
                config("ssl.keystore.password", "hunter2", true),
                config("listener.security.protocol.map", "SECRET-VALUE-SENSITIVE", true),
                config("some.unlisted.setting", "should-not-appear", false)));

        BrokerDto node = new BrokerDto();
        Topic healthy = new Topic().setName("orders").setPartitions(1).setIsInternal(false).setPartitionDetails(
                List.of(new Partition().setReplicas(List.of(node, node)).setIsr(List.of(node, node))));
        Topic degraded = new Topic().setName("payments").setPartitions(1).setIsInternal(false).setPartitionDetails(
                List.of(new Partition().setReplicas(List.of(node, node)).setIsr(List.of(node))));
        when(topicService.getTopics(CLUSTER)).thenReturn(List.of(healthy, degraded));
        when(topicService.getTopic(CLUSTER, "orders")).thenReturn(new Topic().setName("orders").setPartitions(1).setConfig(List.of(
                new ConfigEntry("retention.ms", "60000", "DYNAMIC_TOPIC_CONFIG", false, false, "LONG", null, null),
                new ConfigEntry("segment.bytes", "1073741824", "DEFAULT_CONFIG", false, false, "LONG", null, null),
                new ConfigEntry("flush.messages", "1", "DEFAULT_CONFIG", false, false, "LONG", null, null),
                new ConfigEntry("secret.thing", "topic-secret", "DYNAMIC_TOPIC_CONFIG", true, false, "STRING", null, null))));

        when(consumerService.getConsumersGroup(CLUSTER)).thenReturn(List.of(
                new ConsumerGroup().setId("billing").setGroupState("STABLE").setType("CLASSIC").setCoordinator("kafka-1")));

        when(schemaRegistryService.getConfig(CLUSTER)).thenReturn(new SchemaRegistryConfigDto().setConfigured(true).setUrl("http://sr:8081"));
        when(schemaRegistryService.getSubjects(CLUSTER)).thenReturn(
                new SchemaRegistryDto().setConfigured(true).setSubjects(List.of(new SchemaDto().setName("orders-value"))));
        when(schemaRegistryService.getGlobalCompatibility(CLUSTER)).thenReturn(new CompatibilityConfigDto().setLevel("BACKWARD"));
        when(schemaRegistryService.getVersion("orders-value", "latest", CLUSTER)).thenReturn(
                new SchemaVersionDto().setSubject("orders-value").setVersion(3).setId(7).setSchemaType("AVRO").setSchema("{\"type\":\"record\"}"));

        when(ksqlDbService.getConfig(CLUSTER)).thenReturn(new KsqlDbConfigDto().setConfigured(false));
        when(ksqlDbService.getServerInfo(CLUSTER)).thenThrow(new ConfigNotFoundException("no ksql"));
        when(kafkaConnectService.getConfig(CLUSTER)).thenReturn(new KafkaConnectConfigDto().setConfigured(true).setUrl("http://connect:8083"));
        when(kafkaConnectService.getConnectors(CLUSTER)).thenReturn(List.of(new ConnectorDto().setName("jdbc-sink").setType("sink")
                .setState("FAILED").setTrace("org.apache.kafka.ConnectException: boom\n\tat some.stack.Frame")
                .setTasks(List.of(new ConnectorTaskDto().setId(0).setState("FAILED")))
                .setConfig(Map.of("connection.password", "db-password"))));
        when(kafkaConnectService.getPlugins(CLUSTER)).thenReturn(List.of());
    }

    @Test
    void snapshot_describesClusterTopicsGroupsSchemasAndSetup() {
        String context = builder.build(CLUSTER, "give me an overview");

        assertThat(context)
                .contains("Active connection: local (kafka-1:9092)")
                .contains("Schema Registry URL: http://sr:8081")
                .contains("ksqlDB URL: not configured")
                .contains("Kafka cluster id: abc")
                .contains("advertised.listeners=PLAINTEXT://kafka-1:9092")
                .contains("- orders | partitions=1 | replication-factor=2")
                .contains("- payments | partitions=1 | replication-factor=2 | UNDER-REPLICATED partitions=1")
                .contains("- billing | state=STABLE")
                .contains("Global compatibility: BACKWARD")
                .contains("- orders-value")
                .contains("- jdbc-sink | type=sink | state=FAILED | tasks running=0/1")
                .contains("failure: org.apache.kafka.ConnectException: boom");
    }

    @Test
    void snapshot_leavesOutSecretsAndUnlistedBrokerSettings() {
        String context = builder.build(CLUSTER, "give me an overview");

        assertThat(context)
                .doesNotContain("hunter2")
                .doesNotContain("SECRET-VALUE-SENSITIVE")
                .doesNotContain("should-not-appear")
                .doesNotContain("db-password")
                .doesNotContain("some.stack.Frame");
    }

    @Test
    void unconfiguredOrFailingServices_areReportedWithoutFailingTheWholeSnapshot() {
        String context = builder.build(CLUSTER, "overview");

        assertThat(context).contains("## ksqlDB\nNot configured in Kafkador.");
        assertThat(context).contains("## Topics");
    }

    @Test
    void mentionedTopicAndSubject_getExtraDetail_withoutSensitiveConfigs() {
        String context = builder.build(CLUSTER, "What is the retention of the ORDERS topic and its orders-value schema?");

        assertThat(context)
                .contains("Details for topic 'orders'")
                .contains("retention.ms=60000 (overridden)")
                .contains("segment.bytes=1073741824")
                .contains("Latest schema for subject 'orders-value'")
                .contains("Version 3, id 7, type AVRO")
                .contains("{\"type\":\"record\"}")
                .doesNotContain("flush.messages")
                .doesNotContain("topic-secret");
    }

    @Test
    void unmentionedTopics_areNotDrilledInto() throws Exception {
        builder.build(CLUSTER, "how many brokers do we have?");

        verify(topicService, never()).getTopic(anyString(), any());
        verify(schemaRegistryService, never()).getVersion(anyString(), anyString(), anyString());
    }

    @Test
    void builderOnlyUsesReadOperations() throws Exception {
        builder.build(CLUSTER, "What is the retention of the orders topic?");

        verify(topicService, never()).createTopic(any(), any());
        verify(topicService, never()).deleteTopic(any(), any());
        verify(topicService, never()).updateConfig(any(), any(), any());
        verify(brokerService, never()).updateConfig(any(), any(), any());
        verify(kafkaConnectService, never()).deleteConnector(any(), any());
        verify(kafkaConnectService, never()).createConnector(any(), any());
        verify(ksqlDbService, never()).terminateQuery(any(), any());
    }

    private static ConfigEntry config(String name, String value, boolean sensitive) {
        return new ConfigEntry(name, value, "STATIC_BROKER_CONFIG", sensitive, true, "STRING", null, null);
    }

}
