package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.config.features.pets.display.PetOverlayConfig
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

object PetOverlayConfigScreen {
    private var editor: MoulConfigEditor<PetOverlayConfig>? = null
    private val config get() = SkysoftConfigGui.config().pets.display
    private var previewPosition: PreviewPosition? = null
    private var draggingPreview = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    private var wasMouseDown = false

    fun open() {
        resetPreviewInteraction()
        MinecraftClient.setScreen(createScreen(MinecraftClient.screen()))
    }

    fun createScreen(parent: Screen?): Screen =
        object : MoulConfigScreenComponent(
            Component.empty(),
            GuiContext(GuiElementComponent(editor())),
            parent,
        ) {
            override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
                super.extractRenderState(context, mouseX, mouseY, delta)
                renderPreview(context, mouseX, mouseY)
            }

            override fun removed() {
                super.removed()
                SkysoftConfigGui.config().saveNow()
            }
        }

    private fun editor(): MoulConfigEditor<PetOverlayConfig> {
        editor?.let { return it }
        return SkysoftMoulConfigGuis.createEditor(config).also { editor = it }
    }

    private fun renderPreview(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val renderable = ActivePetOverlay.previewRenderable() ?: return
        val minecraft = Minecraft.getInstance()
        val pane = previewPane(renderable)
        val position = updatePreviewPosition(
            pane.defaultLeft,
            pane.defaultTop,
            pane.width,
            pane.height,
            pane.screenWidth,
            pane.screenHeight,
            mouseX,
            mouseY,
        )
        val bounds = pane.boundsAt(position)

        context.fill(
            bounds.left - PREVIEW_SHADOW,
            bounds.top,
            bounds.left,
            bounds.bottom + PREVIEW_SHADOW,
            PREVIEW_SHADOW_COLOR,
        )
        context.fill(
            bounds.right,
            bounds.top,
            bounds.right + PREVIEW_SHADOW,
            bounds.bottom + PREVIEW_SHADOW,
            PREVIEW_SHADOW_COLOR,
        )
        context.fill(
            bounds.left - PREVIEW_SHADOW,
            bounds.bottom,
            bounds.right + PREVIEW_SHADOW,
            bounds.bottom + PREVIEW_SHADOW,
            PREVIEW_SHADOW_COLOR,
        )
        context.fill(bounds.left - 1, bounds.top - 1, bounds.right + 1, bounds.bottom + 1, PREVIEW_BORDER_COLOR)
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, PREVIEW_BACKGROUND_COLOR)

        val label = "Preview"
        val font = minecraft.font
        val labelX = bounds.left + (pane.width - font.width(label)) / 2
        context.text(font, label, labelX, bounds.top + PREVIEW_PADDING / 2, PREVIEW_LABEL_COLOR, false)

        val renderX = bounds.left + PREVIEW_PADDING
        val renderY = bounds.top + PREVIEW_PADDING + PREVIEW_LABEL_HEIGHT + PREVIEW_LABEL_GAP
        context.pose().pushMatrix()
        context.pose().translate(renderX.toFloat(), renderY.toFloat())
        context.pose().scale(pane.scale, pane.scale)
        renderable.render(context)
        context.pose().popMatrix()
    }

    private fun previewPane(renderable: GuiRenderable): PreviewPane {
        val window = Minecraft.getInstance().window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight
        val margin = PREVIEW_MARGIN_AT_NORMAL_SCALE / window.guiScale.coerceAtLeast(1)
        val editorBottom = (screenHeight + (screenHeight - margin).coerceAtMost(MAX_EDITOR_BOTTOM)) / 2
        val scale = config.preview.scale.get()
        val width = (renderable.width * scale).roundToInt() + PREVIEW_PADDING * 2
        val height = (renderable.height * scale).roundToInt() + PREVIEW_PADDING * 2 + PREVIEW_LABEL_HEIGHT + PREVIEW_LABEL_GAP
        return PreviewPane(
            width = width,
            height = height,
            defaultLeft = (screenWidth - width) / 2,
            defaultTop = editorBottom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            scale = scale,
        )
    }

    private fun updatePreviewPosition(
        defaultLeft: Int,
        defaultTop: Int,
        paneWidth: Int,
        paneHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ): PreviewPosition {
        var position = initialPreviewPosition(defaultLeft, defaultTop, paneWidth, paneHeight, screenWidth, screenHeight)
        val mouseDown = isLeftMouseButtonDown()
        if (!mouseDown) {
            draggingPreview = false
        } else if (!wasMouseDown && position.headerContains(mouseX, mouseY, paneWidth)) {
            startPreviewDrag(position, mouseX, mouseY)
        }
        if (draggingPreview) {
            position = PreviewPosition(mouseX - dragOffsetX, mouseY - dragOffsetY)
                .clamped(paneWidth, paneHeight, screenWidth, screenHeight)
        }

        wasMouseDown = mouseDown
        previewPosition = position
        return position
    }

    private fun initialPreviewPosition(
        defaultLeft: Int,
        defaultTop: Int,
        paneWidth: Int,
        paneHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): PreviewPosition =
        (previewPosition ?: PreviewPosition(defaultLeft, defaultTop))
            .clamped(paneWidth, paneHeight, screenWidth, screenHeight)

    private fun isLeftMouseButtonDown(): Boolean =
        GLFW.glfwGetMouseButton(Minecraft.getInstance().window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

    private fun PreviewPosition.headerContains(mouseX: Int, mouseY: Int, paneWidth: Int): Boolean =
        mouseX in left..(left + paneWidth) &&
            mouseY in top..(top + PREVIEW_PADDING + PREVIEW_LABEL_HEIGHT)

    private fun startPreviewDrag(position: PreviewPosition, mouseX: Int, mouseY: Int) {
        draggingPreview = true
        dragOffsetX = mouseX - position.left
        dragOffsetY = mouseY - position.top
    }

    private fun PreviewPosition.clamped(
        paneWidth: Int,
        paneHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): PreviewPosition {
        val maxLeft = screenWidth - paneWidth - PREVIEW_SHADOW
        val maxTop = screenHeight - paneHeight - PREVIEW_SHADOW
        return PreviewPosition(
            clampPreviewCoordinate(left, maxLeft),
            clampPreviewCoordinate(top, maxTop),
        )
    }

    private fun clampPreviewCoordinate(value: Int, maximumValue: Int): Int =
        value.coerceIn(PREVIEW_SHADOW, maxOf(PREVIEW_SHADOW, maximumValue))

    private fun resetPreviewInteraction() {
        draggingPreview = false
        wasMouseDown = false
    }

    private const val PREVIEW_PADDING = 14
    private const val PREVIEW_LABEL_HEIGHT = 10
    private const val PREVIEW_LABEL_GAP = 4
    private const val PREVIEW_SHADOW = 3
    private const val PREVIEW_MARGIN_AT_NORMAL_SCALE = 100
    private const val MAX_EDITOR_BOTTOM = 400
    private const val PREVIEW_SHADOW_COLOR = 0x40000000
    private const val PREVIEW_BORDER_COLOR = 0xFF202026.toInt()
    private const val PREVIEW_BACKGROUND_COLOR = 0xFF17171D.toInt()
    private const val PREVIEW_LABEL_COLOR = 0xFF888888.toInt()

    private data class PreviewPosition(val left: Int, val top: Int)

    private data class PreviewPane(
        val width: Int,
        val height: Int,
        val defaultLeft: Int,
        val defaultTop: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val scale: Float,
    ) {
        fun boundsAt(position: PreviewPosition): PreviewBounds =
            PreviewBounds(position.left, position.top, position.left + width, position.top + height)
    }

    private data class PreviewBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
