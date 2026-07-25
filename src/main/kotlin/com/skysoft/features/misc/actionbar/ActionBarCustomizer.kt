package com.skysoft.features.misc.actionbar

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.OverlayMessages
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.gui.fillOverlayBackground
import kotlin.math.min
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB

object ActionBarCustomizer {
    private val config get() = SkysoftConfigGui.config().gui.actionBar

    fun register() {
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "action_bar"
            override val label: String = "Action Bar"
            override val position get() = config.position
            override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
            override val hasEditorBackground: Boolean get() = !config.background
            override fun width(): Int = editorLayout().width
            override fun height(): Int = editorLayout().height
            override fun isVisible(): Boolean = config.settings.customPosition
            override fun renderDummy(context: GuiGraphicsExtractor) = renderEditorPreview(context)
            override fun openConfig() = SkysoftConfigGui.open("Action Bar")
        })
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE) { vanilla ->
            HudElement { context, tick ->
                SkysoftErrorBoundary.run("Action Bar position render") {
                    renderPositioned(context) {
                        vanilla.extractRenderState(context, tick)
                    }
                }
            }
        }
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.OVERLAY_MESSAGE,
            SkysoftMod.id("action_bar_background"),
            { context, tick ->
                SkysoftErrorBoundary.run("Action Bar background render") { renderBackground(context, tick) }
            },
        )
    }

    private fun renderPositioned(context: GuiGraphicsExtractor, drawVanillaActionBar: () -> Unit) {
        if (!config.settings.customPosition) {
            drawVanillaActionBar()
            return
        }
        if (MinecraftClient.screen() is SkysoftHudEditor.EditorScreen) return

        val message = currentMessage() ?: run {
            drawVanillaActionBar()
            return
        }
        val layout = layout(context, message, useCustomPosition = true)
        val vanillaTextX = (context.guiWidth() - layout.textWidth) / 2
        val vanillaTextY = context.guiHeight() - VANILLA_TEXT_Y_FROM_BOTTOM
        context.pose().pushMatrix()
        try {
            context.pose().translate(layout.x.toFloat(), layout.y.toFloat())
            context.pose().scale(layout.scale, layout.scale)
            context.pose().translate(
                (X_PADDING - vanillaTextX).toFloat(),
                (Y_PADDING - vanillaTextY).toFloat(),
            )
            drawVanillaActionBar()
        } finally {
            context.pose().popMatrix()
        }
    }

    private fun renderBackground(context: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!config.background || MinecraftClient.isGuiHidden(Minecraft.getInstance())) return
        if (config.settings.customPosition && MinecraftClient.screen() is SkysoftHudEditor.EditorScreen) return

        val minecraft = Minecraft.getInstance()
        val message = currentMessage() ?: return
        val time = OverlayMessages.time(minecraft)
        if (time <= 0) return

        val alpha = ((time - tick.getGameTimeDeltaPartialTick(false)) * COLOR_CHANNEL_MAX / FADE_TICKS).toInt()
        if (alpha <= 0) return

        val layout = layout(context, message, config.settings.customPosition)
        val y = layout.y - BottomHudLayout.reservedHeight()
        drawBackground(context, layout.x, y, layout.scaledWidth, layout.scaledHeight, alpha)
        context.nextStratum()
    }

    private fun renderEditorPreview(context: GuiGraphicsExtractor) {
        val layout = editorLayout()
        if (config.background) {
            drawBackground(context, 0, 0, layout.width, layout.height, COLOR_CHANNEL_MAX)
            context.nextStratum()
        }
        context.textWithBackdrop(
            Minecraft.getInstance().font,
            layout.message,
            X_PADDING,
            Y_PADDING,
            layout.textWidth,
            ARGB.white(COLOR_CHANNEL_MAX),
        )
    }

    private fun drawBackground(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        alpha: Int,
    ) {
        val maxAlpha = config.details.backgroundOpacity * COLOR_CHANNEL_MAX / PERCENT_MAX
        val color = BACKGROUND_RGB.withAlpha(min(maxAlpha, alpha))
        context.fillOverlayBackground(
            x,
            y,
            x + width,
            y + height,
            color,
            config.details.roundedCorners,
        )
    }

    private fun currentMessage(): Component? {
        val minecraft = Minecraft.getInstance()
        return OverlayMessages.message(minecraft)?.takeIf {
            OverlayMessages.time(minecraft) > 0 && it.string.isNotBlank() && minecraft.font.width(it) > 0
        }
    }

    private fun editorLayout(): ActionBarLayout {
        val message = currentMessage() ?: EDITOR_MESSAGE
        return layout(message, scale = 1f, x = 0, y = 0)
    }

    private fun layout(
        context: GuiGraphicsExtractor,
        message: Component,
        useCustomPosition: Boolean,
    ): ActionBarLayout {
        val scale = if (useCustomPosition) config.position.effectiveScale else 1f
        val measured = layout(message, scale = scale, x = 0, y = 0)
        if (!useCustomPosition) {
            return measured.copy(
                x = (context.guiWidth() - measured.textWidth) / 2 - X_PADDING,
                y = context.guiHeight() - VANILLA_TEXT_Y_FROM_BOTTOM - Y_PADDING,
            )
        }

        return measured.copy(
            x = config.position.getAbsX0AllowingOverflow(measured.scaledWidth),
            y = config.position.getAbsY0AllowingOverflow(measured.scaledHeight),
        )
    }

    private fun layout(message: Component, scale: Float, x: Int, y: Int): ActionBarLayout {
        val textWidth = Minecraft.getInstance().font.width(message)
        val width = textWidth + X_PADDING * 2
        val height = FONT_HEIGHT + Y_PADDING * 2
        return ActionBarLayout(
            message,
            textWidth,
            width,
            height,
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
            scale,
            x,
            y,
        )
    }
}

private data class ActionBarLayout(
    val message: Component,
    val textWidth: Int,
    val width: Int,
    val height: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val scale: Float,
    val x: Int,
    val y: Int,
)

private val EDITOR_MESSAGE = Component.literal("Action Bar")
private const val BACKGROUND_RGB = 0x101010
private const val X_PADDING = 4
private const val Y_PADDING = 3
private const val PERCENT_MAX = 100
private const val FADE_TICKS = 20.0f
private const val VANILLA_TEXT_Y_FROM_BOTTOM = 72
private const val FONT_HEIGHT = 9
