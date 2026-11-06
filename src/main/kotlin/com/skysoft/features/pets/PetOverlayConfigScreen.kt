package com.skysoft.features.pets

import com.mojang.blaze3d.platform.InputConstants
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.renderables.withIsolatedPose
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.config.features.pets.display.PetOverlayConfig
import com.skysoft.config.features.pets.display.PetOverlayConfig.GeneralPetOverlaySettingsConfig.HorizontalAnchor
import com.skysoft.utils.MinecraftClient
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
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
        val preview = ActivePetOverlay.settingsPreview() ?: return
        val renderable = preview.renderable
        val minecraft = Minecraft.getInstance()
        val pane = previewPane(preview)
        val bounds = pane.boundsAt(updatePreviewPosition(pane, mouseX, mouseY))

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

        val contentLeft = bounds.left + PREVIEW_PADDING
        val contentTop = bounds.top + PREVIEW_PADDING + PREVIEW_LABEL_HEIGHT + PREVIEW_LABEL_GAP
        val renderWidth = (renderable.width * pane.scale).roundToInt()
        val renderHeight = (renderable.height * pane.scale).roundToInt()
        val renderX = contentLeft + when (preview.horizontalAnchor) {
            HorizontalAnchor.CENTER -> (pane.contentWidth - renderWidth) / 2
            HorizontalAnchor.RIGHT -> pane.contentWidth - renderWidth
            HorizontalAnchor.LEFT, null -> 0
        }
        val renderY = contentTop + (pane.contentHeight - renderHeight) / 2
        context.withIsolatedPose {
            context.pose().translate(renderX.toFloat(), renderY.toFloat())
            context.pose().scale(pane.scale, pane.scale)
            renderable.render(context)
        }
        preview.horizontalAnchor?.let { horizontalAnchor ->
            val anchorX = contentLeft + when (horizontalAnchor) {
                HorizontalAnchor.LEFT -> 0
                HorizontalAnchor.CENTER -> pane.contentWidth / 2
                HorizontalAnchor.RIGHT -> pane.contentWidth
            }
            context.fill(
                anchorX,
                contentTop - ANCHOR_LINE_OVERHANG,
                anchorX + ANCHOR_LINE_WIDTH,
                contentTop + pane.contentHeight + ANCHOR_LINE_OVERHANG,
                ANCHOR_LINE_COLOR,
            )
        }
    }

    private fun previewPane(preview: PetDisplayPreview): PreviewPane {
        val window = Minecraft.getInstance().window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight
        val margin = PREVIEW_MARGIN_AT_NORMAL_SCALE / window.guiScale.coerceAtLeast(1)
        val editorBottom = (screenHeight + (screenHeight - margin).coerceAtMost(MAX_EDITOR_BOTTOM)) / 2
        val scale = config.preview.scale.get()
        val contentWidth = (preview.width * scale).roundToInt()
        val contentHeight = (preview.height * scale).roundToInt()
        val width = contentWidth + PREVIEW_PADDING * 2
        val height = contentHeight + PREVIEW_PADDING * 2 + PREVIEW_LABEL_HEIGHT + PREVIEW_LABEL_GAP
        return PreviewPane(
            width = width,
            height = height,
            defaultLeft = (screenWidth - width) / 2,
            defaultTop = editorBottom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            scale = scale,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
        )
    }

    private fun updatePreviewPosition(pane: PreviewPane, mouseX: Int, mouseY: Int): PreviewPosition {
        var position = (previewPosition ?: PreviewPosition(pane.defaultLeft, pane.defaultTop)).clampedTo(pane)
        val mouseDown = InputUtilities.isMouseButtonDown(InputConstants.MOUSE_BUTTON_LEFT)
        if (!mouseDown) {
            draggingPreview = false
        } else if (!wasMouseDown && position.headerContains(mouseX, mouseY, pane.width)) {
            draggingPreview = true
            dragOffsetX = mouseX - position.left
            dragOffsetY = mouseY - position.top
        }
        if (draggingPreview) {
            position = PreviewPosition(mouseX - dragOffsetX, mouseY - dragOffsetY).clampedTo(pane)
        }

        wasMouseDown = mouseDown
        previewPosition = position
        return position
    }

    private fun PreviewPosition.headerContains(mouseX: Int, mouseY: Int, paneWidth: Int): Boolean =
        mouseX in left..(left + paneWidth) &&
            mouseY in top..(top + PREVIEW_PADDING + PREVIEW_LABEL_HEIGHT)

    private fun PreviewPosition.clampedTo(pane: PreviewPane): PreviewPosition = PreviewPosition(
        clampPreviewCoordinate(left, pane.screenWidth - pane.width - PREVIEW_SHADOW),
        clampPreviewCoordinate(top, pane.screenHeight - pane.height - PREVIEW_SHADOW),
    )

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
    private const val ANCHOR_LINE_COLOR = 0xFFFF5555.toInt()
    private const val ANCHOR_LINE_WIDTH = 1
    private const val ANCHOR_LINE_OVERHANG = 4

    private data class PreviewPosition(val left: Int, val top: Int)

    private data class PreviewPane(
        val width: Int,
        val height: Int,
        val defaultLeft: Int,
        val defaultTop: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val scale: Float,
        val contentWidth: Int,
        val contentHeight: Int,
    ) {
        fun boundsAt(position: PreviewPosition): PreviewBounds =
            PreviewBounds(position.left, position.top, position.left + width, position.top + height)
    }

    private data class PreviewBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
