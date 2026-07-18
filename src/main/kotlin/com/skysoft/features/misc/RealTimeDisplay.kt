package com.skysoft.features.misc

import com.skysoft.config.ChatTimestampFormat
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.formatLocalTime
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import java.time.LocalTime
import java.time.ZoneId
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object RealTimeDisplay {
    private val config get() = SkysoftConfigGui.config().gui.realTimeDisplay

    fun register() {
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "real_time_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = { canRenderLive() },
                render = { context, _ -> renderHud(context) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "real_time_display"
            override val label: String = "Real Time Display"
            override val position get() = config.position
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun width(): Int = currentRenderable().width
            override fun height(): Int = currentRenderable().height
            override fun isVisible(): Boolean = config.enabled
            override fun renderDummy(context: GuiGraphicsExtractor) = currentRenderable().render(context)
            override fun openConfig() = SkysoftConfigGui.open("Real Time Display")
        })
    }

    internal fun diagnosticSnapshot(): String {
        val minecraft = Minecraft.getInstance()
        val zone = ZoneId.systemDefault()
        val time = LocalTime.now(zone)
        val text = realTimeText(time, config.settings.format)
        val renderable = renderable(text)
        return "enabled=${config.enabled}, worldLoaded=${minecraft.level != null}, playerLoaded=${minecraft.player != null}, " +
            "guiHidden=${MinecraftClient.isGuiHidden(minecraft)}, canRender=${canRenderLive(minecraft)}, " +
            "zone=${zone.id}, format=${config.settings.format.name}, time=$time, rendered='$text', " +
            "background=${config.details.background}, color=${config.details.color.get()}, " +
            "position=${config.position.x},${config.position.y}, scale=${config.position.scale}, " +
            "dimensions=${renderable.width}x${renderable.height}"
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
        renderable(realTimeText(LocalTime.now(), config.settings.format))

    private fun renderable(text: String): GuiRenderable =
        StringRenderable(text, color = config.details.color.get().toColor().rgb)
            .withOverlayPanel(config.details.background)
}

internal fun realTimeText(time: LocalTime, format: ChatTimestampFormat): String =
    formatLocalTime(time, format.pattern)

internal fun shouldShowRealTimeDisplay(
    isEnabled: Boolean,
    isWorldLoaded: Boolean,
    isPlayerLoaded: Boolean,
    isGuiHidden: Boolean,
): Boolean = isEnabled && isWorldLoaded && isPlayerLoaded && !isGuiHidden
