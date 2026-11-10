package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.gui.TabDataOverlays
import com.skysoft.gui.registerRenderableHud
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object DayDisplay {
    private const val TICKS_PER_DAY = 24_000L
    private val config get() = SkysoftConfigGui.config().gui.information.dayDisplay

    fun register() {
        registerRenderableHud(
            id = "day_display",
            label = "Day Display",
            position = { config.position },
            visible = TabDataOverlays::canRender,
            hasEditorBackground = { !config.details.background },
            renderable = ::currentRenderable,
        )
    }

    private fun isDisplayVisible(): Boolean =
        config.enabled && HypixelLocationState.inSkyBlock &&
            HypixelLocationState.currentIsland in config.settings.islands.get() &&
            !MinecraftClient.isGuiHidden(Minecraft.getInstance()) && currentDay() != null

    private fun currentDay(): Long? =
        Minecraft.getInstance().level?.overworldClockTime?.floorDiv(TICKS_PER_DAY)

    private fun currentRenderable(): GuiRenderable? {
        if (!isDisplayVisible()) return null
        return currentDay()?.let(::renderable)
    }

    private fun renderable(day: Long): GuiRenderable = DayRenderable(day).withOverlayPanel(config.details.background)

    private class DayRenderable(day: Long) : GuiRenderable {
        private val font get() = Minecraft.getInstance().font
        private val text = "Day: $day"
        override val width: Int get() = font.width(text)
        override val height: Int get() = font.lineHeight

        override fun render(context: GuiGraphicsExtractor) {
            context.text(font, text, 0, 0, config.details.color.get().toColor().rgb, true)
        }
    }
}
