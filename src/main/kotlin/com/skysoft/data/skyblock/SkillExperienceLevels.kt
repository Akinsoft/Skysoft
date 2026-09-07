// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.data.skyblock

internal object SkillExperienceLevels {
    fun getLevelExact(neededXp: Long, skillType: SkyBlockSkill): Int =
        exactLevelingMap[neededXp.toInt()] ?: skillType.maxLevel

    fun calculateLevelXp(level: Int): Double =
        levelArray.asSequence().take(level + 1).sumOf { it.toDouble() }

    fun xpRequiredForLevel(desiredLevel: Int): Long {
        var totalXp = 0L
        if (desiredLevel <= MAX_VANILLA_SKILL_LEVEL) {
            for (level in 1..desiredLevel) {
                totalXp += levelingMap[level]?.toLong() ?: 0L
            }
            return totalXp
        }

        totalXp += XP_NEEDED_FOR_60
        var level = MAX_VANILLA_SKILL_LEVEL
        var xpForNext = BASE_OVERFLOW_XP_FOR_NEXT_LEVEL + OVERFLOW_XP_SLOPE_START
        var slope = OVERFLOW_XP_SLOPE_START
        while (level < desiredLevel) {
            totalXp += xpForNext
            level++
            xpForNext += slope
            if (level % OVERFLOW_SLOPE_DOUBLING_INTERVAL == 0) slope *= OVERFLOW_SLOPE_MULTIPLIER
        }
        return totalXp
    }

    private fun xpAtLevelBoundary(level: Int): Long =
        levelArray.getOrNull(level)?.toLong() ?: DEFAULT_CURRENT_LEVEL_XP

    private fun xpStepAfter(level: Int): Long =
        (levelArray.getOrNull(level + 1)?.toLong() ?: DEFAULT_NEXT_LEVEL_XP) - xpAtLevelBoundary(level)

    fun calculateSkillLevel(currentXp: Long, maxSkillCap: Int): ParsedSkillLevel {
        val maxLevel = maxSkillCap.coerceAtMost(MAX_VANILLA_SKILL_LEVEL)
        val baseProgress = consumeStandardLevels(currentXp, maxLevel)
        var xpCurrent = baseProgress.remainingXp
        var level = baseProgress.level

        var xpForNext = levelingMap[level + 1]?.toLong() ?: 0L
        var overflowXp = 0L
        if (level >= maxLevel) {
            val xpNeeded = xpRequiredForLevel(maxLevel)
            if (currentXp >= xpNeeded) {
                overflowXp = currentXp - xpNeeded
                xpCurrent = overflowXp
                var slope = xpStepAfter(maxLevel)
                var xpForCurrent = xpAtLevelBoundary(maxLevel) + slope
                while (xpCurrent >= xpForCurrent && level < MAX_VANILLA_SKILL_LEVEL) {
                    level++
                    xpCurrent -= xpForCurrent
                    slope = xpStepAfter(level)
                    xpForCurrent += slope
                }
                if (level >= MAX_VANILLA_SKILL_LEVEL) {
                    slope = OVERFLOW_XP_SLOPE_START
                    xpForCurrent = BASE_OVERFLOW_XP_FOR_NEXT_LEVEL + slope
                    while (xpCurrent >= xpForCurrent) {
                        level++
                        xpCurrent -= xpForCurrent
                        xpForCurrent += slope
                        if (level % OVERFLOW_SLOPE_DOUBLING_INTERVAL == 0) slope *= OVERFLOW_SLOPE_MULTIPLIER
                    }
                }
                xpForNext = xpForCurrent
            }
        }
        return ParsedSkillLevel(level, xpCurrent, xpForNext, overflowXp)
    }

    private fun consumeStandardLevels(totalXp: Long, cap: Int): LevelConsumption {
        var remaining = totalXp
        var reached = 0
        for (nextLevel in 1..cap) {
            val cost = levelingMap[nextLevel]?.toLong() ?: break
            if (remaining < cost) break
            remaining -= cost
            reached = nextLevel
        }
        return LevelConsumption(reached, remaining)
    }

    fun xpForNextLevel(level: Int): Double =
        levelArray.getOrNull(level)?.toDouble() ?: DEFAULT_SKILL_XP_TO_NEXT_LEVEL
}

internal data class ParsedSkillLevel(
    val level: Int,
    val xpCurrent: Long,
    val xpForNext: Long,
    val overflowXp: Long,
)

private data class LevelConsumption(val level: Int, val remainingXp: Long)

private const val MAX_VANILLA_SKILL_LEVEL = 60
private const val DEFAULT_SKILL_XP_TO_NEXT_LEVEL = 7_600_000.0
private const val DEFAULT_CURRENT_LEVEL_XP = 4_000_000L
private const val DEFAULT_NEXT_LEVEL_XP = 4_300_000L
private const val BASE_OVERFLOW_XP_FOR_NEXT_LEVEL = 7_000_000L
private const val OVERFLOW_XP_SLOPE_START = 600_000L
private const val OVERFLOW_SLOPE_DOUBLING_INTERVAL = 10
private const val OVERFLOW_SLOPE_MULTIPLIER = 2

private const val XP_NEEDED_FOR_60 = 111_672_425L

private val levelArray = listOf(
    50,
    125,
    200,
    300,
    500,
    750,
    1_000,
    1_500,
    2_000,
    3_500,
    5_000,
    7_500,
    10_000,
    15_000,
    20_000,
    30_000,
    50_000,
    75_000,
    100_000,
    200_000,
    300_000,
    400_000,
    500_000,
    600_000,
    700_000,
    800_000,
    900_000,
    1_000_000,
    1_100_000,
    1_200_000,
    1_300_000,
    1_400_000,
    1_500_000,
    1_600_000,
    1_700_000,
    1_800_000,
    1_900_000,
    2_000_000,
    2_100_000,
    2_200_000,
    2_300_000,
    2_400_000,
    2_500_000,
    2_600_000,
    2_750_000,
    2_900_000,
    3_100_000,
    3_400_000,
    3_700_000,
    4_000_000,
    4_300_000,
    4_600_000,
    4_900_000,
    5_200_000,
    5_500_000,
    5_800_000,
    6_100_000,
    6_400_000,
    6_700_000,
    7_000_000,
)
private val levelingMap = levelArray.withIndex().associate { (index, xp) -> (index + 1) to xp }
private val exactLevelingMap = levelArray.withIndex().associate { (index, xp) -> xp to (index + 1) }
