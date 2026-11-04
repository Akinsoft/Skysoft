package com.skysoft.features.fishing

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.InteractionClick
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.events.input.ItemUseEvent
import com.skysoft.events.input.ItemUseEvents
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.PlayerUtilities.localPlayerLocation
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.SkysoftClientEvents
import kotlin.math.abs
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleTypes

object FishingHotspotRadar {
    private val config get() = SkysoftConfigGui.config().fishing.hotspotRadar
    private val solver = FishingHotspotRadarSolver()
    private var lastTrailParticleMillis = 0L
    private var activeLevel: ClientLevel? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Fishing Hotspot Radar tick",
            isActive = { isEnabled() || activeLevel != null },
        ) { onTick() }
        SkysoftClientEvents.onDisconnect("Fishing Hotspot Radar disconnect reset", ::clearSession)
        ClientParticleEvents.register("Fishing Hotspot particles", ::isEnabled) { event ->
            handleParticle(event)
            false
        }
        ItemUseEvents.register("Fishing Hotspot item use", ::isEnabled, ::shouldCancelItemUse)
        WorldRenderDispatcher.registerHandler("Fishing Hotspot radar rendering", ::isEnabled, ::onRenderWorld)
    }

    private fun onTick() {
        val level = Minecraft.getInstance().level
        if (level !== activeLevel) {
            activeLevel = level
            clear()
        }
        if (!isEnabled()) {
            clearSession()
            return
        }
        val guess = solver.guess ?: return
        val playerLocation = localPlayerLocation() ?: return
        if (playerLocation.distance(guess.blockCenter()) <= ARRIVAL_DISTANCE) solver.clear()
    }

    private fun handleParticle(event: ClientParticleEvent) {
        if (!isTrailParticle(event)) return
        val now = System.currentTimeMillis()
        lastTrailParticleMillis = now
        solver.addParticle(event.location, now)
    }

    private fun shouldCancelItemUse(event: ItemUseEvent): Boolean {
        if (event.clickType != InteractionClick.RIGHT_CLICK) return false
        if (event.itemInHand?.skyBlockId() != HOTSPOT_RADAR_ITEM_ID) return false
        val now = System.currentTimeMillis()
        if (now - lastTrailParticleMillis in 0..RADAR_REUSE_GUARD_MILLIS) return true
        solver.begin(now)
        return false
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        val guess = solver.guess ?: return
        FishingHotspotRadarRenderer.renderWorld(context, guess, config.details.crosshairLine)
    }

    private fun isTrailParticle(event: ClientParticleEvent): Boolean =
        event.type == ParticleTypes.FLAME && event.count == TRAIL_PARTICLE_COUNT && abs(event.speed) <= TRAIL_SPEED_TOLERANCE

    private fun isEnabled(): Boolean =
        config.enabled && isOnFishingHotspotIsland()

    private fun clear() {
        solver.clear()
        lastTrailParticleMillis = 0L
    }

    private fun clearSession() {
        clear()
        activeLevel = null
    }

    private const val HOTSPOT_RADAR_ITEM_ID = "HOTSPOT_RADAR"
    private const val RADAR_REUSE_GUARD_MILLIS = 200L
    private const val ARRIVAL_DISTANCE = 10.0
    private const val TRAIL_PARTICLE_COUNT = 1
    private const val TRAIL_SPEED_TOLERANCE = 0.0001f
}
