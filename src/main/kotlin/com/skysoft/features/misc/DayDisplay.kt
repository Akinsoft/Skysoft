package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.renderRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object DayDisplay {
    private const val TICKS_PER_DAY = 24_000L
    private val config get() = SkysoftConfigGui.config().gui.dayDisplay

    fun register() {
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "day_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ -> renderHud(context) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "day_display"
            override val label: String = "Day Display"
            override val position get() = config.position
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun width(): Int = currentRenderable()?.width ?: 0
            override fun height(): Int = currentRenderable()?.height ?: 0
            override fun isVisible(): Boolean = config.enabled && currentDay() != null
            override fun renderDummy(context: GuiGraphicsExtractor) {
                currentRenderable()?.render(context)
            }
            override fun openConfig() = SkysoftConfigGui.open("Day Display")
        })
    }

    internal fun diagnosticSnapshot(): String {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val island = HypixelLocationState.currentIsland
        return "enabled=${config.enabled}, inSkyBlock=${HypixelLocationState.inSkyBlock}, " +
            "island=${island?.displayName}, islandEnabled=${island in config.settings.islands.get()}, " +
            "levelLoaded=${level != null}, day=${currentDay()}, " +
            "guiHidden=${MinecraftClient.isGuiHidden(minecraft)}"
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        val island = HypixelLocationState.currentIsland
        if (
            !config.enabled ||
            island !in config.settings.islands.get() ||
            MinecraftClient.isGuiHidden(minecraft)
        ) return
        val day = currentDay() ?: return
        config.position.renderRenderable(context, renderable(day))
    }

    private fun currentDay(): Long? =
        Minecraft.getInstance().level?.overworldClockTime?.floorDiv(TICKS_PER_DAY)

    private fun currentRenderable(): GuiRenderable? = currentDay()?.let(::renderable)

    private fun renderable(day: Long): GuiRenderable = DayRenderable(day).withOverlayPanel(config.details.background)

    private class DayRenderable(private val day: Long) : GuiRenderable {
        private val font get() = Minecraft.getInstance().font
        private val text = "Day: $day"
        override val width: Int get() = font.width(text)
        override val height: Int get() = font.lineHeight

        override fun render(context: GuiGraphicsExtractor) {
            context.text(font, text, 0, 0, config.details.color.get().toColor().rgb, true)
        }
    }
}
