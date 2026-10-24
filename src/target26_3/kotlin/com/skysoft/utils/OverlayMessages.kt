package com.skysoft.utils

import com.skysoft.mixin.OverlayMessageAccessor
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object OverlayMessages {
    fun message(minecraft: Minecraft): Component? =
        accessor(minecraft).skysoftGetOverlayMessageString()

    fun time(minecraft: Minecraft): Int =
        accessor(minecraft).skysoftGetOverlayMessageTime()

    private fun accessor(minecraft: Minecraft): OverlayMessageAccessor =
        minecraft.gui.hud as OverlayMessageAccessor
}
