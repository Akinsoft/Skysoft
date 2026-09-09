package com.skysoft.features.misc.custombars

import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.utils.DurationParts
import com.skysoft.utils.NumberUtilities.formatInt

internal object RiftCustomBarValues {
    private const val HEALTH_POINTS_PER_HEART = 2
    private const val SECOND_DIGITS = 2
    private val damagePattern =
        Regex("^Rift Damage:\\s*${SkyBlockStatGlyph.RIFT_DAMAGE}(?<damage>[\\d,]+)$")

    fun parseDamage(lines: Iterable<String>): Int? = lines.firstNotNullOfOrNull { line ->
        damagePattern.matchEntire(line.trim())?.groups?.get("damage")?.value?.formatInt()
    }

    fun formatHearts(value: BarValue?): String = value?.let {
        "${it.displayedCurrent.toHearts()}/${it.maximum.toHearts()}"
    } ?: "---/---"

    fun formatTime(seconds: Int): String {
        val duration = DurationParts.fromSeconds(seconds.toLong())
        return if (duration.totalMinutes == 0L) {
            "${duration.seconds}s"
        } else {
            "${duration.totalMinutes}m${duration.seconds.toString().padStart(SECOND_DIGITS, '0')}s"
        }
    }

    private fun Int.toHearts(): String = if (this % HEALTH_POINTS_PER_HEART == 0) {
        (this / HEALTH_POINTS_PER_HEART).toString()
    } else {
        "${this / HEALTH_POINTS_PER_HEART}.5"
    }
}
