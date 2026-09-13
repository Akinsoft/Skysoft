package com.skysoft.features.waypoints.codec;

import java.util.Arrays;

final class WaypointerCompact {
    private WaypointerCompact() {}

    static WaypointerRouteData.Group read(WaypointBytes input) {
        int control = input.u8();
        boolean noNames = (control & 1) != 0;
        int gradient = (control >>> 5) & 3;
        WaypointBytes.require(gradient != 3 && (!noNames || gradient == 2 && (control & 128) == 0), "Invalid compact Waypointer control.");
        WaypointerRouteData.Group group = new WaypointerRouteData.Group();
        group.gradient = gradient == 0 ? 2 : gradient == 1 ? 1 : 0;
        group.ordered = (control & 8) == 0;
        group.skipAhead = (control & 2) == 0;
        group.zone = zone(input);
        if (noNames) {
            WaypointBytes coordinates = input.section(2_097_152);
            for (int[] position : WaypointerEntropy.read(coordinates, false)) group.points.add(new WaypointerRouteData.Point(position));
            coordinates.end();
        } else {
            group.name = groupName(input, group.zone);
            payload(input, group);
        }
        if ((control & 4) != 0) {
            long[] palette = new long[input.count(group.points.size())];
            WaypointBytes.require(palette.length > 0, "Empty Waypointer flag palette.");
            for (int i = 0; i < palette.length; i++) palette[i] = input.unsigned(32);
            int[] indexes = indexes(input, group.points.size(), palette.length, false);
            for (int i = 0; i < indexes.length; i++) group.points.get(i).flags = palette[indexes[i]];
        }
        if ((control & 16) != 0) group.radius = WaypointerGeneral.radius(input);
        if ((control & 128) != 0) {
            group.color = input.rgb();
            group.startColor = input.rgb();
            group.endColor = input.rgb();
        }
        if (input.remaining() > 0) precision(input, group);
        input.end();
        return group;
    }

    private static void payload(WaypointBytes input, WaypointerRouteData.Group group) {
        int count = input.count(20000);
        String[] names = names(input, count);
        int[] colors = colors(input, count);
        int[][] coordinates = WaypointerCoordinates.read(input, count, 6, true);
        for (int i = 0; i < count; i++) {
            WaypointerRouteData.Point point = new WaypointerRouteData.Point(coordinates[i]);
            point.name = names[i];
            point.color = colors[i];
            group.points.add(point);
        }
    }

    private static String zone(WaypointBytes input) {
        int token = input.u8();
        if (token == 255) return input.text();
        if (token > 0) {
            WaypointBytes.require(token <= WaypointerGeneral.ZONES.size(), "Invalid Waypointer zone token.");
            return WaypointerGeneral.zone(token - 1);
        }
        int count = input.count(1_048_576);
        WaypointBytes.Bits bits = new WaypointBytes.Bits(input, false);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int value = (int) bits.read(5);
            WaypointBytes.require(value < 28, "Invalid packed Waypointer zone.");
            text.append(value < 26 ? (char) ('a' + value) : value == 26 ? '-' : '_');
        }
        bits.end();
        return text.toString();
    }

    private static String groupName(WaypointBytes input, String zone) {
        int token = input.u8();
        String words = zone.replace('_', ' ').replace('-', ' ');
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < words.length(); i++) title.append(i == 0 || words.charAt(i - 1) == ' '
            ? Character.toUpperCase(words.charAt(i)) : words.charAt(i));
        return switch (token) {
            case 0 -> input.text();
            case 1 -> "";
            case 2 -> "Secret Route";
            case 3 -> title + " secrets";
            case 4 -> "Secret Route — " + title;
            case 5 -> "Route 1";
            case 6 -> "New group";
            case 7 -> "Imported Route";
            case 8 -> "Route -- " + words;
            default -> throw new IllegalArgumentException("Invalid Waypointer group name token.");
        };
    }

    private static String[] names(WaypointBytes input, int count) {
        String[] names = new String[count];
        int mode = input.u8();
        switch (mode) {
            case 0 -> Arrays.fill(names, "");
            case 1 -> Arrays.fill(names, input.text());
            case 2, 4 -> {
                String prefix = mode == 4 ? input.text() : "";
                long start = input.signed(64);
                long step = input.signed(64);
                for (int i = 0; i < count; i++) names[i] = prefix + Math.addExact(start, Math.multiplyExact(step, i));
            }
            case 3 -> {
                long value = 0;
                for (int i = 0; i < count; i++) { value = Math.addExact(value, input.signed(64)); names[i] = Long.toString(value); }
            }
            case 5 -> {
                String[] palette = new String[input.count(count)];
                for (int i = 0; i < palette.length; i++) palette[i] = input.text();
                int[] indexes = indexes(input, count, palette.length, true);
                for (int i = 0; i < count; i++) names[i] = palette[indexes[i]];
            }
            default -> throw new IllegalArgumentException("Invalid Waypointer name encoding.");
        }
        return names;
    }

    private static int[] colors(WaypointBytes input, int count) {
        int[] colors = new int[count];
        int mode = input.u8();
        switch (mode) {
            case 0 -> Arrays.fill(colors, input.rgb());
            case 1 -> {
                int[] palette = new int[input.count(count)];
                for (int i = 0; i < palette.length; i++) palette[i] = input.rgb();
                int[] indexes = indexes(input, count, palette.length, true);
                for (int i = 0; i < count; i++) colors[i] = palette[indexes[i]];
            }
            case 2 -> { for (int i = 0; i < count; i++) colors[i] = input.rgb(); }
            default -> throw new IllegalArgumentException("Invalid Waypointer color encoding.");
        }
        return colors;
    }

    private static int[] indexes(WaypointBytes input, int count, int cardinality, boolean leastFirst) {
        WaypointBytes.require(cardinality > 0 && cardinality <= count, "Invalid Waypointer palette size.");
        int width = 32 - Integer.numberOfLeadingZeros(cardinality - 1);
        WaypointBytes.Bits bits = new WaypointBytes.Bits(input, leastFirst);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = (int) bits.read(width);
            WaypointBytes.require(values[i] < cardinality, "Invalid Waypointer palette index.");
        }
        bits.end();
        return values;
    }

    private static void precision(WaypointBytes input, WaypointerRouteData.Group group) {
        int count = input.count(group.points.size());
        WaypointBytes.require(count > 0, "Empty Waypointer precision stream.");
        int index = -1;
        for (int i = 0; i < count; i++) {
            index = Math.addExact(index, input.count(group.points.size()) + 1);
            WaypointBytes.require(index < group.points.size(), "Waypointer precision index is out of range.");
            WaypointerRouteData.Point point = group.points.get(index);
            point.x += input.signed(32) / 16.0;
            point.y += input.signed(32) / 16.0;
            point.z += input.signed(32) / 16.0;
        }
    }
}
