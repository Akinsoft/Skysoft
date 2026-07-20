package com.skysoft.data.skyblock

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SidebarScoreboard
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility

object SlayerQuestState {
    private var snapshot = SlayerQuestSnapshot.NONE
    private val minibossNames = mutableSetOf<String>()

    val isActive: Boolean get() = snapshot.bossName != null
    val isBossActive: Boolean get() = snapshot.isBossActive
    val bossName: String? get() = snapshot.bossName

    fun register() {
        ChatEvents.onVisibleMessage(
            "Slayer Quest State chat",
            isActive = { HypixelLocationState.inSkyBlock },
        ) { message ->
            if (message.isSystemLike) {
                SlayerMessageParser.parseMinibossSpawn(message.cleanText)?.let(minibossNames::add)
            }
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Slayer Quest State scoreboard",
            isActive = { HypixelLocationState.inSkyBlock || isActive },
        ) { update() }
        SkysoftClientEvents.onDisconnect("Slayer Quest State disconnect reset", ::clear)
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
    }

    private fun clear() {
        snapshot = SlayerQuestSnapshot.NONE
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

    private val slayerMinibossSpawnPattern = Regex("""^SLAYER MINI-BOSS (?<name>.+?) has spawned!$""")
    private const val SLAYER_BOSS_COCOONED_MESSAGE = "YOU COCOONED YOUR SLAYER BOSS"
}

internal data class SlayerQuestSnapshot(
    val bossName: String?,
    val isBossActive: Boolean,
) {
    val isActive: Boolean get() = bossName != null

    companion object {
        val NONE = SlayerQuestSnapshot(null, false)
    }
}

internal fun parseSlayerQuestSnapshot(scoreboardLines: List<String>): SlayerQuestSnapshot {
    val headerIndex = scoreboardLines.indexOfFirst { it.equals(SLAYER_QUEST_HEADER, ignoreCase = true) }
    if (headerIndex < 0) return SlayerQuestSnapshot.NONE
    val bossName = scoreboardLines.getOrNull(headerIndex + 1)
        ?.let(::slayerBossEntityName)
        ?.takeIf(String::isNotEmpty)
        ?: return SlayerQuestSnapshot.NONE
    return SlayerQuestSnapshot(
        bossName = bossName,
        isBossActive = scoreboardLines.any { it.equals(SLAYER_BOSS_ACTIVE_LINE, ignoreCase = true) },
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
private val SLAYER_TIER_SUFFIX = Regex("""\s+(?:I|II|III|IV|V)$""")
