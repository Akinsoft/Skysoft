package com.skysoft.utils.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component

class PixelButtonWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: () -> Unit,
    private val isClickSoundEnabled: Boolean = true,
    private val tone: PixelButtonTone = PixelButtonTone.NORMAL,
    private val canReceiveArrowFocus: Boolean = true,
) : Button(
    x,
    y,
    width,
    height,
    message,
    OnPress { onPress() },
    CreateNarration { defaultNarration -> defaultNarration.get() },
) {
    var isSelected = false

    override fun playDownSound(soundManager: SoundManager) {
        if (isClickSoundEnabled) super.playDownSound(soundManager)
    }

    override fun nextFocusPath(navigationEvent: FocusNavigationEvent): ComponentPath? {
        if (!canReceiveArrowFocus && navigationEvent is FocusNavigationEvent.ArrowNavigation) return null
        return super.nextFocusPath(navigationEvent)
    }

    override fun extractContents(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        PixelButtonRenderer.draw(
            context,
            Minecraft.getInstance().font,
            Rect(x, y, width, height),
            message.string,
            isSelected,
            isHovered,
            active,
            tone,
            alpha.toDouble(),
        )
    }
}
