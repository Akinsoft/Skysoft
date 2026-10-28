package com.skysoft.utils

import net.minecraft.client.Minecraft

object PlayerUtilities {
    fun localPlayerLocation(): WorldVec? = Minecraft.getInstance().player?.location

    fun localPlayerName(): String? = Minecraft.getInstance().player?.gameProfile?.name
}
