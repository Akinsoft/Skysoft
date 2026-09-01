package com.skysoft.utils

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

internal object SkysoftScreenEvents {
    private val beforeInitListeners = ActiveListenerRegistry<(Minecraft, Screen) -> Unit>()
    private var registered = false

    fun onBeforeInit(
        boundary: String,
        isActive: () -> Boolean = { true },
        listener: (Minecraft, Screen) -> Unit,
    ) {
        register()
        beforeInitListeners.register(boundary, isActive, listener)
    }

    private fun register() {
        if (registered) return
        registered = true
        ScreenEvents.BEFORE_INIT.register { minecraft, screen, _, _ ->
            beforeInitListeners.forEachActive { listener -> listener(minecraft, screen) }
        }
    }
}
