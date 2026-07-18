package com.skysoft.features.misc

import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft

internal class ServerTpsService(private val estimator: ServerTpsEstimator = ServerTpsEstimator()) {
    private val registeredConsumers = linkedSetOf<String>()
    private val activeConsumers = linkedSetOf<String>()

    val hasActiveConsumers: Boolean
        get() = activeConsumers.isNotEmpty()

    val tps: Double?
        get() = estimator.tps

    fun registerConsumer(id: String) {
        check(registeredConsumers.add(id)) { "TPS consumer is already registered: $id" }
    }

    fun updateConsumerState(id: String, isActive: Boolean) {
        check(id in registeredConsumers) { "TPS consumer is not registered: $id" }
        val hadActiveConsumers = hasActiveConsumers
        if (isActive) activeConsumers.add(id) else activeConsumers.remove(id)
        if (hadActiveConsumers != hasActiveConsumers) estimator.reset()
    }

    fun recordTimeUpdate(gameTime: Long, timestampNanos: Long, targetTps: Double): TpsSampleResult? {
        if (!hasActiveConsumers) return null
        return estimator.recordTimeUpdate(gameTime, timestampNanos, targetTps)
    }

    fun resetMeasurements() {
        estimator.reset()
    }

    fun snapshot(): ServerTpsProviderSnapshot = ServerTpsProviderSnapshot(
        registeredConsumers = registeredConsumers.toList(),
        activeConsumers = activeConsumers.toList(),
        estimator = estimator.snapshot(),
    )
}

internal object ServerTpsProvider {
    private val service = ServerTpsService()

    val tps: Double?
        get() = service.tps

    fun register() {
        SkysoftClientEvents.onJoin("Server TPS join reset", service::resetMeasurements)
        SkysoftClientEvents.onDisconnect("Server TPS disconnect reset", service::resetMeasurements)
    }

    fun registerConsumer(id: String) {
        service.registerConsumer(id)
    }

    fun updateConsumerState(id: String, isActive: Boolean) {
        service.updateConsumerState(id, isActive)
    }

    fun recordServerTime(gameTime: Long, timestampNanos: Long): TpsSampleResult? {
        if (!service.hasActiveConsumers) return null
        val targetTps = Minecraft.getInstance().level?.tickRateManager()?.tickrate()?.toDouble() ?: Double.NaN
        return service.recordTimeUpdate(gameTime, timestampNanos, targetTps)
    }

    fun snapshot(): ServerTpsProviderSnapshot = service.snapshot()
}

internal data class ServerTpsProviderSnapshot(
    val registeredConsumers: List<String>,
    val activeConsumers: List<String>,
    val estimator: ServerTpsSnapshot,
)
