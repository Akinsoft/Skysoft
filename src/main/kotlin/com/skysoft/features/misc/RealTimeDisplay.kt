package com.skysoft.features.misc

import com.skysoft.config.ChatTimestampFormat
import com.skysoft.config.DisplayLabelStyle
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.registerRenderableHud
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import java.time.LocalTime
import net.minecraft.client.Minecraft

object RealTimeDisplay {
    private val config get() = SkysoftConfigGui.config().gui.information.realTimeDisplay

    fun register() {
        registerRenderableHud(
            id = "real_time_display",
            label = "Real Time Display",
            position = { config.position },
            visible = { canRenderLive() },
            hasEditorBackground = { !config.details.background },
            renderable = ::currentRenderable,
        )
    }

    private fun canRenderLive(minecraft: Minecraft = Minecraft.getInstance()): Boolean =
        shouldShowRealTimeDisplay(
            isEnabled = config.enabled,
            isWorldLoaded = minecraft.level != null,
            isPlayerLoaded = minecraft.player != null,
            isGuiHidden = MinecraftClient.isGuiHidden(minecraft),
        )

    private fun currentRenderable(): GuiRenderable? {
        if (!canRenderLive()) return null
        return renderable(realTimeText(LocalTime.now(), config.settings.format, config.details.labelStyle))
    }

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
