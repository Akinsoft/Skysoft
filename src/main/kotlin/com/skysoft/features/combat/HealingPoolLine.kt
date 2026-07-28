package com.skysoft.features.combat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import kotlin.math.abs
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component

object HealingPoolLine {
    private val config get() = SkysoftConfigGui.config().combat.healingPool
    private val detector = HealingPoolDetector()
    private var activePool: WorldVec? = null
    private var lastSeenMillis = 0L

    fun register() {
        ClientParticleEvents.register("Healing Pool particles", ::isEnabled) { event ->
            val now = System.currentTimeMillis()
            detector.detect(event, now)?.let { pool ->
                activePool = pool
                lastSeenMillis = now
            }
            false
        }
        WorldRenderDispatcher.registerHandler(
            "Healing Pool line rendering",
            isActive = { isEnabled() && activePool != null },
            handler = ::renderWorld,
        )
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val pool = activePool?.takeIf {
            System.currentTimeMillis() - lastSeenMillis in 0..POOL_SIGNAL_TIMEOUT_MILLIS
        } ?: run {
            activePool = null
            return
        }
        val color = config.details.color.get().toColor()
        context.drawLineToCrosshair(pool, color)
        if (config.settings.showText) {
            WorldLabelRenderer.draw(
                context,
                pool + TEXT_OFFSET,
                listOf(Component.literal(config.details.text).withStyle(ChatFormatting.BOLD)),
                WorldLabelStyle(textColor = color.rgb),
            )
        }
    }

    private fun isEnabled(): Boolean = config.enabled && SkyBlockIsland.CRIMSON_ISLE.isInIsland()

    private val TEXT_OFFSET = WorldVec(0.0, 1.5, 0.0)
    private const val POOL_SIGNAL_TIMEOUT_MILLIS = 750L
}

internal class HealingPoolDetector {
    private val samples = mutableListOf<TimedPoolParticle>()

    fun detect(
        event: ClientParticleEvent,
        now: Long = System.currentTimeMillis(),
    ): WorldVec? {
        if (!event.isHealingPoolRingParticle()) return null
        samples.removeIf { sample -> now - sample.receivedAtMillis !in 0..RING_WINDOW_MILLIS }
        samples += TimedPoolParticle(event.location, now)
        if (samples.size < RING_PARTICLE_COUNT) return null

        val ring = samples.takeLast(RING_PARTICLE_COUNT)
        val center = ring.fold(WorldVec(0.0, 0.0, 0.0)) { total, sample -> total + sample.location } *
            (1.0 / RING_PARTICLE_COUNT)
        if (ring.any { sample -> !sample.location.isOnPoolRing(center) }) {
            samples.removeAt(0)
            return null
        }

        samples.clear()
        return center
    }

    private fun ClientParticleEvent.isHealingPoolRingParticle(): Boolean =
        type == ParticleTypes.CLOUD &&
            count == 1 &&
            abs(speed) <= VALUE_TOLERANCE &&
            abs(offset.x) <= VALUE_TOLERANCE &&
            abs(offset.y) <= VALUE_TOLERANCE &&
            abs(offset.z) <= VALUE_TOLERANCE &&
            longDistance

    private fun WorldVec.isOnPoolRing(center: WorldVec): Boolean {
        val dx = x - center.x
        val dz = z - center.z
        return abs(y - center.y) <= VALUE_TOLERANCE &&
            abs(dx * dx + dz * dz - RING_RADIUS_SQUARED) <= RING_DISTANCE_TOLERANCE
    }

    private data class TimedPoolParticle(
        val location: WorldVec,
        val receivedAtMillis: Long,
    )

    private companion object {
        const val RING_PARTICLE_COUNT = 12
        const val RING_WINDOW_MILLIS = 100L
        const val RING_RADIUS_SQUARED = 2.25
        const val RING_DISTANCE_TOLERANCE = 0.01
        const val VALUE_TOLERANCE = 0.0001
    }
}
