package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SidebarScoreboard
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SlayerQuestState {
    private var snapshot = SlayerQuestSnapshot.NONE
    private var lastActiveSnapshot = SlayerQuestSnapshot.NONE
    private var completionListeners: List<(SlayerQuestSnapshot) -> Unit> = emptyList()
    private var startListeners: List<() -> Unit> = emptyList()
    private val minibossNames = mutableSetOf<String>()

    val isActive: Boolean get() = snapshot.bossName != null
    val isBossActive: Boolean get() = snapshot.isBossActive
    val bossName: String? get() = snapshot.bossName
    val slayerType: SkyBlockSlayerType? get() = snapshot.slayerType
    val tier: Int? get() = snapshot.tier

    fun register() {
        ChatEvents.onVisibleMessage(
            "Slayer Quest State chat",
            isActive = { HypixelLocationState.inSkyBlock },
        ) { message ->
            if (message.isSystemLike) {
                SlayerMessageParser.parseMinibossSpawn(message.cleanText)?.let(minibossNames::add)
                when {
                    SlayerMessageParser.isQuestStarted(message.cleanText) -> {
                        snapshot = SlayerQuestSnapshot.NONE
                        lastActiveSnapshot = SlayerQuestSnapshot.NONE
                        minibossNames.clear()
                        startListeners.forEach { it() }
                    }
                    SlayerMessageParser.isQuestComplete(message.cleanText) -> {
                        val completedQuest = snapshot.takeIf(SlayerQuestSnapshot::isActive) ?: lastActiveSnapshot
                        snapshot = SlayerQuestSnapshot.NONE
                        lastActiveSnapshot = SlayerQuestSnapshot.NONE
                        minibossNames.clear()
                        if (completedQuest.isActive) completionListeners.forEach { it(completedQuest) }
                    }
                }
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Slayer Quest State scoreboard",
            isActive = { HypixelLocationState.inSkyBlock || isActive },
        ) { update() }
        SkysoftClientEvents.onDisconnect("Slayer Quest State disconnect reset", ::clear)
    }

    internal fun onQuestStarted(listener: () -> Unit) {
        startListeners += listener
    }

    internal fun onQuestComplete(listener: (SlayerQuestSnapshot) -> Unit) {
        completionListeners += listener
    }

    fun isSlayerTarget(mobName: String): Boolean =
        snapshot.bossName?.endsWith(mobName, ignoreCase = true) == true ||
            minibossNames.any { it.equals(mobName, ignoreCase = true) }

    fun targetNames(): Set<String> = buildSet {
        snapshot.bossName?.let(::add)
        addAll(minibossNames)
    }

    private fun update() {
        if (!HypixelLocationState.inSkyBlock) {
            clear()
            return
        }
        val next = parseSlayerQuestSnapshot(SidebarScoreboard.currentLines())
        if (!next.isActive || (snapshot.bossName != null && snapshot.bossName != next.bossName)) {
            minibossNames.clear()
        }
        snapshot = next
        if (next.isActive) lastActiveSnapshot = next
    }

    private fun clear() {
        snapshot = SlayerQuestSnapshot.NONE
        lastActiveSnapshot = SlayerQuestSnapshot.NONE
        minibossNames.clear()
    }
}

object SlayerMessageParser {
    fun parseMinibossSpawn(message: String): String? =
        slayerMinibossSpawnPattern.matchEntire(message)
            ?.groups
            ?.get("name")
            ?.value
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun isBossCocooned(message: String): Boolean = message == SLAYER_BOSS_COCOONED_MESSAGE

    fun isQuestStarted(message: String): Boolean = message.trim() == SLAYER_QUEST_STARTED_MESSAGE

    fun isQuestComplete(message: String): Boolean = message.trim() == SLAYER_QUEST_COMPLETE_MESSAGE

    private val slayerMinibossSpawnPattern = Regex("""^SLAYER MINI-BOSS (?<name>.+?) has spawned!$""")
    private const val SLAYER_BOSS_COCOONED_MESSAGE = "YOU COCOONED YOUR SLAYER BOSS"
    private const val SLAYER_QUEST_STARTED_MESSAGE = "SLAYER QUEST STARTED!"
    private const val SLAYER_QUEST_COMPLETE_MESSAGE = "SLAYER QUEST COMPLETE!"
}

enum class SkyBlockSlayerType(
    val displayName: String,
    val bossEntityPrefix: String,
    val questCosts: List<Long>,
    val costCurrency: String = "Coins",
) {
    ZOMBIE("Zombie", "REVENANT_HORROR", listOf(2_000, 7_500, 20_000, 50_000, 100_000)),
    SPIDER("Spider", "TARANTULA_BROODFATHER", listOf(2_000, 7_500, 20_000, 50_000, 100_000)),
    WOLF("Wolf", "SVEN_PACKMASTER", listOf(2_000, 7_500, 20_000, 50_000)),
    ENDERMAN("Enderman", "VOIDGLOOM_SERAPH", listOf(2_000, 7_500, 20_000, 50_000)),
    BLAZE("Blaze", "INFERNO_DEMONLORD", listOf(10_000, 25_000, 60_000, 150_000)),
    VAMPIRE("Vampire", "RIFTSTALKER_BLOODFIEND", listOf(2_000, 4_000, 5_000, 7_000, 10_000), "Motes"),
    ;

    fun questCost(tier: Int): Long? = questCosts.getOrNull(tier - 1)

    fun bossEntityId(tier: Int): String = "${bossEntityPrefix}_${tier}_BOSS"

    companion object {
        fun fromBossEntityId(entityId: String): Pair<SkyBlockSlayerType, Int>? {
            val match = SLAYER_BOSS_ENTITY_PATTERN.matchEntire(entityId) ?: return null
            val type = entries.firstOrNull { it.bossEntityPrefix == match.groupValues[1] } ?: return null
            val tier = match.groupValues[2].toIntOrNull() ?: return null
            return type to tier
        }

        fun fromBossName(name: String): SkyBlockSlayerType? = when {
            name.startsWith("Revenant Horror", ignoreCase = true) || name.equals("Atoned Horror", true) -> ZOMBIE
            name.startsWith("Tarantula Broodfather", ignoreCase = true) -> SPIDER
            name.startsWith("Sven Packmaster", ignoreCase = true) -> WOLF
            name.startsWith("Voidgloom Seraph", ignoreCase = true) -> ENDERMAN
            name.startsWith("Inferno Demonlord", ignoreCase = true) -> BLAZE
            name.startsWith("Riftstalker Bloodfiend", ignoreCase = true) -> VAMPIRE
            else -> null
        }

        private val SLAYER_BOSS_ENTITY_PATTERN = Regex(
            "(REVENANT_HORROR|TARANTULA_BROODFATHER|SVEN_PACKMASTER|VOIDGLOOM_SERAPH|" +
                "INFERNO_DEMONLORD|RIFTSTALKER_BLOODFIEND)_([1-5])_BOSS",
        )
    }
}

internal data class SlayerQuestSnapshot(
    val bossName: String?,
    val isBossActive: Boolean,
    val slayerType: SkyBlockSlayerType? = null,
    val tier: Int? = null,
) {
    val isActive: Boolean get() = bossName != null

    companion object {
        val NONE = SlayerQuestSnapshot(null, false)
    }
}

internal fun parseSlayerQuestSnapshot(scoreboardLines: List<String>): SlayerQuestSnapshot {
    val headerIndex = scoreboardLines.indexOfFirst { it.equals(SLAYER_QUEST_HEADER, ignoreCase = true) }
    if (headerIndex < 0) return SlayerQuestSnapshot.NONE
    val questName = scoreboardLines.getOrNull(headerIndex + 1)?.trim().orEmpty()
    val tier = SLAYER_TIER_SUFFIX.find(questName)?.groupValues?.get(1)?.let(::slayerTier)
        ?: return SlayerQuestSnapshot.NONE
    val bossName = slayerBossEntityName(questName).takeIf(String::isNotEmpty) ?: return SlayerQuestSnapshot.NONE
    val slayerType = SkyBlockSlayerType.fromBossName(questName) ?: return SlayerQuestSnapshot.NONE
    return SlayerQuestSnapshot(
        bossName = bossName,
        isBossActive = scoreboardLines.any { it.equals(SLAYER_BOSS_ACTIVE_LINE, ignoreCase = true) },
        slayerType = slayerType,
        tier = tier,
    )
}

internal fun slayerBossEntityName(scoreboardBossName: String): String {
    val trimmedName = scoreboardBossName.trim()
    return if (trimmedName.equals(ATONED_HORROR_QUEST_NAME, ignoreCase = true)) {
        ATONED_HORROR_ENTITY_NAME
    } else {
        trimmedName.replace(SLAYER_TIER_SUFFIX, "")
    }
}

private const val SLAYER_QUEST_HEADER = "Slayer Quest"
private const val SLAYER_BOSS_ACTIVE_LINE = "Slay the boss!"
private const val ATONED_HORROR_QUEST_NAME = "Revenant Horror V"
private const val ATONED_HORROR_ENTITY_NAME = "Atoned Horror"
private fun slayerTier(romanNumeral: String): Int? =
    SLAYER_TIER_NUMERALS.indexOf(romanNumeral.uppercase()).takeIf { it >= 0 }?.inc()

private val SLAYER_TIER_NUMERALS = listOf("I", "II", "III", "IV", "V")

private val SLAYER_TIER_SUFFIX = Regex("""\s+(I|II|III|IV|V)$""")
