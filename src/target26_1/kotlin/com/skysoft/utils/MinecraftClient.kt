package com.skysoft.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen

object MinecraftClient {
    fun screen(): Screen? = screen(Minecraft.getInstance())

    fun screen(minecraft: Minecraft): Screen? = minecraft.screen

    fun setScreen(screen: Screen?) = Minecraft.getInstance().setScreen(screen)

    fun isGuiHidden(minecraft: Minecraft): Boolean = minecraft.options.hideGui

    fun chat(minecraft: Minecraft): ChatComponent = minecraft.gui.chat

    fun guiTicks(minecraft: Minecraft): Int = minecraft.gui.guiTicks
}
