package com.skysoft.utils.render

import com.skysoft.utils.ActiveListenerRegistry
import com.skysoft.utils.MinecraftRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.state.level.CameraRenderState

object WorldRenderDispatcher {
    private val handlers = ActiveListenerRegistry<(SkysoftRenderContext) -> Unit>()

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            SkysoftErrorBoundary.run("World render setup") {
                if (!handlers.hasActiveListeners) return@run
                val minecraft = Minecraft.getInstance()
                val partialTicks = partialTicks()
                val camera = MinecraftRenderer.mainCamera(minecraft.gameRenderer)
                val cameraRenderState = CameraRenderState()
                camera.extractRenderState(cameraRenderState, partialTicks)
                val skysoftContext = SkysoftRenderContext(
                    context.poseStack(),
                    context.submitNodeCollector(),
                    partialTicks,
                    camera,
                    cameraRenderState,
                )
                handlers.forEachActive { handler -> handler(skysoftContext) }
            }
        }
    }

    fun registerHandler(
        boundary: String,
        isActive: () -> Boolean,
        handler: (SkysoftRenderContext) -> Unit,
    ) {
        handlers.register(boundary, isActive, handler)
    }

    private fun partialTicks(): Float =
        Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)

}
