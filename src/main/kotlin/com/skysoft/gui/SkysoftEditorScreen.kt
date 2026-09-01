package com.skysoft.gui

import com.skysoft.utils.MinecraftClient
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class SkysoftEditorScreen(
    title: Component,
    private val returnScreen: Screen?,
) : Screen(title) {
    final override fun onClose() {
        beforeEditorClose()
        if (returnScreen == null) {
            super.onClose()
        } else {
            MinecraftClient.setScreen(returnScreen)
        }
    }

    protected abstract fun beforeEditorClose()
}
