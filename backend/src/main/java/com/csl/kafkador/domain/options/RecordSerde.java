package com.csl.kafkador.domain.options;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * How the raw bytes of a record key or value are shown. Numeric serdes match Kafka's own (big-endian) serializers.
 * SCHEMA_REGISTRY is Confluent's wire format (Avro, Protobuf or JSON Schema), decoded by
 * {@code SchemaRegistryRecordDecoders} with the cluster's registry; it can't be produced.
 */
public enum RecordSerde implements RecordDecoder {
    STRING,
    JSON,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BASE64,
    HEX,
    SCHEMA_REGISTRY;

    /** Confluent's wire format: a 0 magic byte, then the 4-byte schema id, then the payload. */
    public static final int WIRE_HEADER_LENGTH = 5;

    /** The bytes as text, or null for a null key/value. Bytes that don't fit the serde are described instead of failing. */
    @Override
    public String decode(byte[] data) {
        if (data == null) return null;
        return switch (this) {
            case STRING, JSON -> new String(data, StandardCharsets.UTF_8);
            // Without a registry at hand, say what the bytes are rather than show them garbled.
            case SCHEMA_REGISTRY -> isWireFormat(data)
                    ? "<Schema Registry data, schema id " + schemaId(data) + ", " + data.length + " bytes>"
                    : notWireFormat(data);
            case INT -> data.length == Integer.BYTES ? String.valueOf(ByteBuffer.wrap(data).getInt()) : mismatch(data, Integer.BYTES);
            case LONG -> data.length == Long.BYTES ? String.valueOf(ByteBuffer.wrap(data).getLong()) : mismatch(data, Long.BYTES);
            case FLOAT -> data.length == Float.BYTES ? String.valueOf(ByteBuffer.wrap(data).getFloat()) : mismatch(data, Float.BYTES);
            case DOUBLE -> data.length == Double.BYTES ? String.valueOf(ByteBuffer.wrap(data).getDouble()) : mismatch(data, Double.BYTES);
            case BASE64 -> Base64.getEncoder().encodeToString(data);
            case HEX -> HexFormat.of().formatHex(data);
        };
    }

    /**
     * The bytes to produce for {@code text}, or null for null. JSON is checked to be valid and sent as typed;
     * numbers use Kafka's big-endian layout; BASE64 / HEX ignore whitespace (and a leading 0x for hex).
     *
     * @throws IllegalArgumentException when the text isn't valid for this serde
     */
    public byte[] encode(String text) {
        if (text == null) return null;
        if (this == SCHEMA_REGISTRY) {
            throw new IllegalArgumentException("Producing with Schema Registry isn't supported yet; pick another format");
        }
        try {
            return switch (this) {
                case STRING -> text.getBytes(StandardCharsets.UTF_8);
                case JSON -> {
                    JSON_MAPPER.readTree(text);
                    yield text.getBytes(StandardCharsets.UTF_8);
                }
                case INT -> ByteBuffer.allocate(Integer.BYTES).putInt(Integer.parseInt(text.trim())).array();
                case LONG -> ByteBuffer.allocate(Long.BYTES).putLong(Long.parseLong(text.trim())).array();
                case FLOAT -> ByteBuffer.allocate(Float.BYTES).putFloat(Float.parseFloat(text.trim())).array();
                case DOUBLE -> ByteBuffer.allocate(Double.BYTES).putDouble(Double.parseDouble(text.trim())).array();
                case BASE64 -> Base64.getDecoder().decode(text.replaceAll("\\s", ""));
                case HEX -> HexFormat.of().parseHex(text.replaceAll("\\s", "").replaceFirst("^0[xX]", ""));
                case SCHEMA_REGISTRY -> throw new IllegalStateException("checked above");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + abbreviate(text) + "' is not a valid " + label());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not valid " + label() + " data");
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private String label() {
        return switch (this) {
            case BASE64 -> "Base64";
            case HEX -> "hex";
            case JSON -> "JSON";
            default -> name().toLowerCase();
        };
    }

    private static String abbreviate(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40) + "…";
    }

    public static boolean isWireFormat(byte[] data) {
        return data.length >= WIRE_HEADER_LENGTH && data[0] == 0;
    }

    public static int schemaId(byte[] data) {
        return ByteBuffer.wrap(data, 1, Integer.BYTES).getInt();
    }

    public static String notWireFormat(byte[] data) {
        return "<not Schema Registry data: " + data.length + " bytes without the Confluent header>";
    }

    private static String mismatch(byte[] data, int expected) {
        return "<" + data.length + " bytes, not a " + expected + "-byte number>";
    }
}
