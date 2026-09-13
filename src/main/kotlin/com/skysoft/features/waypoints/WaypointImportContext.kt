package com.skysoft.features.waypoints

import com.skysoft.data.SkyBlockIsland

internal class WaypointImportContext(val source: String) {
    val warnings = linkedSetOf<String>()
    private val missingIslands = linkedSetOf<String>()
    private var pointCount = 0
    private var groupCount = 0

    fun group(name: String, zone: String?, points: List<WaypointPoint>, ordered: Boolean): WaypointGroup {
        require(++groupCount <= MAX_WAYPOINT_GROUPS) { "Too many waypoint presets." }
        pointCount += points.size
        require(pointCount <= MAX_WAYPOINT_POINTS) { "Too many waypoints." }
        val island = zone?.let(::resolveIsland)
        val group = WaypointGroup(
            name = name(name.ifBlank { "Imported preset" }), island = island ?: SkyBlockIsland.HUB,
            points = points, route = ordered
        )
        if (island == null) {
            missingIslands += group.id
            if (!zone.isNullOrBlank() && zone != "unknown") warnings += "Choose an island for the unrecognized location '$zone'."
        }
        return group
    }

    fun name(value: String): String {
        val clean = value.replace(FORMATTING, "").filter { it >= ' ' && it != '\u007f' && it != '§' }
            .take(MAX_WAYPOINT_NAME).dropLastWhile(Char::isHighSurrogate).trim()
        if (clean != value) warnings += "Long names and text formatting were shortened or removed."
        return clean
    }

    fun radius(value: Double): Double {
        require(value.isFinite() && value > 0.0) { "Waypoint arrival distance is invalid." }
        val converted = value.coerceIn(MIN_WAYPOINT_RADIUS, MAX_WAYPOINT_RADIUS)
        if (converted != value) warnings += "Arrival distances were limited to $MIN_WAYPOINT_RADIUS–$MAX_WAYPOINT_RADIUS blocks."
        return converted
    }

    fun finish(groups: List<WaypointGroup>): WaypointImport {
        require(groups.isNotEmpty() && groups.any { it.points.isNotEmpty() }) { "No waypoints were found in this import." }
        WaypointValidation.validate(groups)
        return WaypointImport(source, groups, warnings.toList(), missingIslands)
    }

    private fun resolveIsland(zone: String): SkyBlockIsland? {
        val normalized = zone.trim().lowercase().replace(' ', '_').replace('-', '_').replace("'", "")
        return when {
            normalized.startsWith("dungeon_") && normalized != "dungeon_hub" -> SkyBlockIsland.DUNGEONS
            normalized.startsWith("mineshaft_") -> SkyBlockIsland.GLACITE_MINESHAFTS
            normalized in setOf("great_glacite_lake", "glacite_tunnels", "dwarven_base_camp") -> SkyBlockIsland.DWARVEN_MINES
            normalized == "private_island_guest" || normalized == "garden_guest" ->
                error("Waypoints require your own Private Island or Garden.")
            normalized == "private_islands" -> SkyBlockIsland.PRIVATE_ISLAND
            normalized == "the_farming_isles" -> SkyBlockIsland.THE_FARMING_ISLANDS
            normalized == "rift" -> SkyBlockIsland.THE_RIFT
            else -> SkyBlockIsland.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: SkyBlockIsland.getByLocation(normalized, zone)
        }
    }

    private companion object {
        val FORMATTING = Regex("§.")
    }
}
