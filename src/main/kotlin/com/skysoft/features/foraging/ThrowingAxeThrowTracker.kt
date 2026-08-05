package com.skysoft.features.foraging

import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.utils.WorldVec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.block.Block

internal class ThrowingAxeThrowTracker {
    private val thrownLogs = mutableMapOf<BlockPos, ThrownLog>()
    private val history = ArrayDeque<ThrowSnapshot>()
    private var lastConfirmedThrowTick: Long? = null

    val positions: Set<BlockPos>
        get() = thrownLogs.keys

    val isNotEmpty: Boolean
        get() = thrownLogs.isNotEmpty()

    fun record(recordedTick: Long, firstParticle: WorldVec, blocks: List<BlockPos>, expectedBlock: Block?) {
        removeOldHistory(recordedTick)
        history += ThrowSnapshot(recordedTick, firstParticle, blocks, expectedBlock)
    }

    fun confirm(event: ClientParticleEvent, level: ClientLevel) {
        if (event.type != ParticleTypes.WAX_ON) return
        val currentTick = level.gameTime
        if (lastConfirmedThrowTick?.let { currentTick - it < THROW_CONFIRMATION_INTERVAL_TICKS } == true) return
        removeOldHistory(currentTick)
        val snapshot = history.minByOrNull { it.firstParticle.distanceSq(event.location) } ?: return
        if (snapshot.firstParticle.distanceSq(event.location) > FIRST_PARTICLE_MATCH_DISTANCE_SQUARED) return

        val expiryTick = currentTick + THROWN_LOG_TIMEOUT_TICKS
        snapshot.expectedBlock?.let { expectedBlock ->
            snapshot.blocks.forEach { position ->
                if (level.getBlockState(position).block == expectedBlock) {
                    thrownLogs[position] = ThrownLog(expectedBlock, expiryTick)
                }
            }
        }
        lastConfirmedThrowTick = currentTick
        history.clear()
    }

    fun update(level: ClientLevel, enabled: Boolean) {
        if (!enabled) {
            clear()
            return
        }
        val currentTick = level.gameTime
        thrownLogs.entries.removeIf { (position, log) ->
            currentTick >= log.expiryTick || level.getBlockState(position).block != log.block
        }
    }

    fun clear() {
        thrownLogs.clear()
        history.clear()
        lastConfirmedThrowTick = null
    }

    private fun removeOldHistory(currentTick: Long) {
        while (history.firstOrNull()?.recordedTick?.let { currentTick - it > THROW_HISTORY_TICKS } == true) {
            history.removeFirst()
        }
    }

    private data class ThrownLog(
        val block: Block,
        val expiryTick: Long,
    )

    private data class ThrowSnapshot(
        val recordedTick: Long,
        val firstParticle: WorldVec,
        val blocks: List<BlockPos>,
        val expectedBlock: Block?,
    )

    private companion object {
        const val THROW_HISTORY_TICKS = 20
        const val THROW_CONFIRMATION_INTERVAL_TICKS = 5
        const val FIRST_PARTICLE_MATCH_DISTANCE_SQUARED = 0.5625
        const val THROWN_LOG_TIMEOUT_TICKS = 120
    }
}
