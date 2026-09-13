package com.skysoft.features.waypoints.codec;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class WaypointerDecoder {
    private static final int MAX_BYTES = 2_097_152;

    private WaypointerDecoder() {}

    public static WaypointerRouteData decode(String share) {
        WaypointBytes.require(share.startsWith("WP:") && share.length() <= 3_145_731, "Invalid or oversized Waypointer share.");
        String text = share.substring(3);
        WaypointBytes.require(!text.isEmpty(), "Empty Waypointer share.");
        return current(frame(text));
    }

    private static Frame frame(String text) {
        byte[] payload = WaypointerText.stream(WaypointerText.contextual(text, false));
        WaypointBytes.require(payload.length >= 3 && payload.length <= MAX_BYTES, "Invalid Waypointer frame size.");
        int header = payload[0] & 255;
        WaypointBytes.require((header & 15) == 10, "Only Waypointer V10 shares are supported. Export the route from the latest Waypointer.");
        WaypointBytes.require(WaypointerText.contextual(WaypointerText.encode(payload), true).equals(text), "Noncanonical Waypointer share text.");
        byte[] body = Arrays.copyOfRange(payload, 1, payload.length - 2);
        if ((header & 128) != 0) body = inflate(body);
        WaypointBytes.require(body.length + 3 <= MAX_BYTES, "Waypointer frame exceeds its size limit.");
        int checksum = crc16(0xffff, header);
        for (byte next : body) checksum = crc16(checksum, next & 255);
        int expected = ((payload[payload.length - 2] & 255) << 8) | (payload[payload.length - 1] & 255);
        WaypointBytes.require(checksum == expected, "Waypointer checksum does not match. Copy the complete share again.");
        return new Frame(header & 127, (header & 128) != 0, body);
    }

    private static WaypointerRouteData current(Frame frame) {
        WaypointBytes input = new WaypointBytes(frame.body);
        int kind = (frame.header >>> 4) & 7;
        if (kind == 0 || kind == 7) return WaypointerGeneral.read(input, frame.header);
        WaypointerRouteData result = new WaypointerRouteData();
        switch (kind) {
            case 1 -> result.groups.add(WaypointerCompact.read(input));
            case 2 -> result.groups.add(bare(input, frame.delta));
            case 5 -> result.groups.add(WaypointerSparse.read(input, frame.delta));
            case 6 -> result = library(input, frame.delta);
            case 3 -> throw new IllegalArgumentException("This Waypointer share contains settings. Export an ordinary route.");
            case 4 -> throw new IllegalArgumentException("Dungeon-room shares need room orientation support. Export an ordinary route.");
            default -> throw new IllegalArgumentException("Unsupported Waypointer content kind.");
        }
        input.end();
        return result;
    }

    private static WaypointerRouteData.Group bare(WaypointBytes input, boolean delta) {
        WaypointerRouteData.Group group = new WaypointerRouteData.Group();
        for (int[] point : WaypointerEntropy.read(input, delta)) group.points.add(new WaypointerRouteData.Point(point));
        input.end();
        return group;
    }

    private static WaypointerRouteData library(WaypointBytes input, boolean delta) {
        int subtype = input.count(2);
        if (subtype == 2) {
            WaypointBytes.require(input.count(0) == 0, "Unknown Waypointer catalog.");
            int form = input.u8();
            WaypointBytes.require(form == 0 || form == 1, "Invalid Waypointer catalog reference.");
            String id = form == 0 ? Base64.getUrlEncoder().withoutPadding().encodeToString(input.bytes(16)) : input.text();
            input.end();
            throw new IllegalArgumentException("Catalog reference " + id + " has no coordinates. Export its route data from Waypointer first.");
        }
        if (subtype == 0) {
            WaypointerRouteData result = new WaypointerRouteData();
            int count = input.count(256);
            WaypointBytes.require(count >= 2, "A Waypointer route pack requires at least two groups.");
            int points = 0;
            for (int i = 0; i < count; i++) {
                WaypointerRouteData.Group group = bare(input.section(MAX_BYTES), delta);
                points += group.points.size();
                WaypointBytes.require(points <= 50000, "Waypointer route pack contains too many points.");
                result.groups.add(group);
            }
            return result;
        }
        WaypointBytes route = input.section(MAX_BYTES);
        int header = route.u8();
        WaypointBytes.require(header == 10 || header == 122, "Invalid Waypointer library route header.");
        WaypointerRouteData result = WaypointerGeneral.read(route, header);
        libraryMetadata(input, result);
        return result;
    }

    private static void libraryMetadata(WaypointBytes input, WaypointerRouteData result) {
        int manual = input.count(256);
        int previous = -1;
        for (int i = 0; i < manual; i++) {
            int index = input.count(result.groups.size() - 1);
            WaypointBytes.require(index > previous, "Invalid Waypointer color snapshot order.");
            previous = index;
            int colors = input.count(20000);
            WaypointBytes.require(colors == result.groups.get(index).points.size(), "Waypointer color snapshot count does not match.");
            input.bytes(colors * 3);
        }
        int folders = input.count(256);
        boolean[] assigned = new boolean[result.groups.size()];
        for (int i = 0; i < folders; i++) {
            input.text();
            input.rgb();
            WaypointBytes.require(input.u8() <= 1, "Invalid Waypointer folder flags.");
            int members = input.count(256);
            WaypointBytes.require(members > 0, "Empty Waypointer folder.");
            for (int j = 0; j < members; j++) {
                int member = input.count(result.groups.size() - 1);
                WaypointBytes.require(!assigned[member], "Duplicate Waypointer folder member.");
                assigned[member] = true;
            }
        }
        int paints = input.count(256);
        previous = -1;
        for (int i = 0; i < paints; i++) {
            int index = input.count(result.groups.size() - 1);
            WaypointBytes.require(index > previous && input.u8() <= 1, "Invalid Waypointer paint record.");
            previous = index;
            input.bytes(16 * 3 + 768);
        }
        WaypointBytes.require(manual + folders + paints > 0, "Empty Waypointer library metadata.");
        result.warnings.add("Waypointer folders, saved gradient snapshots and block paintings are not imported.");
    }

    private static int crc16(int crc, int next) {
        crc ^= next << 8;
        for (int bit = 0; bit < 8; bit++) crc = ((crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0)) & 0xffff;
        return crc;
    }

    private static byte[] inflate(byte[] bytes) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(bytes);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                WaypointBytes.require(count > 0 || inflater.finished(), "Truncated or invalid Waypointer compressed data.");
                WaypointBytes.require(output.size() + count <= MAX_BYTES, "Waypointer data exceeds its size limit.");
                output.write(buffer, 0, count);
            }
            WaypointBytes.require(inflater.getRemaining() == 0, "Trailing Waypointer compressed data.");
            return output.toByteArray();
        } catch (DataFormatException failure) {
            throw new IllegalArgumentException("Invalid Waypointer compressed data.", failure);
        } finally { inflater.end(); }
    }

    private record Frame(int header, boolean delta, byte[] body) {}
}
