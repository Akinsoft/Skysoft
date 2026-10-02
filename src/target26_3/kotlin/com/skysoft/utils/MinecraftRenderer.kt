package com.skysoft.utils

import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.state.WindowRenderState

object MinecraftRenderer {
    fun mainCamera(renderer: GameRenderer): Camera = renderer.mainCamera()

    fun lighting(renderer: GameRenderer): Lighting = renderer.lighting()

    fun windowRenderState(renderer: GameRenderer): WindowRenderState = renderer.gameRenderState().windowRenderState

    fun invalidateCompiledGeometry(minecraft: Minecraft) {
        val level = minecraft.level ?: return
        minecraft.levelRenderer.invalidateCompiledGeometry(
            level,
            minecraft.options,
            minecraft.gameRenderer.mainCamera(),
            minecraft.blockColors,
        )
    }
}
