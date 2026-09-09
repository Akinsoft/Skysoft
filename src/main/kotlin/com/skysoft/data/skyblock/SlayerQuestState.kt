package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.romanToDecimal
import com.skysoft.utils.SidebarScoreboardState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object SlayerQuestState {
    private var snapshot = SlayerQuestSnapshot.NONE
    private var lastActiveSnapshot = SlayerQuestSnapshot.NONE
    private val completionListeners = ActiveListenerRegistry<(SlayerQuestSnapshot) -> Unit>()
    private val startListeners = ActiveListenerRegistry<() -> Unit>()
    private val bossSpawnListeners = ActiveListenerRegistry<(SlayerQuestSnapshot) -> Unit>()
    private val minibossNames = mutableSetOf<String>()
    private val recentlyClearedMinibossNames = mutableMapOf<String, ElapsedTimeMark>()

    val isActive: Boolean get() = snapshot.bossName != null
    val isBossActive: Boolean get() = snapshot.isBossActive
    val bossName: String? get() = snapshot.bossName
    val bossNames: Set<String>
        get() = snapshot.slayerType
            ?.let { slayerType -> snapshot.tier?.let(slayerType::bossNames) }
            .orEmpty()
    val slayerType: SkyBlockSlayerType? get() = snapshot.slayerType
    val tier: Int? get() = snapshot.tier
    internal var scoreboardSlayerType: SkyBlockSlayerType? = null
        private set

    fun register() {
        ChatEvents.onVisibleMessage(
            "Slayer Quest State chat",
            isActive = { HypixelLocationState.inSkyBlock },
        ) { message ->
            if (message.isSystemLike) {
                SlayerMessageParser.parseMinibossSpawn(message.cleanText)?.let(minibossNames::add)
                when {
                    SlayerMessageParser.isQuestStarted(message.cleanText) -> {
                        clearQuest()
                        startListeners.forEachActive { it() }
                    }
                    SlayerMessageParser.isQuestComplete(message.cleanText) -> {
                        val completedQuest = snapshot.takeIf(SlayerQuestSnapshot::isActive) ?: lastActiveSnapshot
                        clearQuest()
                        if (completedQuest.isActive) completionListeners.forEachActive { it(completedQuest) }
                    }
                }
            }
            ChatMessageVisibility.SHOW
        }
        SidebarScoreboardState.onChange(
            "Slayer Quest State scoreboard",
            isActive = { HypixelLocationState.inSkyBlock || isActive || scoreboardSlayerType != null },
            listener = ::update,
        )
        SkysoftClientEvents.onDisconnect("Slayer Quest State disconnect reset", ::clear)
    }

    internal fun onQuestStarted(boundary: String, listener: () -> Unit) {
        startListeners.register(boundary, { true }, listener)
    }

    internal fun onQuestComplete(boundary: String, listener: (SlayerQuestSnapshot) -> Unit) {
        completionListeners.register(boundary, { true }, listener)
    }

    internal fun onBossSpawn(boundary: String, listener: (SlayerQuestSnapshot) -> Unit) {
        bossSpawnListeners.register(boundary, { true }, listener)
    }

    fun isSlayerTarget(mobName: String): Boolean =
        bossNames.any { bossName -> bossName.endsWith(mobName, ignoreCase = true) } ||
            minibossNames.any { it.equals(mobName, ignoreCase = true) } ||
            recentlyClearedMinibossNames.any { (name, clearedAt) ->
                name.equals(mobName, ignoreCase = true) &&
                    clearedAt.isWithinMinibossCocoonWindow()
            }

    fun targetNames(): Set<String> = buildSet {
        addAll(bossNames)
        addAll(minibossNames)
    }

    private fun update(lines: List<String>) {
        if (!HypixelLocationState.inSkyBlock) {
            clear()
            return
        }
        val next = parseSlayerQuestSnapshot(lines)
        scoreboardSlayerType = next.slayerType
        recentlyClearedMinibossNames.values.removeIf { !it.isWithinMinibossCocoonWindow() }
        if (!next.isActive || (snapshot.bossName != null && snapshot.bossName != next.bossName)) {
            val clearedAt = ElapsedTimeMark.now()
            minibossNames.forEach { name -> recentlyClearedMinibossNames[name] = clearedAt }
            minibossNames.clear()
        }
        val bossSpawned = snapshot.isActive && !snapshot.isBossActive && next.isBossActive
        snapshot = next
        if (next.isActive) lastActiveSnapshot = next
        if (bossSpawned) bossSpawnListeners.forEachActive { it(next) }
    }

    private fun clear() {
        scoreboardSlayerType = null
        clearQuest()
    }

    private fun clearQuest() {
        snapshot = SlayerQuestSnapshot.NONE
        lastActiveSnapshot = SlayerQuestSnapshot.NONE
        minibossNames.clear()
        recentlyClearedMinibossNames.clear()
    }
}

private fun ElapsedTimeMark.isWithinMinibossCocoonWindow(): Boolean =
    passedSince() in Duration.ZERO..MINIBOSS_COCOON_WINDOW

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

    fun parseAutoSlayerBankCost(message: String): Long? =
        autoSlayerBankPattern.matchEntire(message)
            ?.groups
            ?.get("coins")
            ?.value
            ?.formatDoubleOrNull()
            ?.roundToLong()

    private val slayerMinibossSpawnPattern = Regex("""^SLAYER MINI-BOSS (?<name>.+?) has spawned!$""")
    private val autoSlayerBankPattern = Regex(
        """^Took (?<coins>[\d,.]+[kKmMbB]?) coins from your bank for auto-slayer\.\.\.$""",
    )
    private const val SLAYER_BOSS_COCOONED_MESSAGE = "YOU COCOONED YOUR SLAYER BOSS"
    private const val SLAYER_QUEST_STARTED_MESSAGE = "SLAYER QUEST STARTED!"
    private const val SLAYER_QUEST_COMPLETE_MESSAGE = "SLAYER QUEST COMPLETE!"
}

enum class SkyBlockSlayerType(
    val displayName: String,
    private val bossName: String,
    val bossEntityPrefix: String,
    private val tierFiveBossNames: Set<String> = emptySet(),
) {
    ZOMBIE("Zombie", "Revenant Horror", "REVENANT_HORROR", setOf("Atoned Horror")),
    SPIDER(
        "Spider",
        "Tarantula Broodfather",
        "TARANTULA_BROODFATHER",
        setOf("Tarantula Broodfather", "Conjoined Brood"),
    ),
    WOLF("Wolf", "Sven Packmaster", "SVEN_PACKMASTER"),
    ENDERMAN("Enderman", "Voidgloom Seraph", "VOIDGLOOM_SERAPH"),
    BLAZE("Blaze", "Inferno Demonlord", "INFERNO_DEMONLORD"),
    VAMPIRE("Vampire", "Riftstalker Bloodfiend", "RIFTSTALKER_BLOODFIEND"),
    ;

    fun bossNames(tier: Int): Set<String> =
        tierFiveBossNames.takeIf { tier == TIER_FIVE && it.isNotEmpty() } ?: setOf(bossName)

    companion object {
        fun fromBossEntityId(entityId: String): Pair<SkyBlockSlayerType, Int>? {
            val match = SLAYER_BOSS_ENTITY_PATTERN.matchEntire(entityId) ?: return null
            val type = entries.firstOrNull { it.bossEntityPrefix == match.groupValues[1] } ?: return null
            val tier = match.groupValues[2].toIntOrNull() ?: return null
            return type to tier
        }

        fun fromBossName(name: String): SkyBlockSlayerType? = entries.firstOrNull { slayerType ->
            name.startsWith(slayerType.bossName, ignoreCase = true) ||
                slayerType.tierFiveBossNames.any { bossName -> name.startsWith(bossName, ignoreCase = true) }
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
    val slayerType = SkyBlockSlayerType.fromBossName(questName) ?: return SlayerQuestSnapshot.NONE
    val bossName = slayerType.bossNames(tier).firstOrNull() ?: return SlayerQuestSnapshot.NONE
    return SlayerQuestSnapshot(
        bossName = bossName,
        isBossActive = scoreboardLines.any { it.equals(SLAYER_BOSS_ACTIVE_LINE, ignoreCase = true) },
        slayerType = slayerType,
        tier = tier,
    )
}

private const val SLAYER_QUEST_HEADER = "Slayer Quest"
private const val SLAYER_BOSS_ACTIVE_LINE = "Slay the boss!"
private const val TIER_FIVE = 5
private val MINIBOSS_COCOON_WINDOW = 1_500.milliseconds
private fun slayerTier(romanNumeral: String): Int? =
    romanNumeral.uppercase().romanToDecimal().takeIf { it in 1..TIER_FIVE }

private val SLAYER_TIER_SUFFIX = Regex("""\s+(I|II|III|IV|V)$""")
