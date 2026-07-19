package com.skysoft.utils

import com.skysoft.mixin.PlayerTabOverlayAccessor
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object TabListOverlay {
    fun readHeader(minecraft: Minecraft): Component? =
        (minecraft.gui.tabList as PlayerTabOverlayAccessor).skysoftGetHeader()

    fun readFooter(minecraft: Minecraft): Component? =
        (minecraft.gui.tabList as PlayerTabOverlayAccessor).skysoftGetFooter()

    fun setVisible(minecraft: Minecraft, isVisible: Boolean) {
        minecraft.gui.tabList.setVisible(isVisible)
    }
}
