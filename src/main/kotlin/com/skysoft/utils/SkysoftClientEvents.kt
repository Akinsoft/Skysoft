package com.skysoft.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

internal object SkysoftClientEvents {
    fun onEndTick(boundary: String, action: (Minecraft) -> Unit) {
        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            SkysoftErrorBoundary.run(boundary) { action(minecraft) }
        }
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
}
