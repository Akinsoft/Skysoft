package com.skysoft.features.misc

import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft

internal object ServerTpsProvider {
    private val estimator = ServerTpsEstimator()
    private val registeredConsumers = linkedSetOf<String>()
    private val activeConsumers = linkedSetOf<String>()

    val hasActiveConsumers: Boolean
        get() = activeConsumers.isNotEmpty()

    val tps: Double?
        get() = estimator.tps

    fun register() {
        SkysoftClientEvents.onJoin("Server TPS join reset", estimator::reset)
        SkysoftClientEvents.onDisconnect("Server TPS disconnect reset", estimator::reset)
    }

    fun registerConsumer(id: String) {
        check(registeredConsumers.add(id)) { "TPS consumer is already registered: $id" }
    }

    fun updateConsumerState(id: String, isActive: Boolean) {
        check(id in registeredConsumers) { "TPS consumer is not registered: $id" }
        val hadActiveConsumers = hasActiveConsumers
        if (isActive) activeConsumers.add(id) else activeConsumers.remove(id)
        if (hadActiveConsumers != hasActiveConsumers) estimator.reset()
    }

    fun recordServerTime(gameTime: Long, timestampNanos: Long) {
        if (!hasActiveConsumers) return
        val targetTps = Minecraft.getInstance().level?.tickRateManager()?.tickrate()?.toDouble() ?: Double.NaN
        estimator.recordTimeUpdate(gameTime, timestampNanos, targetTps)
    }
}
