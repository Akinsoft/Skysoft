package com.skysoft.features.helditem

import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft

object HeldItemSwingPreview {
    fun play() {
        val player = Minecraft.getInstance().player ?: return
        MinecraftClient.swingMainHand(player)
    }
}
