package com.skysoft.features.event.diana

import com.skysoft.data.InteractionClick
import com.skysoft.events.input.ItemUseEvent
import com.skysoft.events.particle.ClientParticleEvent
import com.skysoft.features.event.diana.DianaEventState.isDianaSpade
import com.skysoft.utils.WorldVec
import com.skysoft.utils.particle.ParticleEstimateStabilizer
import com.skysoft.utils.particle.ParticlePathEstimator
import com.skysoft.utils.particle.ParticlePathPointResult

internal object DianaSpadeGuess {
    private val estimator = ParticlePathEstimator()
    private val stabilizer = ParticleEstimateStabilizer()
    private var lastSpadeUseMillis = 0L
    private var activeGuess: DianaBurrowTarget? = null

    fun handleItemUse(event: ItemUseEvent) {
        if (event.clickType != InteractionClick.RIGHT_CLICK || !event.itemInHand.isDianaSpade()) return
        estimator.reset()
        stabilizer.reset()
        activeGuess = null
        lastSpadeUseMillis = System.currentTimeMillis()
    }

    fun handleParticle(
        event: ClientParticleEvent,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!DianaParticleClassifier.isSpadeTrail(event)) return
        if (now - lastSpadeUseMillis > SPADE_TRAIL_WINDOW_MILLIS) return
        if (estimator.addIfUsable(event.location) != ParticlePathPointResult.ADDED) return
        if (estimator.count() == 1) stabilizer.reset()
        val estimate = estimator.estimate() ?: return
        val guessLocation = stabilizer.accept(estimate.down(BURROW_GUESS_Y_OFFSET)) ?: return
        if (!dianaHubBounds.contains(guessLocation.roundToBlock())) return
        if (DianaBurrowSurfaceValidator.check(guessLocation) == DianaBurrowSurfaceStatus.INVALID) return
        val replacement = activeGuess ?: DianaArrowGuess.recentGuessForReplacement(guessLocation, now)
        val tracked = DianaBurrowTargetTracker.trackGuess(guessLocation, now, replacing = replacement)
        DianaBurrowChainState.onTargetReplaced(replacement, tracked, now)
        if (replacement != null && replacement != activeGuess) DianaArrowGuess.clearActiveGuess(replacement)
        activeGuess = tracked?.takeIf { it.source == DianaBurrowSource.GUESS }
    }

    fun onDetectedBurrow(location: WorldVec, now: Long = System.currentTimeMillis()) {
        val guess = activeGuess ?: return
        val removed = DianaBurrowTargetTracker.removeGuessIfCurrentNear(
            target = guess,
            location = location,
            maxDistance = SPADE_CONFIRM_RADIUS,
            now = now,
        )
        val current = DianaBurrowTargetTracker.targetAt(guess.location)
        if (removed != null || current?.targetId != guess.targetId || current.source != DianaBurrowSource.GUESS) {
            activeGuess = null
        }
    }

    fun clear() {
        estimator.reset()
        stabilizer.reset()
        lastSpadeUseMillis = 0L
        activeGuess = null
    }

    private const val SPADE_TRAIL_WINDOW_MILLIS = 3_000L
    private const val SPADE_CONFIRM_RADIUS = 8.0
}

private const val BURROW_GUESS_Y_OFFSET = 0.5
