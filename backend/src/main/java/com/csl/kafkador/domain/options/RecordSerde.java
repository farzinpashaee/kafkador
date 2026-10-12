package com.csl.kafkador.domain.options;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/** How the raw bytes of a record key or value are shown. Numeric serdes match Kafka's own (big-endian) serializers. */
public enum RecordSerde {
    STRING,
    JSON,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BASE64,
    HEX;

    /** The bytes as text, or null for a null key/value. Bytes that don't fit the serde are described instead of failing. */
    public String decode(byte[] data) {
        if (data == null) return null;
        return switch (this) {
            case STRING, JSON -> new String(data, StandardCharsets.UTF_8);
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

    private static String mismatch(byte[] data, int expected) {
        return "<" + data.length + " bytes, not a " + expected + "-byte number>";
    }
}
