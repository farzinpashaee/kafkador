package com.csl.kafkador.util.metrics;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Builds protobuf messages for tests. */
final class ProtoWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    static ProtoWriter message() {
        return new ProtoWriter();
    }

    ProtoWriter varint(int field, long value) {
        writeVarint((long) field << 3);
        writeVarint(value);
        return this;
    }

    ProtoWriter fixed64(int field, long value) {
        writeVarint((long) field << 3 | 1);
        out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
        return this;
    }

    ProtoWriter doubleValue(int field, double value) {
        return fixed64(field, Double.doubleToLongBits(value));
    }

    ProtoWriter string(int field, String value) {
        return bytes(field, value.getBytes(StandardCharsets.UTF_8));
    }

    ProtoWriter message(int field, ProtoWriter message) {
        return bytes(field, message.build());
    }

    ProtoWriter bytes(int field, byte[] value) {
        writeVarint((long) field << 3 | 2);
        writeVarint(value.length);
        out.writeBytes(value);
        return this;
    }

    byte[] build() {
        return out.toByteArray();
    }

    private void writeVarint(long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

}
