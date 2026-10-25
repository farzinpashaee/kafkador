package com.csl.kafkador.service.registry;

import com.csl.kafkador.domain.dto.SchemaRegistryConfigDto;
import com.csl.kafkador.domain.options.RecordDecoder;
import com.csl.kafkador.domain.options.RecordSerde;
import com.csl.kafkador.exception.InvalidRecordRequestException;
import com.csl.kafkador.service.ExternalServiceConnections;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import lombok.RequiredArgsConstructor;
import org.apache.avro.generic.GenericData;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decoders for the Records tab. Plain serdes decode by themselves; SCHEMA_REGISTRY reads Confluent's wire format
 * (magic byte, schema id, payload), looks the writer's schema up in the cluster's Schema Registry and shows the record
 * as JSON, whether it was written as Avro, Protobuf or JSON Schema. Registry clients are kept per URL so schemas are
 * fetched once, not per record.
 */
@Service
@RequiredArgsConstructor
public class SchemaRegistryRecordDecoders {

    /** Schemas each registry client keeps; far more than a topic's history of versions. */
    static final int SCHEMA_CACHE_SIZE = 1_000;

    private final SchemaRegistryService schemaRegistryService;
    private final ExternalServiceConnections connections;
    private final Map<String, SchemaRegistryClient> clients = new ConcurrentHashMap<>();

    /**
     * A decoder for one request on {@code topic}. Use it from one thread at a time.
     *
     * @throws InvalidRecordRequestException when Schema Registry decoding is asked for but no registry is set up
     */
    public RecordDecoder decoder(RecordSerde serde, String topic, String clusterId) throws InvalidRecordRequestException {
        if (serde != RecordSerde.SCHEMA_REGISTRY) return serde;
        SchemaRegistryConfigDto config = schemaRegistryService.getConfig(clusterId);
        if (!config.isConfigured() || config.getUrl() == null || config.getUrl().isBlank()) {
            throw new InvalidRecordRequestException(
                    "Schema Registry isn't set up for this cluster. Add its URL in Settings > Schema Registry to decode records with it.");
        }
        String url = config.getUrl();
        // Signs in and trusts the CA as set in Settings; a changed setting gets a client of its own.
        Map<String, Object> clientConfig = connections.schemaRegistryClientConfig(clusterId);
        SchemaRegistryClient client = clients.computeIfAbsent(url + "\u0000" + new TreeMap<>(clientConfig), key -> new CachedSchemaRegistryClient(
                List.of(url), SCHEMA_CACHE_SIZE, List.of(new AvroSchemaProvider(), new ProtobufSchemaProvider(), new JsonSchemaProvider()),
                clientConfig));
        return new WireFormatDecoder(client, url, topic);
    }

    /** Decodes Confluent wire-format bytes with the schema they name. Anything it can't decode is described instead. */
    static final class WireFormatDecoder implements RecordDecoder {

        private static final ObjectMapper JSON = new ObjectMapper();

        private final SchemaRegistryClient client;
        private final Map<String, Object> config;
        private final String topic;
        private KafkaAvroDeserializer avro;
        private KafkaProtobufDeserializer<Message> protobuf;
        private KafkaJsonSchemaDeserializer<Object> jsonSchema;

        WireFormatDecoder(SchemaRegistryClient client, String url, String topic) {
            this.client = client;
            this.config = Map.of("schema.registry.url", url);
            this.topic = topic;
        }

        @Override
        public String decode(byte[] data) {
            if (data == null) return null;
            if (!RecordSerde.isWireFormat(data)) return RecordSerde.notWireFormat(data);
            int id = RecordSerde.schemaId(data);
            try {
                ParsedSchema schema = client.getSchemaById(id);
                return switch (schema.schemaType()) {
                    case "AVRO" -> avroText(avro().deserialize(topic, data));
                    case "PROTOBUF" -> JsonFormat.printer().preservingProtoFieldNames().print(protobuf().deserialize(topic, data));
                    case "JSON" -> JSON.writeValueAsString(jsonSchema().deserialize(topic, data));
                    default -> "<schema " + id + " is " + schema.schemaType() + ", which Kafkador can't decode>";
                };
            } catch (Exception e) {
                RestClientException registryError = find(e, RestClientException.class);
                // 40403 is "schema not found"; any 404-family code means the registry doesn't know this id.
                if (registryError != null && registryError.getErrorCode() / 100 == 404) {
                    return "<schema " + id + " not found in Schema Registry>";
                }
                if (registryError != null) {
                    return "<Schema Registry error for schema " + id + ": " + registryError.getMessage() + ">";
                }
                return "<couldn't decode with schema " + id + ": " + rootMessage(e) + ">";
            }
        }

        /** Records, enums, arrays and maps as JSON; a plain string or number schema as its value. */
        static String avroText(Object value) {
            if (value == null) return "null";
            if (value instanceof CharSequence text) return text.toString();
            if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
            if (value instanceof ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.duplicate().get(bytes);
                return Base64.getEncoder().encodeToString(bytes);
            }
            return GenericData.get().toString(value);
        }

        private KafkaAvroDeserializer avro() {
            if (avro == null) avro = new KafkaAvroDeserializer(client, config);
            return avro;
        }

        private KafkaProtobufDeserializer<Message> protobuf() {
            if (protobuf == null) protobuf = new KafkaProtobufDeserializer<>(client, config);
            return protobuf;
        }

        private KafkaJsonSchemaDeserializer<Object> jsonSchema() {
            if (jsonSchema == null) jsonSchema = new KafkaJsonSchemaDeserializer<>(client, config);
            return jsonSchema;
        }

        private static <T extends Throwable> T find(Throwable e, Class<T> type) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (type.isInstance(t)) return type.cast(t);
            }
            return null;
        }

        private static String rootMessage(Throwable e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        }
    }

}
