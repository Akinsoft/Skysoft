// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkillExperienceLevels.calculateLevelXp
import com.skysoft.data.skyblock.SkillExperienceLevels.calculateSkillLevel
import com.skysoft.data.skyblock.SkillExperienceLevels.getLevelExact
import com.skysoft.data.skyblock.SkillExperienceLevels.xpRequiredForLevel
import com.skysoft.data.skyblock.SkillExperienceLevels.xpForNextLevel
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

object SkillExpGainApi {
    private val storage get() = ProfileStorageApi.storage.skillData
    private val listeners = ActiveListenerRegistry<(SkillExpGain) -> Unit>()
    private var lastLilySplosion = ElapsedTimeMark.farPast()

    fun register() {
        ProfileStorageApi.registerConsumer("Skill Experience API", hasActiveListeners)
        SkillTabProgress.register(hasActiveListeners)
        ChatEvents.onActionBar(
            "Skill Experience action bar",
            isActive = hasActiveListeners,
        ) { message ->
            if (HypixelLocationState.inSkyBlock) handleActionBar(message.component)
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onVisibleMessage(
            "Skill Experience chat",
            isActive = hasActiveListeners,
        ) { message ->
            if (HypixelLocationState.inSkyBlock) handleChat(message.formattedText)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("Skill Experience disconnect reset") {
            lastLilySplosion = ElapsedTimeMark.farPast()
        }
    }

    fun onSkillExpGain(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkillExpGain) -> Unit,
    ) {
        listeners.register(boundary, isActive, listener)
    }

    fun getSkillInfo(skill: SkyBlockSkill): SkyBlockSkillView? = storage[skill]

    internal fun xpRequiredForMaxLevel(skill: SkyBlockSkill): Long = xpRequiredForLevel(skill.maxLevel)

    fun readOpenInventory(inventoryName: String?, inventoryItems: Map<Int, ItemStack>) {
        if (inventoryName != "Your Skills") return
        ProfileStorageApi.updateProfile { profile ->
            val storage = profile.skillData
            for (stack in inventoryItems.values) {
                val lore = stack.loreLines()
                if (lore.none { it.contains("Click to view!") || it.contains("Not unlocked!") }) continue
                val split = stack.formattedHoverName().cleanSkyBlockText().split(" ")
                val skillName = split.firstOrNull() ?: continue
                val skill = SkyBlockSkill.getByNameOrNull(skillName) ?: continue
                val skillLevel = split.getOrNull(1)?.romanToDecimalIfNecessary() ?: 0
                val skillInfo = storage.getOrPut(skill, ::SkillInfo)
                readSkillMenuLore(lore, skill, skillInfo, skillLevel)
            }
        }
    }

    private fun handleActionBar(component: Component) {
        val match = findActionBarGain(component.cleanSkyBlockText()) ?: return
        val skillType = SkyBlockSkill.getByNameOrNull(match.skillName) ?: return
        val gained = match.gainedText.formatDoubleOrNull() ?: return
        ProfileStorageApi.updateProfile { profile ->
            val skillInfo = profile.skillData.getOrPut(skillType, ::SkillInfo)
            val previousTotalXp = skillInfo.totalXp.takeIf { it > 0L }?.toDouble()
            val updated = if (match.percentageText != null) {
                tryHandlePercentActionBar(match, skillType, skillInfo)
            } else {
                tryHandleNumericActionBar(match, skillType, skillInfo)
            }
            if (!updated) return@updateProfile
            post(
                SkillExpGain(
                    skill = skillType,
                    gained = gained,
                    totalXp = skillInfo.totalXp.takeIf { it > 0L }?.toDouble(),
                    previousTotalXp = previousTotalXp,
                    source = ACTIONBAR_SOURCE,
                ),
            )
        }
    }

    internal fun findActionBarGain(text: String): SkillActionBarMatch? =
        skillActionBarPattern.find(text)?.toSkillActionBarMatch()

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
        ProfileStorageApi.updateProfile { profile ->
            profile.skillData.getValue(skillType).recordChatGain(
                parsedLevel = calculateSkillLevel(roundedTotalXp, skillType.maxLevel),
                roundedTotalXp = roundedTotalXp,
                maxLevel = skillType.maxLevel,
                gained = gained,
            )
        }
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
    ) {
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
    }

    private fun tryHandlePercentActionBar(
        match: SkillActionBarMatch,
        skillType: SkyBlockSkill,
        skillInfo: SkillInfo,
    ): Boolean {
        val tabInfo = SkillTabProgress.get(skillType) ?: return false
        val progress = match.percentageText?.formatDoubleOrNull() ?: return false
        val level = tabInfo.level
        if (tabInfo.currentXp != null && tabInfo.neededXp != null) {
            val totalXp = calculateLevelXp(level - 1).toLong() + tabInfo.currentXp
            updateSkillInfo(
                skillInfo,
                skillType.maxLevel,
                level,
                tabInfo.currentXp,
                tabInfo.neededXp,
                totalXp,
                match.gainedText,
            )
            return true
        }
        val levelXp = calculateLevelXp(level - 1)
        val nextLevelDiff = xpForNextLevel(level)
        val currentXp = (nextLevelDiff * progress / PERCENT_DENOMINATOR).toLong()
        val totalXp = (levelXp + currentXp).toLong()
        updateSkillInfo(
            skillInfo,
            skillType.maxLevel,
            level,
            currentXp,
            nextLevelDiff.toLong(),
            totalXp,
            match.gainedText,
        )
        return true
    }

    private fun tryHandleNumericActionBar(
        match: SkillActionBarMatch,
        skillType: SkyBlockSkill,
        skillInfo: SkillInfo,
    ): Boolean {
        val currentXp = match.currentText?.formatDoubleOrNull()?.roundToLong() ?: return false
        val maxXp = match.neededText?.formatDoubleOrNull()?.roundToLong() ?: return false
        val minus = if (maxXp == 0L) 0 else 1
        val level = getLevelExact(maxXp, skillType) - minus
        val totalXp = if (maxXp == 0L) currentXp else calculateLevelXp(level - 1).roundToLong() + currentXp
        updateSkillInfo(skillInfo, skillType.maxLevel, level, currentXp, maxXp, totalXp, match.gainedText)
        return true
    }

    private fun updateSkillInfo(
        skillInfo: SkillInfo,
        maxLevel: Int,
        level: Int,
        currentXp: Long,
        maxXp: Long,
        totalXp: Long,
        gained: String,
    ) {
        val add = maxLevel.takeIf { level >= it }?.let(::xpRequiredForLevel) ?: 0L
        val skillLevel = calculateSkillLevel(totalXp + add, maxLevel)
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

    private fun post(event: SkillExpGain) {
        listeners.forEachActive { listener -> listener(event) }
    }

    private val hasActiveListeners: () -> Boolean = { listeners.hasActiveListeners }

    data class SkillExpGain(
        val skill: SkyBlockSkill,
        val gained: Double,
        val totalXp: Double?,
        val previousTotalXp: Double? = null,
        val source: String = ACTIONBAR_SOURCE,
    ) {
        internal val isFromActionBar: Boolean get() = source == ACTIONBAR_SOURCE
    }

    private fun SkillInfo.update(
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

    private val skillNamePattern = SkyBlockSkill.entries.joinToString("|") { Regex.escape(it.displayName) }
    private val skillActionBarPattern = Regex(
        """\+(?<gained>[\d.,]+) (?<skillName>$skillNamePattern) """ +
            """\((?:(?<progress>[\d.,]+)%|(?<current>[\d.,]+)/(?<needed>[\d,.]+[kmbKMB]?))\)""",
    )
    private val skillMenuProgressPattern = Regex("""[\d,.]+[kmbKMB]?(?:/[\d,.]+[kmbKMB]?)?""")
    private val jerryBoxSkillXpPattern = Regex(""".*You claimed (?<gained>[\d,]+) (?<skillName>\w+) XP from the Jerry Box!""")
    private val giftSkillXpPattern =
        Regex("""(?:COMMON|RARE|SWEET|SANTA(?: TIER)?|PARTY(?: TIER)?)! \+(?<gained>[\d,]+) (?<skillName>[\w ]+) XP gift with .*!?""")
    private val lilySplosionStartPattern = Regex("""LIL[YI]-SPLOSION!""")
    private val lilySplosionSkillXpPattern = Regex("""\+(?<gained>[\d,]+) (?<skillName>\w+) Experience""")

}

private typealias SkillInfo = SkyBlockSkillInfo

private fun String.romanToDecimalIfNecessary(): Int =
    toIntOrNull() ?: romanToDecimal()

private const val PERCENT_DENOMINATOR = 100.0

internal data class SkillActionBarMatch(
    val range: IntRange,
    val skillName: String,
    val gainedText: String,
    val percentageText: String?,
    val currentText: String?,
    val neededText: String?,
)

private fun MatchResult.toSkillActionBarMatch(): SkillActionBarMatch = SkillActionBarMatch(
    range = range,
    skillName = groups["skillName"]?.value.orEmpty(),
    gainedText = groups["gained"]?.value.orEmpty(),
    percentageText = groups["progress"]?.value,
    currentText = groups["current"]?.value,
    neededText = groups["needed"]?.value,
)

private data class DisplayedSkillProgress(
    val level: Int,
    val totalXp: Long,
    val currentXp: Long,
    val nextLevelXp: Long,
)

private const val ACTIONBAR_SOURCE = "actionbar"
private const val JERRY_BOX_SOURCE = "chat-jerry-box"
private const val GIFT_SOURCE = "chat-gift"
private const val LILY_SPLOSION_SOURCE = "chat-lily-splosion"
