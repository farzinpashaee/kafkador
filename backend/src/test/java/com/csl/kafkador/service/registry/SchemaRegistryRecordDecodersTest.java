package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.options.RecordDecoder;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.service.registry.SchemaRegistryRecordDecoders.WireFormatDecoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaRegistryRecordDecodersTest {

    private static final String TOPIC = "orders";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> SERIALIZER_CONFIG = Map.of("schema.registry.url", "mock://kafkador", "auto.register.schemas", true);

    private final MockSchemaRegistryClient registry = new MockSchemaRegistryClient(
            List.of(new AvroSchemaProvider(), new ProtobufSchemaProvider(), new JsonSchemaProvider()));
    private final WireFormatDecoder decoder = new WireFormatDecoder(registry, "mock://kafkador", TOPIC);

    @Test
    void decodesAvroAsJson() throws Exception {
        Schema schema = new Schema.Parser().parse("""
                {"type":"record","name":"Order","fields":[{"name":"id","type":"int"},{"name":"item","type":["null","string"]}]}""");
        GenericRecord order = new GenericData.Record(schema);
        order.put("id", 7);
        order.put("item", "tea");
        byte[] bytes;
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(registry, SERIALIZER_CONFIG)) {
            bytes = serializer.serialize(TOPIC, order);
        }

        assertThat(json(decoder.decode(bytes))).isEqualTo(JSON.readTree("{\"id\":7,\"item\":\"tea\"}"));
    }

    @Test
    void showsAnAvroStringAsItsText() throws Exception {
        byte[] bytes;
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(registry, SERIALIZER_CONFIG)) {
            bytes = serializer.serialize(TOPIC, "plain text");
        }

        assertThat(decoder.decode(bytes)).isEqualTo("plain text");
    }

    @Test
    void decodesProtobufAsJson() throws Exception {
        ProtobufSchema schema = new ProtobufSchema("syntax = \"proto3\"; message Order { int32 id = 1; string item_name = 2; }");
        Descriptor descriptor = schema.toDescriptor();
        Message order = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("id"), 7)
                .setField(descriptor.findFieldByName("item_name"), "tea")
                .build();
        byte[] bytes;
        try (KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>(registry, SERIALIZER_CONFIG)) {
            bytes = serializer.serialize(TOPIC, order);
        }

        assertThat(json(decoder.decode(bytes))).isEqualTo(JSON.readTree("{\"id\":7,\"item_name\":\"tea\"}"));
    }

    @Test
    void decodesJsonSchemaData() throws Exception {
        int id = registry.register(TOPIC + "-value", new JsonSchema("""
                {"type":"object","properties":{"id":{"type":"integer"}}}"""));

        assertThat(json(decoder.decode(wire(id, "{\"id\":7}".getBytes(StandardCharsets.UTF_8)))))
                .isEqualTo(JSON.readTree("{\"id\":7}"));
    }

    @Test
    void describesWhatItCannotDecode() {
        assertThat(decoder.decode(null)).isNull();
        assertThat(decoder.decode("hello".getBytes(StandardCharsets.UTF_8))).isEqualTo("<not Schema Registry data: 5 bytes without the Confluent header>");
        assertThat(decoder.decode(wire(999, new byte[]{1, 2}))).isEqualTo("<schema 999 not found in Schema Registry>");
    }

    @Test
    void plainSerdesDecodeByThemselvesAndAMissingRegistryIsReported() throws Exception {
        SchemaRegistryService service = mock(SchemaRegistryService.class);
        when(service.getConfig("c1")).thenReturn(new SchemaRegistryConfigDto());
        SchemaRegistryRecordDecoders decoders = new SchemaRegistryRecordDecoders(service);

        RecordDecoder hex = decoders.decoder(RecordSerde.HEX, TOPIC, "c1");
        assertThat(hex.decode(new byte[]{(byte) 0xAB})).isEqualTo("ab");
        assertThatThrownBy(() -> decoders.decoder(RecordSerde.SCHEMA_REGISTRY, TOPIC, "c1"))
                .isInstanceOf(InvalidRecordRequestException.class)
                .hasMessageContaining("Settings > Schema Registry");
    }

    @Test
    void theSchemaRegistrySerdeOnItsOwnNamesTheSchemaAndCannotProduce() {
        assertThat(RecordSerde.SCHEMA_REGISTRY.decode(wire(12, new byte[]{1, 2, 3})))
                .isEqualTo("<Schema Registry data, schema id 12, 8 bytes>");
        assertThatThrownBy(() -> RecordSerde.SCHEMA_REGISTRY.encode("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isn't supported");
    }

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    private static byte[] wire(int schemaId, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(schemaId).array());
        out.writeBytes(payload);
        return out.toByteArray();
    }

}
