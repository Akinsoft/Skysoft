package com.skysoft.features.inventory.sacks

import com.skysoft.utils.input.InputHandlingResult
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

internal object SackHudInput {
    @JvmStatic
    fun handleKeyPress(event: KeyEvent): InputHandlingResult =
        InputHandlingResult.from(sackHudConfig.enabled && sackHudItemPanel.wasKeyPressHandled(event))

    @JvmStatic
    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        InputHandlingResult.from(sackHudConfig.enabled && sackHudItemPanel.wasCharTypedHandled(event))
}
