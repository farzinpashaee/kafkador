package com.csl.kafkador.util.metrics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * A protobuf message read off the wire without its schema. Fields keep their number and wire type; nested
 * messages and strings are both length-delimited, so callers decide which one a field holds.
 */
public final class ProtoMessage {

    static final int VARINT = 0;
    static final int FIXED64 = 1;
    static final int LENGTH_DELIMITED = 2;
    static final int FIXED32 = 5;

    public record Field(int number, int wireType, long numeric, byte[] bytes) {}

    private final List<Field> fields;

    private ProtoMessage(List<Field> fields) {
        this.fields = fields;
    }

    /** Parses a message; throws {@link IllegalArgumentException} when the bytes aren't a valid protobuf message. */
    public static ProtoMessage parse(byte[] data) {
        List<Field> fields = new ArrayList<>();
        int[] position = {0};
        while (position[0] < data.length) {
            long key = readVarint(data, position);
            int number = (int) (key >>> 3);
            int wireType = (int) (key & 7);
            if (number <= 0 || key >>> 3 > 0x1FFFFFFFL) throw new IllegalArgumentException("Invalid field number");
            switch (wireType) {
                case VARINT -> fields.add(new Field(number, wireType, readVarint(data, position), null));
                case FIXED64 -> fields.add(new Field(number, wireType, readFixed(data, position, 8), null));
                case FIXED32 -> fields.add(new Field(number, wireType, readFixed(data, position, 4), null));
                case LENGTH_DELIMITED -> {
                    long length = readVarint(data, position);
                    if (length < 0 || length > data.length - position[0]) throw new IllegalArgumentException("Truncated field");
                    byte[] bytes = new byte[(int) length];
                    System.arraycopy(data, position[0], bytes, 0, bytes.length);
                    position[0] += bytes.length;
                    fields.add(new Field(number, wireType, 0, bytes));
                }
                default -> throw new IllegalArgumentException("Unsupported wire type " + wireType);
            }
        }
        return new ProtoMessage(fields);
    }

    public static Optional<ProtoMessage> tryParse(byte[] data) {
        if (data == null) return Optional.empty();
        try {
            return Optional.of(parse(data));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<Field> fields() {
        return fields;
    }

    public boolean has(int number) {
        return fields.stream().anyMatch(f -> f.number() == number);
    }

    public List<byte[]> allBytes(int number) {
        return fields.stream().filter(f -> f.number() == number && f.wireType() == LENGTH_DELIMITED).map(Field::bytes).toList();
    }

    /** The field as text, when it's length-delimited and holds printable UTF-8. */
    public Optional<String> string(int number) {
        List<byte[]> all = allBytes(number);
        return all.isEmpty() ? Optional.empty() : text(all.get(all.size() - 1));
    }

    public Optional<ProtoMessage> message(int number) {
        List<byte[]> all = allBytes(number);
        return all.isEmpty() ? Optional.empty() : tryParse(all.get(all.size() - 1));
    }

    /** Every occurrence of a repeated message field that parses as a message. */
    public List<ProtoMessage> messages(int number) {
        return allBytes(number).stream().map(ProtoMessage::tryParse).flatMap(Optional::stream).toList();
    }

    public OptionalLong varint(int number) {
        return last(number, VARINT).map(f -> OptionalLong.of(f.numeric())).orElse(OptionalLong.empty());
    }

    public OptionalLong fixed64(int number) {
        return last(number, FIXED64).map(f -> OptionalLong.of(f.numeric())).orElse(OptionalLong.empty());
    }

    /** A {@code double} field (fixed64 on the wire). */
    public OptionalDouble doubleValue(int number) {
        return last(number, FIXED64).map(f -> OptionalDouble.of(Double.longBitsToDouble(f.numeric()))).orElse(OptionalDouble.empty());
    }

    private Optional<Field> last(int number, int wireType) {
        Field found = null;
        for (Field field : fields) {
            if (field.number() == number && field.wireType() == wireType) found = field;
        }
        return Optional.ofNullable(found);
    }

    /** Decodes bytes as UTF-8 text, rejecting invalid encodings and control characters other than tab and line breaks. */
    public static Optional<String> text(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') return Optional.empty();
            }
            return Optional.of(value);
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static long readVarint(byte[] data, int[] position) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (position[0] >= data.length) throw new IllegalArgumentException("Truncated varint");
            byte b = data[position[0]++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
        }
        throw new IllegalArgumentException("Varint too long");
    }

    private static long readFixed(byte[] data, int[] position, int size) {
        if (data.length - position[0] < size) throw new IllegalArgumentException("Truncated fixed field");
        ByteBuffer buffer = ByteBuffer.wrap(data, position[0], size).order(ByteOrder.LITTLE_ENDIAN);
        position[0] += size;
        return size == 8 ? buffer.getLong() : buffer.getInt() & 0xFFFFFFFFL;
    }

}
