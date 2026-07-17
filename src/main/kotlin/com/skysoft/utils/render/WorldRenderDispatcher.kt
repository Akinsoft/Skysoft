package com.skysoft.utils.render

import com.skysoft.utils.MinecraftRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.state.level.CameraRenderState

object WorldRenderDispatcher {
    private var handlers: List<Handler> = emptyList()

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            SkysoftErrorBoundary.run("World render setup") {
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
                handlers.forEach { handler ->
                    SkysoftErrorBoundary.run(handler.boundary) { handler.render(skysoftContext) }
                }
            }
        }
    }

    fun registerHandler(boundary: String, handler: (SkysoftRenderContext) -> Unit) {
        handlers += Handler(boundary, handler)
    }

    private fun partialTicks(): Float =
        Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)

    private data class Handler(val boundary: String, val render: (SkysoftRenderContext) -> Unit)
}
