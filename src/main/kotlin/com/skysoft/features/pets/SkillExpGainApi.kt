// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.features.pets

import com.google.gson.annotations.Expose
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

object SkillExpGainApi {
    private val storage get() = ProfileStorageApi.storage.skillData
    private val listeners = mutableListOf<Listener>()
    private var lastLilySplosion = ElapsedTimeMark.farPast()
    private var activeSkill: SkyBlockSkill? = null

    fun register() {
        ChatEvents.onActionBar("Skill Experience action bar") { message ->
            if (HypixelLocationState.inSkyBlock) handleActionBar(message.component)
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onVisibleMessage("Skill Experience chat") { message ->
            if (HypixelLocationState.inSkyBlock) handleChat(message.formattedText)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("Skill Experience disconnect reset") {
            lastLilySplosion = ElapsedTimeMark.farPast()
        }
    }

    fun onSkillExpGain(boundary: String, listener: (SkillExpGain) -> Unit) {
        listeners += Listener(boundary, listener)
    }

    fun getSkillInfo(skill: SkyBlockSkill): SkillInfo? = storage[skill]

    fun readOpenInventory(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (inventoryName != "Your Skills") return
        var changed = false
        for (stack in inventoryItems.values) {
            val lore = stack.loreLines()
            if (lore.none { it.contains("Click to view!") || it.contains("Not unlocked!") }) continue
            val split = stack.formattedHoverName().cleanSkyBlockText().split(" ")
            val skillName = split.firstOrNull() ?: continue
            val skill = SkyBlockSkill.getByNameOrNull(skillName) ?: continue
            val skillLevel = split.getOrNull(1)?.romanToDecimalIfNecessary() ?: 0
            val skillInfo = storage.getOrPut(skill, ::SkillInfo)
            if (readSkillMenuLore(lore, skill, skillInfo, skillLevel) == ChangeResult.CHANGED) changed = true
        }
        if (changed) ProfileStorageApi.markDirty()
    }

    private fun handleActionBar(component: Component) {
        val components = component.cleanSkyBlockText().split("  ").map { it.trim() }.filter { it.isNotEmpty() }
        for (part in components) {
            val matcher = listOf(skillPercentPattern, skillMultiplierPattern)
                .firstNotNullOfOrNull { pattern -> pattern.matchEntire(part)?.toMatcher(pattern) }
                ?: continue
            val skillType = SkyBlockSkill.getByNameOrNull(matcher.group("skillName")) ?: continue
            val gained = matcher.group("gained").formatDoubleOrNull() ?: continue
            val skillInfo = storage.getOrPut(skillType, ::SkillInfo)
            val previousTotalXp = skillInfo.totalXp.takeIf { it > 0L }?.toDouble()
            activeSkill = skillType
            when (matcher.pattern) {
                skillPercentPattern -> handlePercentActionBar(matcher, skillType, skillInfo)
                skillMultiplierPattern -> handleMultiplierActionBar(matcher, skillType, skillInfo)
            }
            ProfileStorageApi.markDirty()
            post(
                SkillExpGain(
                    skill = skillType,
                    gained = gained,
                    totalXp = skillInfo.totalXp.takeIf { it > 0L }?.toDouble(),
                    previousTotalXp = previousTotalXp,
                    source = ACTIONBAR_SOURCE,
                ),
            )
            return
        }
    }

    fun handleChat(message: String) {
        for (line in message.cleanSkyBlockText().lineSequence().map { it.trim() }) {
            if (lilySplosionStartPattern.matches(line)) {
                lastLilySplosion = ElapsedTimeMark.now()
                continue
            }
            jerryBoxSkillXpPattern.matchEntire(line)?.let { match ->
                val gained = match.group("gained").formatDoubleOrNull() ?: return
                postChatSkillXp(match.group("skillName"), gained, JERRY_BOX_SOURCE)
                return
            }
            giftSkillXpPattern.matchEntire(line)?.let { match ->
                val gained = match.group("gained").formatDoubleOrNull() ?: return
                postChatSkillXp(match.group("skillName"), gained, GIFT_SOURCE)
                return
            }
            lilySplosionSkillXpPattern.matchEntire(line)?.let { match ->
                if (lastLilySplosion.passedSince() <= 5.seconds) {
                    val gained = match.group("gained").formatDoubleOrNull() ?: return
                    postChatSkillXp(match.group("skillName"), gained, LILY_SPLOSION_SOURCE)
                }
                return
            }
        }
    }

    private fun postChatSkillXp(skillName: String, gained: Double, source: String) {
        val skillType = SkyBlockSkill.getByNameOrNull(skillName) ?: return
        val totalXp = addChatSkillXp(skillType, gained)
        post(SkillExpGain(skillType, gained, totalXp, source = source))
    }

    private fun addChatSkillXp(skillType: SkyBlockSkill, gained: Double): Double? {
        val skillInfo = storage[skillType] ?: return null
        if (skillInfo.totalXp <= 0L) return null
        val totalXp = skillInfo.totalXp + gained
        val roundedTotalXp = totalXp.roundToLong()
        skillInfo.recordChatGain(
            parsedLevel = calculateSkillLevel(roundedTotalXp, skillType.maxLevel),
            roundedTotalXp = roundedTotalXp,
            maxLevel = skillType.maxLevel,
            gained = gained,
        )
        ProfileStorageApi.markDirty()
        return totalXp
    }

    private fun SkillInfo.recordChatGain(
        parsedLevel: ParsedSkillLevel,
        roundedTotalXp: Long,
        maxLevel: Int,
        gained: Double,
    ) {
        update(
            displayed = DisplayedSkillProgress(
                parsedLevel.level.coerceAtMost(maxLevel),
                roundedTotalXp,
                parsedLevel.xpCurrent,
                parsedLevel.xpForNext,
            ),
            overflow = parsedLevel,
            gainText = gained.toString(),
        )
    }

    private fun readSkillMenuLore(
        lore: List<String>,
        skill: SkyBlockSkill,
        skillInfo: SkillInfo,
        skillLevel: Int,
    ): ChangeResult {
        val before = skillInfo.copy()
        lore.forEachIndexed { index, line ->
            val cleanLine = line.cleanSkyBlockText()
            val progress = cleanLine.substringAfterLast(' ')
            if (!skillMenuProgressPattern.matches(progress)) return@forEachIndexed
            val previousLine = lore.getOrNull(index - 1)?.cleanSkyBlockText()
            if (previousLine == "Max Skill level reached!") {
                onUpdateMax(progress, skill, skillInfo, skillLevel)
            } else if (progress.contains('/')) {
                onUpdateNotMax(progress, skillLevel, skillInfo)
            }
        }
        return ChangeResult.from(skillInfo != before)
    }

    private fun handlePercentActionBar(matcher: SkillMatcher, skillType: SkyBlockSkill, skillInfo: SkillInfo) {
        val tabInfo = readTabSkillInfo(skillType) ?: return
        val progress = matcher.group("progress").formatDoubleOrNull() ?: return
        val level = tabInfo.level
        if (tabInfo.currentXp != null && tabInfo.neededXp != null) {
            val totalXp = calculateLevelXp(level - 1).toLong() + tabInfo.currentXp
            updateSkillInfo(skillInfo, level, tabInfo.currentXp, tabInfo.neededXp, totalXp, matcher.group("gained"))
            return
        }
        val levelXp = calculateLevelXp(level - 1)
        val nextLevelDiff = levelArray.getOrNull(level)?.toDouble() ?: DEFAULT_SKILL_XP_TO_NEXT_LEVEL
        val currentXp = (nextLevelDiff * progress / PERCENT_DENOMINATOR).toLong()
        val totalXp = (levelXp + currentXp).toLong()
        updateSkillInfo(skillInfo, level, currentXp, nextLevelDiff.toLong(), totalXp, matcher.group("gained"))
    }

    private fun handleMultiplierActionBar(matcher: SkillMatcher, skillType: SkyBlockSkill, skillInfo: SkillInfo) {
        val currentXp = matcher.group("current").formatDoubleOrNull()?.roundToLong() ?: return
        val maxXp = matcher.group("needed").formatDoubleOrNull()?.roundToLong() ?: return
        val minus = if (maxXp == 0L) 0 else 1
        val level = getLevelExact(maxXp, skillType) - minus
        val totalXp = if (maxXp == 0L) currentXp else calculateLevelXp(level - 1).roundToLong() + currentXp
        updateSkillInfo(skillInfo, level, currentXp, maxXp, totalXp, matcher.group("gained"))
    }

    private fun updateSkillInfo(
        skillInfo: SkillInfo,
        level: Int,
        currentXp: Long,
        maxXp: Long,
        totalXp: Long,
        gained: String,
    ) {
        val cap = activeSkill?.maxLevel
        val add = cap?.takeIf { level >= it }?.let(::xpRequiredForLevel) ?: 0L
        val skillLevel = calculateSkillLevel(totalXp + add, cap ?: MAX_VANILLA_SKILL_LEVEL)
        skillInfo.update(DisplayedSkillProgress(level, totalXp, currentXp, maxXp), skillLevel, gained)
    }

    private fun onUpdateMax(progress: String, skill: SkyBlockSkill, skillInfo: SkillInfo, skillLevel: Int) {
        val totalXp = progress.formatDoubleOrNull()?.roundToLong() ?: return
        val cap = skill.maxLevel
        val maxXp = xpRequiredForLevel(cap)
        val currentXp = totalXp - maxXp
        val overflow = calculateSkillLevel(totalXp, cap)
        skillInfo.update(DisplayedSkillProgress(skillLevel, totalXp, currentXp, 0L), overflow)
    }

    private fun onUpdateNotMax(progress: String, skillLevel: Int, skillInfo: SkillInfo) {
        val splitProgress = progress.split("/")
        val currentXp = splitProgress.firstOrNull()?.formatDoubleOrNull()?.roundToLong() ?: return
        val neededXp = splitProgress.getOrNull(1)?.formatDoubleOrNull()?.roundToLong() ?: return
        val levelXp = calculateLevelXp(skillLevel - 1).toLong()
        val totalXp = levelXp + currentXp
        skillInfo.update(
            DisplayedSkillProgress(skillLevel, totalXp, currentXp, neededXp),
            ParsedSkillLevel(skillLevel, currentXp, neededXp, totalXp),
        )
    }

    private fun readTabSkillInfo(skillType: SkyBlockSkill): TabSkillInfo? {
        val lines = TabListApi.lines.map { it.string.removeColor() }
        for (line in lines) {
            skillTabNoPercentPattern.matchEntire(line)?.takeIf { it.group("type") == skillType.displayName }?.let {
                val level = it.group("level").toIntOrNull() ?: return@let null
                val currentXp = it.group("current").formatDoubleOrNull()?.roundToLong() ?: return@let null
                val neededXp = it.group("needed").formatDoubleOrNull()?.roundToLong() ?: return@let null
                return TabSkillInfo(
                    level = level,
                    currentXp = currentXp,
                    neededXp = neededXp,
                )
            }
            skillTabPattern.matchEntire(line)?.takeIf { it.group("type") == skillType.displayName }?.let {
                val level = it.group("level").toIntOrNull() ?: return@let null
                return TabSkillInfo(level = level)
            }
            maxSkillTabPattern.matchEntire(line)?.takeIf { it.group("type") == skillType.displayName }?.let {
                val level = it.group("level").toIntOrNull() ?: return@let null
                return TabSkillInfo(level = level)
            }
        }
        return null
    }

    private fun post(event: SkillExpGain) {
        listeners.forEach { listener ->
            SkysoftErrorBoundary.run(listener.boundary) { listener.callback(event) }
        }
    }

    private fun MatchResult.toMatcher(pattern: Regex): SkillMatcher =
        SkillMatcher(pattern, groups)

    data class SkillExpGain(
        val skill: SkyBlockSkill,
        val gained: Double,
        val totalXp: Double?,
        val previousTotalXp: Double? = null,
        val source: String = ACTIONBAR_SOURCE,
    )

    private data class Listener(val boundary: String, val callback: (SkillExpGain) -> Unit)

    data class SkillInfo(
        @Expose var level: Int = 0,
        @Expose var lastGain: String = "",
        @Expose var totalXp: Long = 0,
        @Expose var currentXp: Long = 0,
        @Expose var currentXpMax: Long = 0,
        @Expose var overflowLevel: Int = 0,
        @Expose var overflowTotalXp: Long = 0,
        @Expose var overflowCurrentXp: Long = 0,
        @Expose var overflowCurrentXpMax: Long = 0,
    ) {
        internal fun update(
            displayed: DisplayedSkillProgress,
            overflow: ParsedSkillLevel,
            gainText: String = lastGain,
        ) {
            level = displayed.level
            totalXp = displayed.totalXp
            currentXp = displayed.currentXp
            currentXpMax = displayed.nextLevelXp
            overflowLevel = overflow.level
            overflowTotalXp = overflow.overflowXp
            overflowCurrentXp = overflow.xpCurrent
            overflowCurrentXpMax = overflow.xpForNext
            lastGain = gainText
        }
    }

    private val skillPercentPattern = Regex("""\+(?<gained>[\d.,]+) (?<skillName>.+) \((?<progress>[\d.,]+)%\)""")
    private val skillMultiplierPattern =
        Regex("""\+(?<gained>[\d.,]+) (?<skillName>.+) \((?<current>[\d.,]+)/(?<needed>[\d,.]+[kmbKMB]?)\)""")
    private val skillMenuProgressPattern = Regex("""[\d,.]+[kmbKMB]?(?:/[\d,.]+[kmbKMB]?)?""")
    private val skillTabPattern = Regex(""" (?<type>\w+)(?: (?<level>\d+))?: (?<progress>[0-9.]+)%""")
    private val maxSkillTabPattern = Regex(""" (?<type>\w+) (?<level>\d+): MAX""")
    private val skillTabNoPercentPattern =
        Regex(""" (?<type>\w+)(?: (?<level>\d+))?: (?<current>[0-9,.]+)/(?<needed>[\d,.]+[kmbKMB]?)""")
    private val jerryBoxSkillXpPattern = Regex(""".*You claimed (?<gained>[\d,]+) (?<skillName>\w+) XP from the Jerry Box!""")
    private val giftSkillXpPattern =
        Regex("""(?:COMMON|RARE|SWEET|SANTA(?: TIER)?|PARTY(?: TIER)?)! \+(?<gained>[\d,]+) (?<skillName>[\w ]+) XP gift with .*!?""")
    private val lilySplosionStartPattern = Regex("""LIL[YI]-SPLOSION!""")
    private val lilySplosionSkillXpPattern = Regex("""\+(?<gained>[\d,]+) (?<skillName>\w+) Experience""")

}

private fun getLevelExact(neededXp: Long, skillType: SkyBlockSkill): Int =
    exactLevelingMap[neededXp.toInt()] ?: skillType.maxLevel

private fun calculateLevelXp(level: Int): Double =
    levelArray.asSequence().take(level + 1).sumOf { it.toDouble() }

private fun xpRequiredForLevel(desiredLevel: Int): Long {
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

private fun calculateSkillLevel(currentXp: Long, maxSkillCap: Int): ParsedSkillLevel {
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

private fun String.romanToDecimalIfNecessary(): Int =
    toIntOrNull() ?: romanToDecimal()

private const val MAX_VANILLA_SKILL_LEVEL = 60
private const val PERCENT_DENOMINATOR = 100.0
private const val DEFAULT_SKILL_XP_TO_NEXT_LEVEL = 7_600_000.0
private const val DEFAULT_CURRENT_LEVEL_XP = 4_000_000L
private const val DEFAULT_NEXT_LEVEL_XP = 4_300_000L
private const val BASE_OVERFLOW_XP_FOR_NEXT_LEVEL = 7_000_000L
private const val OVERFLOW_XP_SLOPE_START = 600_000L
private const val OVERFLOW_SLOPE_DOUBLING_INTERVAL = 10
private const val OVERFLOW_SLOPE_MULTIPLIER = 2

private data class SkillMatcher(
    val pattern: Regex,
    val groups: MatchGroupCollection,
) {
    fun group(name: String): String = groups[name]?.value.orEmpty()
}

internal data class ParsedSkillLevel(
    val level: Int,
    val xpCurrent: Long,
    val xpForNext: Long,
    val overflowXp: Long,
)

private data class TabSkillInfo(
    val level: Int,
    val currentXp: Long? = null,
    val neededXp: Long? = null,
)

private data class LevelConsumption(val level: Int, val remainingXp: Long)

internal data class DisplayedSkillProgress(
    val level: Int,
    val totalXp: Long,
    val currentXp: Long,
    val nextLevelXp: Long,
)

private const val ACTIONBAR_SOURCE = "actionbar"
private const val JERRY_BOX_SOURCE = "chat-jerry-box"
private const val GIFT_SOURCE = "chat-gift"
private const val LILY_SPLOSION_SOURCE = "chat-lily-splosion"
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
