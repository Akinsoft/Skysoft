package com.skysoft.features.waypoints.codec;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class WaypointBytes {
    private final byte[] data;
    private int offset;

    WaypointBytes(byte[] data) { this.data = data; }

    int remaining() { return data.length - offset; }

    int u8() {
        require(remaining() > 0, "Truncated waypoint data.");
        return data[offset++] & 255;
    }

    long unsigned(int bits) {
        long value = 0;
        for (int shift = 0; shift < bits; shift += 7) {
            int next = u8();
            int available = Math.min(7, bits - shift);
            require((next & 127) < (1 << available), "Waypoint integer overflow.");
            value |= (long) (next & 127) << shift;
            if (next < 128) {
                require(shift == 0 || next != 0, "Noncanonical waypoint integer.");
                return value;
            }
        }
        throw new IllegalArgumentException("Waypoint integer is too long.");
    }

    int count(int max) {
        long value = unsigned(32);
        require(value <= max, "Waypoint count or length exceeds its limit.");
        return (int) value;
    }

    long signed(int bits) {
        long value = unsigned(bits);
        return (value >>> 1) ^ -(value & 1);
    }

    double number() { return ByteBuffer.wrap(bytes(8)).getDouble(); }
    int rgb() { return (u8() << 16) | (u8() << 8) | u8(); }
    int u16() { return (u8() << 8) | u8(); }

    byte[] bytes(int length) {
        require(length >= 0 && length <= remaining(), "Truncated waypoint data.");
        byte[] value = Arrays.copyOfRange(data, offset, offset + length);
        offset += length;
        return value;
    }

    WaypointBytes section(int max) { return new WaypointBytes(bytes(count(max))); }

    String text() {
        byte[] value = bytes(count(1_048_576));
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Waypoint text is not valid UTF-8.", failure);
        }
    }

    void end() { require(remaining() == 0, "Waypoint data contains trailing bytes."); }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    static final class Bits {
        private final WaypointBytes input;
        private final boolean leastFirst;
        private int buffer;
        private int remaining;

        Bits(WaypointBytes input, boolean leastFirst) {
            this.input = input;
            this.leastFirst = leastFirst;
        }

        long read(int count) {
            require(count >= 0 && count <= 32, "Invalid waypoint bit width.");
            long value = 0;
            for (int index = 0; index < count; index++) {
                if (remaining == 0) { buffer = input.u8(); remaining = 8; }
                int bit;
                if (leastFirst) { bit = buffer & 1; buffer >>>= 1; }
                else { bit = (buffer >>> (remaining - 1)) & 1; buffer &= (1 << (remaining - 1)) - 1; }
                remaining--;
                if (leastFirst) value |= (long) bit << index;
                else value = (value << 1) | bit;
            }
            return value;
        }

        void end() { require(buffer == 0, "Nonzero waypoint bit padding."); }
    }
}
