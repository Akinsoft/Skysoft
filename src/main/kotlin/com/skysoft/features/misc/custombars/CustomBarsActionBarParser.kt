package com.skysoft.features.misc.custombars

import com.skysoft.config.CustomBarDisplayMode
import com.skysoft.config.CustomBarsSettingsConfig
import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.features.misc.actionbar.NormalizedActionBar
import com.skysoft.features.misc.actionbar.actionBarSegmentRange
import com.skysoft.utils.NumberUtilities.formatInt

internal object CustomBarsActionBarParser {
    private val healthPattern =
        Regex("(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*[❤${SkyBlockStatGlyph.HEALTH}]")
    private val manaPattern =
        Regex(
            "(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*[✎${SkyBlockStatGlyph.INTELLIGENCE}]" +
                "(?:\\s+Mana)?(?:\\s+(?<overflow>[\\d,]+)\\s*[ʬ${SkyBlockStatGlyph.OVERFLOW_MANA}])?",
        )
    private val defensePattern =
        Regex("(?<defense>[\\d,]+)\\s*[❈${SkyBlockStatGlyph.DEFENSE}](?:\\s+Defense)?")
    private val vitalityPattern =
        Regex("(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*${SkyBlockStatGlyph.VITALITY}")
    private val riftTimePattern =
        Regex("(?:[\\d,]+m)?\\d{1,2}s\\s*${SkyBlockStatGlyph.RIFT_TIME}\\s+Left")

    fun parse(text: String): ParsedCustomBarActionBar {
        val normalized = NormalizedActionBar(text)
        val healthMatch = healthPattern.find(normalized.text)
        val manaMatch = manaPattern.find(normalized.text)
        val vitalityMatch = vitalityPattern.find(normalized.text)
        val defenseMatch = defensePattern.find(normalized.text)
        val riftTimeMatch = riftTimePattern.find(normalized.text)
        return ParsedCustomBarActionBar(
            health = healthMatch?.let {
                BarValue(it.value("current"), it.value("maximum"))
            },
            mana = manaMatch?.let {
                BarValue(it.value("current"), it.value("maximum"), it.groups["overflow"]?.value?.skyBlockInt() ?: 0)
            },
            vitality = vitalityMatch?.let {
                BarValue(it.value("current"), it.value("maximum"))
            },
            defense = defenseMatch?.groups?.get("defense")?.value?.skyBlockInt(),
            removals = buildList {
                healthMatch?.let {
                    val healingFollows = normalized.text.getOrNull(it.range.last + 1) == '+'
                    val range = normalized.rawRange(it.range, preserveLastCharacter = healingFollows)
                    add(
                        StatusRemoval(
                            CustomBarStatus.HEALTH,
                            if (healingFollows) range else text.actionBarSegmentRange(range),
                        ),
                    )
                }
                manaMatch?.let {
                    add(StatusRemoval(CustomBarStatus.MANA, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                vitalityMatch?.let {
                    add(StatusRemoval(CustomBarStatus.VITALITY, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                defenseMatch?.let {
                    add(StatusRemoval(CustomBarStatus.DEFENSE, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                riftTimeMatch?.let {
                    add(StatusRemoval(CustomBarStatus.RIFT_TIME, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
            },
        )
    }

    private fun MatchResult.value(name: String): Int = groups[name]!!.value.skyBlockInt()

    private fun String.skyBlockInt(): Int = formatInt()
}

internal enum class CustomBarStatus {
    HEALTH,
    MANA,
    VITALITY,
    DEFENSE,
    RIFT_TIME,
    ;

    companion object {
        fun hiddenBy(settings: CustomBarsSettingsConfig, inRift: Boolean): Set<CustomBarStatus> = buildSet {
            val displays = settings.displays
            val numbers = settings.numbers
            if (displays.health != CustomBarDisplayMode.VANILLA || numbers.health) add(HEALTH)
            if (displays.mana != CustomBarDisplayMode.VANILLA || numbers.mana) add(MANA)
            if (displays.vitality != CustomBarDisplayMode.VANILLA || numbers.vitality) add(VITALITY)
            if (displays.defense != CustomBarDisplayMode.VANILLA) add(DEFENSE)
            if (inRift && (displays.experience != CustomBarDisplayMode.VANILLA || numbers.experience)) add(RIFT_TIME)
        }
    }
}

internal data class BarValue(val current: Int, val maximum: Int, val overflow: Int = 0) {
    val displayOverflow: Int get() = overflow.coerceAtLeast((current - maximum).coerceAtLeast(0))
    val regularCurrent: Int get() = current.coerceAtMost(maximum)
    val displayedCurrent: Int get() = regularCurrent + displayOverflow
}

internal data class ParsedCustomBarActionBar(
    val health: BarValue?,
    val mana: BarValue?,
    val vitality: BarValue?,
    val defense: Int?,
    private val removals: List<StatusRemoval>,
) {
    fun ranges(hidden: Set<CustomBarStatus>): List<IntRange> =
        removals.filter { it.status in hidden }.map(StatusRemoval::range)
}

internal data class StatusRemoval(val status: CustomBarStatus, val range: IntRange)
