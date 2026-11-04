package com.skysoft.features.fishing

import com.skysoft.utils.WorldVec
import com.skysoft.utils.particle.ParticleEstimateStabilizer
import com.skysoft.utils.particle.ParticlePathEstimator
import com.skysoft.utils.particle.ParticlePathPointResult

internal class FishingHotspotRadarSolver {
    private val estimator = ParticlePathEstimator(minPoints = MIN_RADAR_PARTICLES)
    private val stabilizer = ParticleEstimateStabilizer()

    var guess: WorldVec? = null
        private set

    private var lastRadarUseMillis: Long? = null

    fun begin(now: Long) {
        clear()
        lastRadarUseMillis = now
    }

    fun addParticle(location: WorldVec, now: Long) {
        val radarUse = lastRadarUseMillis ?: return
        if (now < radarUse || now - radarUse > RADAR_PARTICLE_WINDOW_MILLIS) return
        if (estimator.addIfUsable(location) != ParticlePathPointResult.ADDED) return
        if (estimator.count() == 1) stabilizer.reset()
        val estimate = estimator.estimate() ?: return
        guess = stabilizer.accept(estimate) ?: return
    }

    fun clear() {
        estimator.reset()
        stabilizer.reset()
        guess = null
        lastRadarUseMillis = null
    }

    private companion object {
        const val MIN_RADAR_PARTICLES = 4
        const val RADAR_PARTICLE_WINDOW_MILLIS = 1_000L
    }
}
