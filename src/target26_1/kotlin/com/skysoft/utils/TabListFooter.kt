package com.skysoft.utils

import com.skysoft.mixin.PlayerTabOverlayAccessor
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object TabListFooter {
    fun readHeader(minecraft: Minecraft): Component? =
        (minecraft.gui.tabList as PlayerTabOverlayAccessor).skysoftGetHeader()

    fun read(minecraft: Minecraft): Component? =
        (minecraft.gui.tabList as PlayerTabOverlayAccessor).skysoftGetFooter()
}
