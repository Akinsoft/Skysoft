package com.skysoft.data

import com.skysoft.utils.SkysoftClientEvents
import java.util.Collections
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity

object ClientEntitySnapshot {
    private var level: ClientLevel? = null
    private var gameTime = Long.MIN_VALUE
    private var cachedEntities: List<Entity> = emptyList()

    fun register() {
        SkysoftClientEvents.onDisconnect("Client entity snapshot reset", ::reset)
    }

    fun entities(): List<Entity> {
        refresh(Minecraft.getInstance())
        return cachedEntities
    }

    private fun refresh(minecraft: Minecraft) {
        val currentLevel = minecraft.level
        if (currentLevel == null) {
            reset()
            return
        }
        if (level === currentLevel && gameTime == currentLevel.gameTime) return
        level = currentLevel
        gameTime = currentLevel.gameTime
        cachedEntities = Collections.unmodifiableList(currentLevel.entitiesForRendering().toList())
    }

    private fun reset() {
        level = null
        gameTime = Long.MIN_VALUE
        cachedEntities = emptyList()
    }
}
