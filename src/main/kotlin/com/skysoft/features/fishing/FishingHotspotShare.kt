package com.skysoft.features.fishing

import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.whitespacePattern
import com.skysoft.utils.RegexUtilities.worldVecOrNull
import com.skysoft.utils.WorldVec
import kotlin.math.abs

internal data class FishingHotspotShare(
    val stat: String,
    val location: WorldVec,
) {
    val cleanStat: String = FishingHotspotShareParser.cleanStat(stat)
    val key: String = "${location.x.toInt()},${location.y.toInt()},${location.z.toInt()}:$cleanStat"

    fun matches(other: FishingHotspotShare): Boolean =
        cleanStat.equals(other.cleanStat, ignoreCase = true) &&
            verticalDistance(other) <= MATCH_VERTICAL_RANGE &&
            location.horizontalDistanceSq(other.location) <= MATCH_HORIZONTAL_RANGE_SQ

    private fun verticalDistance(other: FishingHotspotShare): Double =
        abs(location.y - other.location.y)

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
            stat = match.group("stat").trim(),
            location = match.worldVecOrNull() ?: return null,
        )
    }

    fun cleanStat(stat: String): String =
        stat.replace(nonStatTextPattern, " ")
            .replace(whitespacePattern, " ")
            .trim()

    private val nonStatTextPattern = Regex("""[^A-Za-z0-9+.% -]""")
}
