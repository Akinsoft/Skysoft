// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.removeColor
import kotlin.math.roundToLong

internal object SkillTabProgress {
    private var snapshot = SkillTabSnapshot()

    fun register(hasActiveListeners: () -> Boolean) {
        TabListApi.onChange("Skill Experience API", hasActiveListeners) {
            snapshot = SkillTabSnapshot(
                contentVersion = TabListApi.contentVersion,
                skills = buildMap {
                    for (line in TabListApi.lines) {
                        val (skill, progress) = parseSkillTabLine(line.string.removeColor()) ?: continue
                        putIfAbsent(skill, progress)
                    }
                },
            )
        }
        SkysoftClientEvents.onDisconnect("Skill Experience tab reset") { snapshot = SkillTabSnapshot() }
    }

    fun get(skill: SkyBlockSkill): TabSkillInfo? {
        if (!TabListApi.isLoaded || snapshot.contentVersion != TabListApi.contentVersion) return null
        return snapshot.skills[skill]
    }
}

private fun parseSkillTabLine(line: String): Pair<SkyBlockSkill, TabSkillInfo>? {
    val numericMatch = skillTabNoPercentPattern.matchEntire(line)
    val match = numericMatch
        ?: skillTabPattern.matchEntire(line)
        ?: maxSkillTabPattern.matchEntire(line)
        ?: return null
    val skill = SkyBlockSkill.entries.firstOrNull { it.displayName == match.group("type") } ?: return null
    val level = match.group("level").toIntOrNull() ?: return null
    val progress = if (numericMatch != null) {
        parseNumericTabProgress(numericMatch, level) ?: return null
    } else {
        TabSkillInfo(level = level)
    }
    return skill to progress
}

private fun parseNumericTabProgress(match: MatchResult, level: Int): TabSkillInfo? {
    val currentXp = match.group("current").formatDoubleOrNull()?.roundToLong() ?: return null
    val neededXp = match.group("needed").formatDoubleOrNull()?.roundToLong() ?: return null
    return TabSkillInfo(level, currentXp, neededXp)
}

private data class SkillTabSnapshot(
    val contentVersion: Long = -1L,
    val skills: Map<SkyBlockSkill, TabSkillInfo> = emptyMap(),
)

internal data class TabSkillInfo(
    val level: Int,
    val currentXp: Long? = null,
    val neededXp: Long? = null,
)

private val skillTabPattern = Regex(""" (?<type>\w+)(?: (?<level>\d+))?: (?<progress>[0-9.]+)%""")
private val maxSkillTabPattern = Regex(""" (?<type>\w+) (?<level>\d+): MAX""")
private val skillTabNoPercentPattern =
    Regex(""" (?<type>\w+)(?: (?<level>\d+))?: (?<current>[0-9,.]+)/(?<needed>[\d,.]+[kmbKMB]?)""")
