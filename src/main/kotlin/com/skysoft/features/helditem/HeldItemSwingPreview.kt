package com.skysoft.features.helditem

import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand

object HeldItemSwingPreview {
    fun play() {
        val player = Minecraft.getInstance().player ?: return
        player.swing(InteractionHand.MAIN_HAND, false)
    }
}
