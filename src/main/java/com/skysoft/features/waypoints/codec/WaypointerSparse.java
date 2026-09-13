package com.skysoft.features.waypoints.codec;

import java.util.ArrayList;
import java.util.List;

final class WaypointerSparse {
    private WaypointerSparse() {}

    static WaypointerRouteData.Group read(WaypointBytes input, boolean delta) {
        int selector = input.count(2_097_152);
        WaypointerRouteData.Group group = new WaypointerRouteData.Group();
        if (selector >= 2) {
            WaypointBytes coordinates = new WaypointBytes(input.bytes(selector));
            for (int[] point : WaypointerEntropy.read(coordinates, delta)) group.points.add(new WaypointerRouteData.Point(point));
            coordinates.end();
            split(input, group);
        } else {
            List<Entry> entries = unified(input, selector);
            for (int[] point : WaypointerEntropy.read(input, delta)) group.points.add(new WaypointerRouteData.Point(point));
            for (Entry entry : entries) {
                WaypointBytes.require(entry.index < group.points.size(), "Waypointer metadata index exceeds its route.");
                WaypointerRouteData.Point point = group.points.get(entry.index);
                point.flags = entry.flags;
                point.precision(entry.precision);
            }
        }
        WaypointBytes.require(group.points.isEmpty() || (group.points.getFirst().flags & 16) == 0,
            "The first waypoint cannot be a subwaypoint.");
        input.end();
        return group;
    }

    private static void split(WaypointBytes input, WaypointerRouteData.Group group) {
        int header = input.u8();
        WaypointBytes.require((header & 192) == 0 && header != 0, "Invalid Waypointer sparse header.");
        int count = group.points.size();
        for (int stream = 0; stream < 3; stream++) {
            int mode = (header >>> (stream * 2)) & 3;
            if (mode == 0) continue;
            List<Integer> indexes = indexes(input, count, mode);
            if (stream < 2) {
                WaypointBytes.Bits bits = new WaypointBytes.Bits(input, true);
                for (int index : indexes) {
                    WaypointerRouteData.Point point = group.points.get(index);
                    if (stream == 0) point.flags = 16 | (bits.read(3) << 5);
                    else {
                        int precision = (int) bits.read(12);
                        WaypointBytes.require(precision != 0x888, "Redundant Waypointer precision.");
                        point.precision(precision);
                    }
                }
                bits.end();
            } else for (int index : indexes) {
                WaypointerRouteData.Point point = group.points.get(index);
                point.flags |= otherFlags(input, (point.flags & 16) != 0);
            }
        }
    }

    private static List<Integer> indexes(WaypointBytes input, int count, int mode) {
        List<Integer> values = new ArrayList<>();
        if (mode == 1) {
            int next = 0;
            for (int index = 0; index < count; index++) {
                if (index % 8 == 0) next = input.u8();
                if ((next & 1) != 0) values.add(index);
                next >>>= 1;
            }
            WaypointBytes.require(next == 0, "Invalid Waypointer index padding.");
        } else {
            WaypointBytes.require(mode == 2, "Reserved Waypointer index mode.");
            int size = input.count(count);
            int previous = -1;
            for (int i = 0; i < size; i++) {
                previous += input.count(count) + 1;
                WaypointBytes.require(previous < count, "Waypointer index is outside its route.");
                values.add(previous);
            }
        }
        WaypointBytes.require(!values.isEmpty(), "Empty Waypointer metadata stream.");
        return values;
    }

    private static List<Entry> unified(WaypointBytes input, int selector) {
        List<Integer> indexes = indexes(input, 20000, 2);
        WaypointBytes.Bits bits = new WaypointBytes.Bits(input, true);
        boolean anySub = true;
        boolean allSub = false;
        boolean anyOther = true;
        boolean allOther = false;
        boolean anyPrecision = true;
        if (selector == 1) {
            anySub = bits.read(1) != 0;
            allSub = anySub && bits.read(1) != 0;
            anyOther = bits.read(1) != 0;
            allOther = anyOther && bits.read(1) != 0;
            anyPrecision = bits.read(1) != 0;
        }
        List<Entry> entries = new ArrayList<>();
        for (int index : indexes) {
            boolean sub = anySub && (allSub || bits.read(1) != 0);
            long flags = sub ? 16 | (bits.read(3) << 5) : 0;
            boolean other = anyOther && (allOther || bits.read(1) != 0);
            int mask = anyPrecision ? (int) bits.read(3) : 0;
            int precision = 0;
            for (int axis = 0; axis < 3; axis++) {
                int residual = (mask & (1 << axis)) != 0 ? (int) bits.read(4) : 0;
                if ((mask & (1 << axis)) != 0) WaypointBytes.require(residual != 0, "Redundant Waypointer precision residual.");
                if (residual >= 8) residual -= 16;
                precision = (precision << 4) | (8 + residual);
            }
            WaypointBytes.require(sub || other || mask != 0, "Empty Waypointer metadata record.");
            entries.add(new Entry(index, flags, precision, other));
        }
        bits.end();
        for (Entry entry : entries) if (entry.other) entry.flags |= otherFlags(input, (entry.flags & 16) != 0);
        return entries;
    }

    private static long otherFlags(WaypointBytes input, boolean sub) {
        long flags = input.unsigned(32);
        WaypointBytes.require(flags != 0 && (flags & (sub ? 0xf0 : 0x10)) == 0, "Invalid Waypointer semantic flags.");
        return flags;
    }

    private static final class Entry {
        final int index;
        long flags;
        final int precision;
        final boolean other;
        Entry(int index, long flags, int precision, boolean other) {
            this.index = index;
            this.flags = flags;
            this.precision = precision;
            this.other = other;
        }
    }
}
