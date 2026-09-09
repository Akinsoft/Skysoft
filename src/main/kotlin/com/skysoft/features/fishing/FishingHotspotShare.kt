package com.skysoft.features.fishing

import com.skysoft.utils.WorldVec

internal data class FishingHotspotShare(
    val stat: String,
    val location: WorldVec,
) {
    val cleanStat: String = FishingHotspotShareParser.cleanStat(stat)
    val key: String = "${location.x.toInt()},${location.y.toInt()},${location.z.toInt()}:$cleanStat"

    fun matches(other: FishingHotspotShare): Boolean =
        cleanStat.equals(other.cleanStat, ignoreCase = true) &&
            verticalDistance(other) <= MATCH_VERTICAL_RANGE &&
            horizontalDistanceSq(other) <= MATCH_HORIZONTAL_RANGE_SQ

    private fun verticalDistance(other: FishingHotspotShare): Double =
        kotlin.math.abs(location.y - other.location.y)

    private fun horizontalDistanceSq(other: FishingHotspotShare): Double {
        val dx = location.x - other.location.x
        val dz = location.z - other.location.z
        return dx * dx + dz * dz
    }

    private companion object {
        const val MATCH_HORIZONTAL_RANGE_SQ = 36.0
        const val MATCH_VERTICAL_RANGE = 3.0
    }
}

internal object FishingHotspotShareParser {
    const val SHARE_MARKER = "Found a Hotspot!"

    private val sharePattern = Regex(
        """$SHARE_MARKER \((?<stat>[^)]+)\) @ (?<x>-?\d+) (?<y>-?\d+) (?<z>-?\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun format(share: FishingHotspotShare): String {
        val x = share.location.x.toInt()
        val y = share.location.y.toInt()
        val z = share.location.z.toInt()
        return "$SHARE_MARKER (${share.cleanStat}) @ $x $y $z"
    }

    fun parse(message: String): FishingHotspotShare? {
        val match = sharePattern.find(message) ?: return null
        return FishingHotspotShare(
            stat = match.groups["stat"]?.value?.trim().orEmpty(),
            location = WorldVec(
                match.groups["x"]?.value?.toDoubleOrNull() ?: return null,
                match.groups["y"]?.value?.toDoubleOrNull() ?: return null,
                match.groups["z"]?.value?.toDoubleOrNull() ?: return null,
            ),
        )
    }

    fun cleanStat(stat: String): String =
        stat.replace(nonStatTextPattern, " ")
            .replace(spacePattern, " ")
            .trim()

    private val nonStatTextPattern = Regex("""[^A-Za-z0-9+.% -]""")
    private val spacePattern = Regex("""\s+""")
}
