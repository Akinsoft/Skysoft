package com.skysoft.features.helditem

import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand

object HeldItemSwingPreview {
    fun play() {
        val player = Minecraft.getInstance().player ?: return
        play(player::swing)
    }

    internal fun play(startSwing: (InteractionHand, Boolean) -> Unit) {
        startSwing(InteractionHand.MAIN_HAND, false)
    }
}
