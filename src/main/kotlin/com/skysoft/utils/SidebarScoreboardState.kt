package com.skysoft.utils

import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry

object SidebarScoreboardState {
    private val publisher = ActiveStatePublisher("Sidebar Scoreboard State", SidebarScoreboardSnapshot())
    private var dirty = true

    internal val current: SidebarScoreboardSnapshot
        get() = publisher.state

    fun register() {
        publisher.register()
        SkysoftClientEvents.onEndTick(
            "Sidebar scoreboard state",
            isActive = { dirty },
        ) { refresh() }
        SkysoftClientEvents.onJoin("Sidebar scoreboard state join", ::markDirty)
        SkysoftClientEvents.onDisconnect("Sidebar scoreboard state reset", ::reset)
    }

    fun onChange(boundary: String, isActive: () -> Boolean, listener: (List<String>) -> Unit) {
        publisher.onChange(boundary, isActive) { snapshot -> listener(snapshot.lines) }
    }

    internal fun markDirty() {
        dirty = true
    }

    private fun refresh() {
        dirty = false
        publisher.update(SidebarScoreboard.readCurrent())
    }

    private fun reset() {
        dirty = false
        publisher.update(SidebarScoreboardSnapshot())
    }
}

internal data class SidebarScoreboardSnapshot(
    val objective: Objective? = null,
    val entries: List<PlayerScoreEntry> = emptyList(),
    val lines: List<String> = emptyList(),
)
