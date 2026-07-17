package com.skysoft.features.fishing

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.InteractionClick
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.events.input.ItemUseEvent
import com.skysoft.events.input.ItemUseEvents
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

object FishingHotspotRadar {
    private val config get() = SkysoftConfigGui.config().fishing.hotspotRadar
    private val solver = FishingHotspotRadarSolver()
    private var lastTrailParticleMillis = 0L
    private var activeLevel: ClientLevel? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clearSession() }
        ClientParticleEvents.EVENT.register { event ->
            handleParticle(event)
            false
        }
        ItemUseEvents.EVENT.register { event -> processItemUse(event).shouldCancel }
        WorldRenderDispatcher.registerHandler(::onRenderWorld)
    }

    private fun onTick() {
        val level = Minecraft.getInstance().level
        if (level !== activeLevel) {
            activeLevel = level
            clear()
        }
        if (!isEnabled()) {
            clear()
            return
        }
        val guess = solver.guess ?: return
        val playerLocation = FishingHotspotDetector.playerLocation() ?: return
        if (shouldClearFishingHotspotRadarGuess(guess, playerLocation)) {
            solver.clear()
        }
    }

    private fun handleParticle(event: ClientParticleEvent) {
        if (!isEnabled() || !FishingHotspotRadarParticleClassifier.isTrail(event)) return
        val now = System.currentTimeMillis()
        lastTrailParticleMillis = now
        solver.addParticle(event.location, now)
    }

    private fun processItemUse(event: ItemUseEvent): RadarUseResult {
        if (!isEnabled() || event.clickType != InteractionClick.RIGHT_CLICK) return RadarUseResult.IGNORED
        if (event.itemInHand?.skyBlockId() != HOTSPOT_RADAR_ITEM_ID) return RadarUseResult.IGNORED
        val now = System.currentTimeMillis()
        if (now - lastTrailParticleMillis in 0..RADAR_REUSE_GUARD_MILLIS) return RadarUseResult.BLOCKED
        solver.begin(now)
        return RadarUseResult.STARTED
    }

    private fun onRenderWorld(context: SkysoftRenderContext) {
        if (!isEnabled()) return
        val guess = solver.guess ?: return
        FishingHotspotRadarRenderer.renderWorld(context, guess, config.details.crosshairLine)
    }

    private fun isEnabled(): Boolean =
        config.enabled &&
            HypixelLocationState.inSkyBlock &&
            HypixelLocationState.currentIsland in FISHING_HOTSPOT_ISLANDS

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

    private enum class RadarUseResult(val shouldCancel: Boolean) {
        IGNORED(false),
        STARTED(false),
        BLOCKED(true),
    }
}

internal fun shouldClearFishingHotspotRadarGuess(
    guess: FishingHotspotRadarGuess,
    playerLocation: WorldVec,
): Boolean =
    playerLocation.distance(guess.location.blockCenter()) <= FISHING_HOTSPOT_RADAR_ARRIVAL_DISTANCE

private const val FISHING_HOTSPOT_RADAR_ARRIVAL_DISTANCE = 10.0
