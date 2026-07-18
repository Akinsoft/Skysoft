package com.skysoft.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

internal object SkysoftClientEvents {
    private var endTickListeners: List<EndTickListener> = emptyList()
    private var isEndTickRegistered = false

    fun onEndTick(
        boundary: String,
        isActive: () -> Boolean,
        action: (Minecraft) -> Unit,
    ) {
        endTickListeners += EndTickListener(boundary, isActive, action)
        if (isEndTickRegistered) return
        isEndTickRegistered = true
        ClientTickEvents.END_CLIENT_TICK.register(::dispatchEndTick)
    }

    fun onJoin(boundary: String, action: () -> Unit) {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            SkysoftErrorBoundary.run(boundary, action)
        }
    }

    fun onDisconnect(boundary: String, action: () -> Unit) {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SkysoftErrorBoundary.run(boundary, action)
        }
    }

    fun onClientStarted(boundary: String, action: (Minecraft) -> Unit) {
        ClientLifecycleEvents.CLIENT_STARTED.register { minecraft ->
            SkysoftErrorBoundary.run(boundary) { action(minecraft) }
        }
    }

    fun onClientStopping(boundary: String, action: (Minecraft) -> Unit) {
        ClientLifecycleEvents.CLIENT_STOPPING.register { minecraft ->
            SkysoftErrorBoundary.run(boundary) { action(minecraft) }
        }
    }

    private fun dispatchEndTick(minecraft: Minecraft) {
        endTickListeners.forEach { listener ->
            if (SkysoftErrorBoundary.value("${listener.boundary} activity", false, listener.isActive)) {
                SkysoftErrorBoundary.run(listener.boundary) { listener.action(minecraft) }
            }
        }
    }

    private data class EndTickListener(
        val boundary: String,
        val isActive: () -> Boolean,
        val action: (Minecraft) -> Unit,
    )
}
