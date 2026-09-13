package com.skysoft.features.waypoints.codec;

import java.util.ArrayList;
import java.util.List;

public final class WaypointerRouteData {
    public String label = "";
    public final List<Group> groups = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public static final class Group {
        public String name = "";
        public String zone = "unknown";
        public boolean ordered = true;
        public boolean skipAhead = true;
        public double radius = 3;
        public int gradient;
        public int color = 0x4fe05a;
        public int startColor = 0x00bfff;
        public int endColor = 0xff3040;
        public final List<Point> points = new ArrayList<>();
    }

    public static final class Point {
        public double x;
        public double y;
        public double z;
        public String name = "";
        public int color = 0x4fe05a;
        public double radius;
        public long flags;

        Point(int[] position) { x = position[0]; y = position[1]; z = position[2]; }

        void precision(int packed) {
            WaypointBytes.require(packed >= 0 && packed <= 4095, "Invalid Waypointer precision offset.");
            x += ((packed >>> 8) - 8) / 16.0;
            y += (((packed >>> 4) & 15) - 8) / 16.0;
            z += ((packed & 15) - 8) / 16.0;
        }
    }
}
