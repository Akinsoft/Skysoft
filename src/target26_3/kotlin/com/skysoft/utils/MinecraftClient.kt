package com.skysoft.utils

import com.mojang.blaze3d.Blaze3D
import com.skysoft.SkysoftMod
import com.skysoft.features.misc.selecteditem.SelectedItemNameState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import org.lwjgl.sdl.SDLClipboard
import org.lwjgl.sdl.SDLError
import org.lwjgl.system.MemoryUtil
import java.net.URI

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
        Blaze3D.openUri(URI.create(url))
    }

    fun swingMainHand(player: LocalPlayer) {
        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
    }

    fun setClipboard(text: String) {
        val buffer = MemoryUtil.memUTF8(text)
        try {
            if (!SDLClipboard.SDL_SetClipboardText(buffer)) {
                SkysoftMod.LOGGER.error("Failed to set clipboard: {}", SDLError.SDL_GetError())
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }
}
