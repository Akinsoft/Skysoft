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
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.image.RegisteredImageTexture
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.net.AsyncRequestSlot
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

internal object ScreenshotCapturePreview {
    private val contexts = GuiOverlayContextType.entries.toSet()
    private val imageRequest = AsyncRequestSlot(
        completionExecutor = Minecraft.getInstance(),
        disposeStaleResult = NativeImage::close,
    )
    private var presentation: CapturePresentation? = null
    private var imageBounds: Rect? = null
    private var shareBounds: Rect? = null
    private var deleteBounds: Rect? = null
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
        clear()
        imageRequest.replace(
            loadScaledScreenshotImage(path, MAXIMUM_TEXTURE_WIDTH, MAXIMUM_TEXTURE_HEIGHT),
        ) { image, failure ->
            if (failure != null || image == null || !SkysoftConfigGui.config().gui.screenshotManager.enabled) {
                image?.close()
            } else {
                replacePresentation(path, image)
            }
        }
    }

    fun processMouseButtonPress(button: Int): InputHandlingResult {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return InputHandlingResult.IGNORED
        val current = presentation?.takeIf { it.elapsedMillis() >= TRAVEL_END_MILLIS }
            ?: return InputHandlingResult.IGNORED
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft)?.takeUnless { it is ScreenshotManagerScreen }
            ?: return InputHandlingResult.IGNORED
        val window = minecraft.window
        val mouseX = (minecraft.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth).toInt()
        val mouseY = (minecraft.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight).toInt()
        return when {
            closeBounds?.contains(mouseX, mouseY) == true -> {
                clear()
                InputHandlingResult.CONSUMED
            }
            deleteBounds?.contains(mouseX, mouseY) == true -> {
                val path = current.path
                clear()
                CompletableFuture.runAsync({ ScreenshotRepository.delete(path) }, Util.ioPool()).whenComplete { _, failure ->
                    if (failure == null) return@whenComplete
                    minecraft.execute {
                        SkysoftChat.error("Couldn't delete screenshot.")
                    }
                }
                InputHandlingResult.CONSUMED
            }
            shareBounds?.contains(mouseX, mouseY) == true -> {
                val path = current.path
                clear()
                ScreenshotSharing.request(path, screen)
                InputHandlingResult.CONSUMED
            }
            imageBounds?.contains(mouseX, mouseY) == true -> {
                val path = current.path
                clear()
                ScreenshotManager.open(path)
                InputHandlingResult.CONSUMED
            }
            else -> InputHandlingResult.IGNORED
        }
    }

    private fun render(context: GuiGraphicsExtractor, overlayContext: GuiOverlayContext) {
        val current = presentation ?: return
        if (!SkysoftConfigGui.config().gui.screenshotManager.enabled || current.elapsedMillis() >= DISPLAY_MILLIS) {
            clear()
            return
        }
        val bounds = imageBounds(
            context.guiWidth(),
            context.guiHeight(),
            current.image.width.toDouble() / current.image.height,
            current.elapsedMillis(),
        )
        val isSettled = current.elapsedMillis() >= TRAVEL_END_MILLIS
        if (!isSettled) {
            drawTexture(context, current, bounds)
            return
        }
        val layout = CapturePreviewLayout.create(bounds)
        OverlayPanelStyle.draw(
            context,
            layout.panel.x,
            layout.panel.y,
            layout.panel.width,
            layout.panel.height,
        )
        drawTexture(context, current, bounds)
        drawSettledControls(context, layout, current.path, overlayContext)
    }

    private fun drawSettledControls(
        context: GuiGraphicsExtractor,
        layout: CapturePreviewLayout,
        path: Path,
        overlayContext: GuiOverlayContext,
    ) {
        imageBounds = layout.image
        closeBounds = layout.close
        deleteBounds = layout.delete
        shareBounds = layout.share
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val mouseX = (minecraft.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth).toInt()
        val mouseY = (minecraft.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight).toInt()
        val canInteract = overlayContext.screen != null && overlayContext.screen !is ScreenshotManagerScreen
        PixelButtonRenderer.draw(
            context,
            minecraft.font,
            layout.share,
            ScreenshotSharing.buttonLabel(path),
            selected = false,
            hovered = canInteract && layout.share.contains(mouseX, mouseY),
            enabled = canInteract && ScreenshotSharing.status(path).state != ScreenshotShareState.UPLOADING,
        )
        PixelButtonRenderer.draw(
            context,
            minecraft.font,
            layout.delete,
            "",
            selected = false,
            hovered = canInteract && layout.delete.contains(mouseX, mouseY),
            enabled = canInteract,
            tone = PixelButtonTone.DANGER,
        )
        PixelButtonRenderer.drawIcon(context, layout.delete, TRASH_ICON, TRASH_ICON_SCALE, canInteract)
        PixelButtonRenderer.draw(
            context,
            minecraft.font,
            layout.close,
            "x",
            selected = false,
            hovered = canInteract && layout.close.contains(mouseX, mouseY),
            enabled = canInteract,
        )
    }

    private fun drawTexture(context: GuiGraphicsExtractor, presentation: CapturePresentation, bounds: Rect) {
        context.blit(
            presentation.image.texture.textureView,
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
            elapsedMillis < SHRINK_END_MILLIS -> Rect(0, 0, screenWidth, screenHeight).interpolateTo(
                center,
                EasingUtilities.easeOutCubic(
                    (elapsedMillis - FULL_SCREEN_HOLD_MILLIS).toDouble() /
                        (SHRINK_END_MILLIS - FULL_SCREEN_HOLD_MILLIS),
                ),
            )
            elapsedMillis < TRAVEL_END_MILLIS -> center.interpolateTo(
                settled,
                EasingUtilities.smoothStep(
                    (elapsedMillis - SHRINK_END_MILLIS).toDouble() / (TRAVEL_END_MILLIS - SHRINK_END_MILLIS),
                ),
            )
            else -> settled
        }
    }

    private fun replacePresentation(path: Path, image: NativeImage) {
        clear()
        val id = SkysoftMod.id("screenshot_capture/preview_${nextTextureId++}")
        val texture = RegisteredImageTexture.register(id, "Skysoft Screenshot Capture Preview", image)
        presentation = CapturePresentation(path, texture, System.currentTimeMillis())
    }

    private fun clear() {
        imageRequest.invalidate()
        val current = presentation
        presentation = null
        imageBounds = null
        shareBounds = null
        deleteBounds = null
        closeBounds = null
        current?.image?.release()
    }

    private data class CapturePreviewLayout(
        val panel: Rect,
        val image: Rect,
        val share: Rect,
        val delete: Rect,
        val close: Rect,
    ) {
        companion object {
            fun create(image: Rect): CapturePreviewLayout {
                val panel = Rect(
                    image.x - PANEL_PADDING,
                    image.y - PANEL_PADDING,
                    image.width + PANEL_PADDING * 2,
                    image.height + PANEL_PADDING * 2 + ACTION_HEIGHT + ACTION_GAP,
                )
                val actionY = image.y + image.height + ACTION_GAP
                val delete = Rect(
                    panel.x + panel.width - PANEL_PADDING - CLOSE_SIZE,
                    actionY + (ACTION_HEIGHT - CLOSE_SIZE) / 2,
                    CLOSE_SIZE,
                    CLOSE_SIZE,
                )
                return CapturePreviewLayout(
                    panel = panel,
                    image = image,
                    share = Rect(
                        panel.x + PANEL_PADDING,
                        actionY,
                        panel.width - PANEL_PADDING * 2 - CLOSE_SIZE - ACTION_GAP,
                        ACTION_HEIGHT,
                    ),
                    delete = delete,
                    close = Rect(
                        image.x + image.width - CLOSE_SIZE,
                        image.y,
                        CLOSE_SIZE,
                        CLOSE_SIZE,
                    ),
                )
            }
        }
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
    private const val TRASH_ICON_SCALE = 1
    private val TRASH_ICON = listOf(".XXX.", "XXXXX", ".X.X.", ".X.X.", ".XXX.")
}

private data class CapturePresentation(
    val path: Path,
    val image: RegisteredImageTexture,
    val startedAtMillis: Long,
) {
    fun elapsedMillis(): Long = System.currentTimeMillis() - startedAtMillis
}
