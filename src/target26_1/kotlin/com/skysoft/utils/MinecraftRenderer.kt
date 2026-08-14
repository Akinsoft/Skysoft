package com.skysoft.utils

import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.state.WindowRenderState

object MinecraftRenderer {
    fun mainCamera(renderer: GameRenderer): Camera = renderer.mainCamera

    fun lighting(renderer: GameRenderer): Lighting = renderer.lighting

    fun windowRenderState(renderer: GameRenderer): WindowRenderState = renderer.gameRenderState.windowRenderState

    fun featureRenderDispatcher(renderer: GameRenderer): FeatureRenderDispatcher = renderer.featureRenderDispatcher

    fun invalidateCompiledGeometry(minecraft: Minecraft) {
        minecraft.levelRenderer.allChanged()
    }
}
