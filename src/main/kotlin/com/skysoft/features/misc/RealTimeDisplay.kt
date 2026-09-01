package com.skysoft.features.misc

import com.skysoft.config.ChatTimestampFormat
import com.skysoft.config.DisplayLabelStyle
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import java.time.LocalTime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object RealTimeDisplay {
    private val config get() = SkysoftConfigGui.config().gui.realTimeDisplay

    fun register() {
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "real_time_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = { canRenderLive() },
                render = { context, _ -> renderHud(context) },
            ),
            object : HudEditorElement {
                override val id: String = "real_time_display"
                override val label: String = "Real Time Display"
                override val position get() = config.position
                override val hasEditorBackground: Boolean get() = !config.details.background
                override fun width(): Int = currentRenderable().width
                override fun height(): Int = currentRenderable().height
                override fun isVisible(): Boolean = config.enabled
                override fun renderEditor(context: GuiGraphicsExtractor) = currentRenderable().render(context)
                override fun openConfig() = SkysoftConfigGui.open("Real Time Display")
            },
        )
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        if (!canRenderLive()) return
        config.position.renderRenderable(context, currentRenderable())
    }

    private fun canRenderLive(minecraft: Minecraft = Minecraft.getInstance()): Boolean =
        shouldShowRealTimeDisplay(
            isEnabled = config.enabled,
            isWorldLoaded = minecraft.level != null,
            isPlayerLoaded = minecraft.player != null,
            isGuiHidden = MinecraftClient.isGuiHidden(minecraft),
        )

    private fun currentRenderable(): GuiRenderable =
        renderable(realTimeText(LocalTime.now(), config.settings.format, config.details.labelStyle))

    private fun renderable(text: String): GuiRenderable =
        StringRenderable(text, color = config.details.color.get().toColor().rgb)
            .withOverlayPanel(config.details.background)
}

internal fun realTimeText(
    time: LocalTime,
    format: ChatTimestampFormat,
    labelStyle: DisplayLabelStyle = DisplayLabelStyle.VALUES_ONLY,
): String = labelStyle.prefix("Time", "⌚") + format.format(time)

internal fun shouldShowRealTimeDisplay(
    isEnabled: Boolean,
    isWorldLoaded: Boolean,
    isPlayerLoaded: Boolean,
    isGuiHidden: Boolean,
): Boolean = isEnabled && isWorldLoaded && isPlayerLoaded && !isGuiHidden
