package com.skysoft.features.screenshot

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContext
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import java.nio.file.Path
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

internal object ScreenshotCapturePreview {
    private val contexts = GuiOverlayContextType.entries.toSet()
    private var presentation: CapturePresentation? = null
    private var shareBounds: Rect? = null
    private var closeBounds: Rect? = null
    private var nextTextureId = 0

    fun register() {
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "Screenshot capture preview",
                layer = GuiOverlayLayer.ABOVE_SCREEN,
                contexts = contexts,
                visible = { presentation != null },
                render = ::render,
            ),
        )
    }

    fun present(path: Path) {
        if (!SkysoftConfigGui.config().gui.screenshotManager.enabled) return
        loadScaledScreenshotImage(path, MAXIMUM_TEXTURE_WIDTH, MAXIMUM_TEXTURE_HEIGHT).whenComplete { image, failure ->
            Minecraft.getInstance().execute {
                if (failure != null || image == null || !SkysoftConfigGui.config().gui.screenshotManager.enabled) {
                    image?.close()
                    return@execute
                }
                replacePresentation(path, image)
            }
        }
    }

    fun processMouseButtonPress(button: Int): InputHandlingResult {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
        val current = presentation ?: return InputHandlingResult.IGNORED
        if (current.elapsedMillis() < TRAVEL_END_MILLIS) return InputHandlingResult.IGNORED
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft) ?: return InputHandlingResult.IGNORED
        if (screen is ScreenshotManagerScreen) return InputHandlingResult.IGNORED
        val window = minecraft.window
        val mouseX = (minecraft.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth).toInt()
        val mouseY = (minecraft.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight).toInt()
        if (closeBounds?.contains(mouseX, mouseY) == true) {
            clear()
            return InputHandlingResult.CONSUMED
        }
        if (shareBounds?.contains(mouseX, mouseY) == true) {
            val path = current.path
            clear()
            ScreenshotSharing.request(path, screen)
            return InputHandlingResult.CONSUMED
        }
        return InputHandlingResult.IGNORED
    }

    private fun render(context: GuiGraphicsExtractor, overlayContext: GuiOverlayContext) {
        val current = presentation ?: return
        if (!SkysoftConfigGui.config().gui.screenshotManager.enabled || current.elapsedMillis() >= DISPLAY_MILLIS) {
            clear()
            return
        }
        val imageBounds = imageBounds(
            context.guiWidth(),
            context.guiHeight(),
            current.imageWidth.toDouble() / current.imageHeight,
            current.elapsedMillis(),
        )
        val isSettled = current.elapsedMillis() >= TRAVEL_END_MILLIS
        if (isSettled) drawSettledPanel(context, imageBounds, current.path, overlayContext)
        drawTexture(context, current, imageBounds)
    }

    private fun drawSettledPanel(
        context: GuiGraphicsExtractor,
        imageBounds: Rect,
        path: Path,
        overlayContext: GuiOverlayContext,
    ) {
        val panel = Rect(
            imageBounds.x - PANEL_PADDING,
            imageBounds.y - PANEL_PADDING,
            imageBounds.width + PANEL_PADDING * 2,
            imageBounds.height + PANEL_PADDING * 2 + ACTION_HEIGHT + ACTION_GAP,
        )
        OverlayPanelStyle.draw(context, panel.x, panel.y, panel.width, panel.height)
        val actionY = imageBounds.y + imageBounds.height + ACTION_GAP
        val close = Rect(
            panel.x + panel.width - PANEL_PADDING - CLOSE_SIZE,
            actionY + (ACTION_HEIGHT - CLOSE_SIZE) / 2,
            CLOSE_SIZE,
            CLOSE_SIZE,
        )
        val share = Rect(
            panel.x + PANEL_PADDING,
            actionY,
            panel.width - PANEL_PADDING * 2 - CLOSE_SIZE - ACTION_GAP,
            ACTION_HEIGHT,
        )
        closeBounds = close
        shareBounds = share
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val mouseX = (minecraft.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth).toInt()
        val mouseY = (minecraft.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight).toInt()
        val canInteract = overlayContext.screen != null && overlayContext.screen !is ScreenshotManagerScreen
        PixelButtonRenderer.draw(
            context,
            minecraft.font,
            share,
            ScreenshotSharing.buttonLabel(path),
            selected = false,
            hovered = canInteract && share.contains(mouseX, mouseY),
            enabled = canInteract && ScreenshotSharing.status(path).state != ScreenshotShareState.UPLOADING,
        )
        PixelButtonRenderer.draw(
            context,
            minecraft.font,
            close,
            "x",
            selected = false,
            hovered = canInteract && close.contains(mouseX, mouseY),
            enabled = canInteract,
        )
    }

    private fun drawTexture(context: GuiGraphicsExtractor, presentation: CapturePresentation, bounds: Rect) {
        context.blit(
            presentation.texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            0f,
            1f,
            0f,
            1f,
        )
    }

    private fun imageBounds(screenWidth: Int, screenHeight: Int, imageAspect: Double, elapsedMillis: Long): Rect {
        val centerWidthLimit = min(CENTER_MAXIMUM_WIDTH, (screenWidth * CENTER_SCREEN_RATIO).roundToInt())
        val centerHeightLimit = min(CENTER_MAXIMUM_HEIGHT, (screenHeight * CENTER_HEIGHT_RATIO).roundToInt())
        val centerWidth = min(centerWidthLimit, (centerHeightLimit * imageAspect).roundToInt()).coerceAtLeast(1)
        val centerHeight = (centerWidth / imageAspect).roundToInt().coerceAtLeast(1)
        val center = Rect((screenWidth - centerWidth) / 2, (screenHeight - centerHeight) / 2, centerWidth, centerHeight)
        val finalWidthLimit = min(FINAL_MAXIMUM_WIDTH, (screenWidth * FINAL_SCREEN_RATIO).roundToInt())
        val finalHeightLimit = min(FINAL_MAXIMUM_HEIGHT, (screenHeight * FINAL_HEIGHT_RATIO).roundToInt())
        val finalWidth = min(finalWidthLimit, (finalHeightLimit * imageAspect).roundToInt()).coerceAtLeast(1)
        val finalHeight = (finalWidth / imageAspect).roundToInt().coerceAtLeast(1)
        val settled = Rect(
            screenWidth - finalWidth - SCREEN_INSET - PANEL_PADDING,
            SCREEN_INSET + PANEL_PADDING,
            finalWidth,
            finalHeight,
        )
        return when {
            elapsedMillis < FULL_SCREEN_HOLD_MILLIS -> Rect(0, 0, screenWidth, screenHeight)
            elapsedMillis < SHRINK_END_MILLIS -> interpolate(
                Rect(0, 0, screenWidth, screenHeight),
                center,
                EasingUtilities.easeOutCubic(
                    (elapsedMillis - FULL_SCREEN_HOLD_MILLIS).toDouble() /
                        (SHRINK_END_MILLIS - FULL_SCREEN_HOLD_MILLIS),
                ),
            )
            elapsedMillis < TRAVEL_END_MILLIS -> interpolate(
                center,
                settled,
                EasingUtilities.smoothStep(
                    (elapsedMillis - SHRINK_END_MILLIS).toDouble() / (TRAVEL_END_MILLIS - SHRINK_END_MILLIS),
                ),
            )
            else -> settled
        }
    }

    private fun interpolate(from: Rect, to: Rect, progress: Double): Rect = Rect(
        interpolate(from.x, to.x, progress),
        interpolate(from.y, to.y, progress),
        interpolate(from.width, to.width, progress),
        interpolate(from.height, to.height, progress),
    )

    private fun interpolate(from: Int, to: Int, progress: Double): Int =
        (from + (to - from) * progress).roundToInt()

    private fun replacePresentation(path: Path, image: NativeImage) {
        clear()
        val id = SkysoftMod.id("screenshot_capture/preview_${nextTextureId++}")
        val texture = try {
            DynamicTexture({ "Skysoft Screenshot Capture Preview" }, image)
        } catch (failure: Throwable) {
            image.close()
            throw failure
        }
        try {
            Minecraft.getInstance().textureManager.register(id, texture)
        } catch (failure: Throwable) {
            texture.close()
            throw failure
        }
        presentation = CapturePresentation(path, id, texture, image.width, image.height, System.currentTimeMillis())
    }

    private fun clear() {
        val current = presentation
        presentation = null
        shareBounds = null
        closeBounds = null
        current?.let { Minecraft.getInstance().textureManager.release(it.id) }
    }

    private const val MAXIMUM_TEXTURE_WIDTH = 1920
    private const val MAXIMUM_TEXTURE_HEIGHT = 1080
    private const val CENTER_MAXIMUM_WIDTH = 520
    private const val CENTER_MAXIMUM_HEIGHT = 292
    private const val CENTER_SCREEN_RATIO = 0.58
    private const val CENTER_HEIGHT_RATIO = 0.62
    private const val FINAL_MAXIMUM_WIDTH = 220
    private const val FINAL_MAXIMUM_HEIGHT = 180
    private const val FINAL_SCREEN_RATIO = 0.23
    private const val FINAL_HEIGHT_RATIO = 0.35
    private const val SCREEN_INSET = 8
    private const val PANEL_PADDING = 4
    private const val ACTION_HEIGHT = 18
    private const val ACTION_GAP = 4
    private const val CLOSE_SIZE = 14
    private const val FULL_SCREEN_HOLD_MILLIS = 90L
    private const val SHRINK_END_MILLIS = 520L
    private const val TRAVEL_END_MILLIS = 980L
    private const val DISPLAY_MILLIS = 10_000L
}

private data class CapturePresentation(
    val path: Path,
    val id: Identifier,
    val texture: DynamicTexture,
    val imageWidth: Int,
    val imageHeight: Int,
    val startedAtMillis: Long,
) {
    fun elapsedMillis(): Long = System.currentTimeMillis() - startedAtMillis
}
