package com.skysoft.features.waypoints.codec;

import java.io.ByteArrayOutputStream;

final class WaypointerText {
    private WaypointerText() {}

    private static String alphabet() {
        StringBuilder result = new StringBuilder();
        for (char c = '!'; c <= '~'; c++) {
            if (c == '.' || c == '`' || c == ',') continue;
            result.append(c);
        }
        return result.toString();
    }

    static byte[] stream(String text) {
        String alphabet = alphabet();
        int radix = alphabet.length();
        int threshold = radix * radix - 8193;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long buffer = 0;
        int bits = 0;
        int first = -1;
        for (int i = 0; i < text.length(); i++) {
            int digit = alphabet.indexOf(text.charAt(i));
            WaypointBytes.require(digit >= 0, "Invalid Waypointer share character.");
            if (first < 0) { first = digit; continue; }
            int value = first + digit * radix;
            buffer |= (long) value << bits;
            bits += (value & 8191) > threshold ? 13 : 14;
            while (bits >= 8) {
                output.write((int) buffer & 255);
                buffer >>>= 8;
                bits -= 8;
            }
            first = -1;
        }
        if (first >= 0) output.write((int) (buffer | ((long) first << bits)) & 255);
        return output.toByteArray();
    }

    static String encode(byte[] bytes) {
        String alphabet = alphabet();
        StringBuilder output = new StringBuilder();
        long buffer = 0;
        int bits = 0;
        for (byte next : bytes) {
            buffer |= (long) (next & 255) << bits;
            bits += 8;
            if (bits > 13) {
                int value = (int) buffer & 8191;
                int size = value > 88 ? 13 : 14;
                value = (int) buffer & ((1 << size) - 1);
                buffer >>>= size;
                bits -= size;
                output.append(alphabet.charAt(value % 91)).append(alphabet.charAt(value / 91));
            }
        }
        if (bits > 0) {
            output.append(alphabet.charAt((int) buffer % 91));
            if (bits > 7 || buffer >= 91) output.append(alphabet.charAt((int) buffer / 91));
        }
        return output.toString();
    }

    static String contextual(String text, boolean encode) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            output.append(current);
            char partner = current == '<' ? '3' : current == 'o' ? '/' : '\0';
            if (partner == '\0' || i + 1 >= text.length()) continue;
            char next = text.charAt(i + 1);
            if (encode && (next == partner || next == '~')) output.append('~');
            if (!encode && next == '~' && i + 2 < text.length()
                && (text.charAt(i + 2) == partner || text.charAt(i + 2) == '~')) i++;
        }
        return output.toString();
    }
}
