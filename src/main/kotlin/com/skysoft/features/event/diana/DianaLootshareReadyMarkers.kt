package com.skysoft.features.event.diana

import com.skysoft.utils.render.EntityLabelRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelStyle
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor

internal object DianaLootshareReadyMarkers {
    fun mark(playerName: String, now: Long) {
        readyPlayers[playerName.lowercase()] = now + MARKER_LIFETIME_MILLIS
    }

    fun clear() {
        readyPlayers.clear()
    }

    fun renderWorld(
        context: SkysoftRenderContext,
        localPlayerName: String?,
        spawnerNames: Set<String>,
        now: Long,
    ) {
        val checkmarks = checkmarksByPlayer(spawnerNames, now)
        if (checkmarks.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        level.players()
            .filterNot { player -> player.gameProfile.name.equals(localPlayerName, ignoreCase = true) }
            .forEach { player ->
                val marker = checkmarks[player.gameProfile.name.lowercase()] ?: return@forEach
                EntityLabelRenderer.drawAboveNameTag(
                    context,
                    player,
                    listOf(marker),
                    CHECKMARK_STYLE,
                )
            }
    }

    fun checkmarksByPlayer(spawnerNames: Set<String>, now: Long): Map<String, Component> {
        readyPlayers.values.removeIf { expiresAtMillis -> now >= expiresAtMillis }
        val checkmarks = readyPlayers.keys.associateWithTo(mutableMapOf<String, Component>()) { checkmark }
        spawnerNames.forEach { name -> checkmarks[name.lowercase()] = spawnerCheckmark }
        return checkmarks
    }

    private val readyPlayers = mutableMapOf<String, Long>()
    val checkmark: Component = checkmark(LOOTSHARE_READY_COLOR)
    val spawnerCheckmark: Component = checkmark(SPAWNER_COLOR)
    private val CHECKMARK_STYLE = WorldLabelStyle(maxRenderDistance = 80.0, maxScale = 6.0)
    private const val LOOTSHARE_READY_COLOR = 0x55FFFF
    private const val SPAWNER_COLOR = 0xFF55FF
    private const val MARKER_LIFETIME_MILLIS = 75_000L

    private fun checkmark(color: Int): Component = Component.literal("✓").withStyle { style ->
        style.withColor(TextColor.fromRgb(color)).withBold(true)
    }
}
