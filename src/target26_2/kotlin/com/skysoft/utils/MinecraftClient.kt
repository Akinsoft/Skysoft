package com.skysoft.utils

import com.skysoft.features.misc.selecteditem.SelectedItemNameState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Util
import net.minecraft.world.InteractionHand

object MinecraftClient {
    fun screen(): Screen? = screen(Minecraft.getInstance())

    fun screen(minecraft: Minecraft): Screen? = minecraft.gui.screen()

    fun setScreen(screen: Screen?) = Minecraft.getInstance().gui.setScreen(screen)

    fun isGuiHidden(minecraft: Minecraft): Boolean = minecraft.gui.hud.isHidden

    fun chat(minecraft: Minecraft): ChatComponent = minecraft.gui.hud.chat

    fun guiTicks(minecraft: Minecraft): Int = minecraft.gui.hud.guiTicks

    fun selectedItemNameState(minecraft: Minecraft): SelectedItemNameState =
        minecraft.gui.hud as SelectedItemNameState

    fun openUri(url: String) {
        Util.getPlatform().openUri(url)
    }

    fun swingMainHand(player: LocalPlayer) {
        player.swing(InteractionHand.MAIN_HAND, false)
    }

    fun setClipboard(text: String) {
        Minecraft.getInstance().keyboardHandler.clipboard = text
    }
}
