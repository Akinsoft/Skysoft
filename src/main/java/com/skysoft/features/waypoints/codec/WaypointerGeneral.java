package com.skysoft.features.waypoints.codec;

import java.util.ArrayList;
import java.util.List;

final class WaypointerGeneral {
    static final List<String> ZONES = List.of((
        "hub,private_island,dungeon_hub,the_park,the_farming_isles,spiders_den,the_end,crimson_isle,kuudra,gold_mine,"
        + "deep_caverns,dwarven_mines,crystal_hollows,garden,rift,galatea,backwater_bayou,winter,dark_auction,dungeon,"
        + "dungeon_f1,dungeon_f2,dungeon_f3,dungeon_f4,dungeon_f5,dungeon_f6,dungeon_f7,dungeon_m1,dungeon_m2,dungeon_m3,"
        + "dungeon_m4,dungeon_m5,dungeon_m6,dungeon_m7,dynamic,farming_1,foraging_1,foraging_2,combat_1,combat_2,combat_3,"
        + "mining_1,mining_2,mining_3,fishing_1,mineshaft,great_glacite_lake,glacite_tunnels,dwarven_base_camp,mineshaft_unknown,"
        + "mineshaft_topaz_1,mineshaft_topaz_2,mineshaft_sapphire_1,mineshaft_sapphire_2,mineshaft_amethyst_1,mineshaft_amethyst_2,"
        + "mineshaft_amber_1,mineshaft_amber_2,mineshaft_jade_1,mineshaft_jade_2,mineshaft_ruby_1,mineshaft_ruby_2,"
        + "mineshaft_ruby_crystal,mineshaft_onyx_1,mineshaft_onyx_2,mineshaft_onyx_crystal,mineshaft_aquamarine_1,"
        + "mineshaft_aquamarine_2,mineshaft_aquamarine_crystal,mineshaft_citrine_1,mineshaft_citrine_2,mineshaft_citrine_crystal,"
        + "mineshaft_peridot_1,mineshaft_peridot_2,mineshaft_peridot_crystal,mineshaft_jasper,mineshaft_jasper_crystal,"
        + "mineshaft_opal,mineshaft_opal_crystal,mineshaft_titanium,mineshaft_umber,mineshaft_tungsten,mineshaft_vanguard,"
        + "mineshaft_littlefoots_den,mineshaft_crystal").split(","));

    private WaypointerGeneral() {}

    static WaypointerRouteData read(WaypointBytes input, int header) {
        WaypointerRouteData result = new WaypointerRouteData();
        int kind = (header >>> 4) & 7;
        WaypointBytes.require(kind == 0 || kind == 7, "This Waypointer share is not an ordinary route.");
        if (kind == 7) result.label = input.text();
        int poolSize = input.count(65536);
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) pool.add(input.text());
        int count = input.count(256);
        int points = 0;
        for (int i = 0; i < count; i++) {
            WaypointerRouteData.Group group = group(input, pool);
            points += group.points.size();
            WaypointBytes.require(points <= 50000, "Waypointer import contains too many points.");
            result.groups.add(group);
        }
        input.end();
        return result;
    }

    private static WaypointerRouteData.Group group(WaypointBytes input, List<String> pool) {
        WaypointerRouteData.Group group = new WaypointerRouteData.Group();
        group.name = pooled(input, pool);
        int ref = input.count(Integer.MAX_VALUE);
        group.zone = (ref & 1) != 0 ? zone(ref >>> 1) : poolValue(pool, ref >>> 1);
        int flags = input.u8();
        group.ordered = (flags & 4) != 0;
        group.gradient = (flags & 2) != 0 ? 1 : 0;
        if ((flags & 128) != 0) {
            int metadata = input.u8();
            WaypointBytes.require((metadata & 248) == 0 && (metadata & 3) != 3, "Invalid Waypointer group metadata.");
            group.gradient = metadata & 3;
            group.skipAhead = (metadata & 4) != 0;
            WaypointBytes.require((group.gradient == 1) == ((flags & 2) != 0), "Inconsistent Waypointer gradient metadata.");
            group.color = input.rgb();
            group.startColor = input.rgb();
            group.endColor = input.rgb();
        }
        if ((flags & 8) != 0) group.radius = radius(input);
        int count = input.count(20000);
        int mode = ((flags >>> 4) & 3) | ((flags & 64) != 0 ? 4 : 0);
        int[][] positions = WaypointerCoordinates.read(input, count, mode, false);
        boolean bodyless = (flags & 1) != 0;
        for (int[] position : positions) group.points.add(point(input, position, pool, bodyless));
        return group;
    }

    private static WaypointerRouteData.Point point(WaypointBytes input, int[] position, List<String> pool, boolean bodyless) {
        WaypointerRouteData.Point point = new WaypointerRouteData.Point(position);
        int fields = bodyless ? 0 : input.u8();
        if ((fields & 1) != 0) point.name = (fields & 16) != 0 ? input.text() : pooled(input, pool);
        if ((fields & 2) != 0) point.color = input.rgb();
        if ((fields & 4) != 0) point.radius = radius(input);
        if ((fields & 8) != 0) point.flags = input.unsigned(32);
        if ((fields & 32) != 0) point.precision(input.count(4095));
        return point;
    }

    static double radius(WaypointBytes input) {
        double radius = input.number();
        WaypointBytes.require(Double.isFinite(radius) && radius > 0 && radius <= 100, "Invalid Waypointer arrival distance.");
        return radius;
    }

    static String zone(int index) { return index < ZONES.size() ? ZONES.get(index) : "unknown"; }
    private static String pooled(WaypointBytes input, List<String> pool) { return poolValue(pool, input.count(65535)); }
    private static String poolValue(List<String> pool, int index) {
        WaypointBytes.require(index < pool.size(), "Invalid Waypointer string reference.");
        return pool.get(index);
    }
}
