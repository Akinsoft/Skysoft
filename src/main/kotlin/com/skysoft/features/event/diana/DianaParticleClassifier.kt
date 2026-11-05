package com.skysoft.features.event.diana

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.WorldVec
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import kotlin.math.abs

internal enum class DianaBurrowParticleKind {
    START,
    MOB,
    TREASURE,
    ENCHANT,
}

internal object DianaParticleClassifier {
    fun classify(event: ClientParticleEvent): DianaBurrowParticleKind? = when {
        event.matches(ParticleTypes.ENCHANTED_HIT, START_PARTICLE_COUNT, FOOTSTEP_SPEED, FOOTSTEP_OFFSET) ->
            DianaBurrowParticleKind.START
        event.matches(ParticleTypes.CRIT, MOB_PARTICLE_COUNT, FOOTSTEP_SPEED, FOOTSTEP_OFFSET) -> DianaBurrowParticleKind.MOB
        event.matches(ParticleTypes.DRIPPING_LAVA, TREASURE_PARTICLE_COUNT, FOOTSTEP_SPEED, TREASURE_OFFSET) ->
            DianaBurrowParticleKind.TREASURE
        event.matches(ParticleTypes.ENCHANT, ENCHANT_PARTICLE_COUNT, ENCHANT_SPEED, ENCHANT_OFFSET) -> DianaBurrowParticleKind.ENCHANT
        else -> null
    }

    fun isSpadeTrail(event: ClientParticleEvent): Boolean =
        event.type == ParticleTypes.DRIPPING_LAVA &&
            event.count == TREASURE_PARTICLE_COUNT &&
            speedMatches(event.speed, SPADE_TRAIL_SPEED)

    fun isArrowParticle(event: ClientParticleEvent): Boolean =
        arrowDistance(event) != null

    fun arrowDistance(event: ClientParticleEvent): DianaArrowDistance? {
        if (event.type != ParticleTypes.DUST || event.count != ARROW_PARTICLE_COUNT || !speedMatches(event.speed, ARROW_PARTICLE_SPEED)) {
            return null
        }
        return DianaArrowDistance.entries.firstOrNull { distance -> event.offset.closeTo(distance.particleOffset) }
    }

    private fun ClientParticleEvent.matches(
        expectedType: ParticleType<*>,
        expectedCount: Int,
        expectedSpeed: Float,
        expectedOffset: WorldVec,
    ): Boolean =
        type == expectedType &&
            count == expectedCount &&
            speedMatches(speed, expectedSpeed) &&
            offset.closeTo(expectedOffset)

    private fun speedMatches(actual: Float, expected: Float): Boolean = abs(actual - expected) <= FLOAT_TOLERANCE

    private fun WorldVec.closeTo(other: WorldVec): Boolean =
        abs(x - other.x) <= OFFSET_TOLERANCE &&
            abs(y - other.y) <= OFFSET_TOLERANCE &&
            abs(z - other.z) <= OFFSET_TOLERANCE

    private val FOOTSTEP_OFFSET = WorldVec(0.5, 0.1, 0.5)
    private val TREASURE_OFFSET = WorldVec(0.35, 0.1, 0.35)
    private val ENCHANT_OFFSET = WorldVec(0.5, 0.4, 0.5)
    private const val START_PARTICLE_COUNT = 4
    private const val MOB_PARTICLE_COUNT = 3
    private const val TREASURE_PARTICLE_COUNT = 2
    private const val ENCHANT_PARTICLE_COUNT = 5
    private const val ARROW_PARTICLE_COUNT = 0
    private const val FOOTSTEP_SPEED = 0.01f
    private const val ENCHANT_SPEED = 0.05f
    private const val SPADE_TRAIL_SPEED = -0.5f
    private const val ARROW_PARTICLE_SPEED = 1.0f
    private const val FLOAT_TOLERANCE = 0.0001f
    private const val OFFSET_TOLERANCE = 0.01
}
