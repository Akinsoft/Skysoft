package com.skysoft.features.inventory.sacks

import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

internal object SackHudInput {
    @JvmStatic
    fun handleKeyPress(event: KeyEvent): InputHandlingResult =
        if (sackHudConfig.enabled && sackHudItemPanel.wasKeyPressHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }

    @JvmStatic
    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        if (sackHudConfig.enabled && sackHudItemPanel.wasCharTypedHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
}
