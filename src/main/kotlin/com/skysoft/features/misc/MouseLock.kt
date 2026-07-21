package com.skysoft.features.misc

import com.mojang.brigadier.Command
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object MouseLock {
    private var locked = false
    private val config get() = SkysoftConfigGui.config().misc.mouseLock

    fun register() {
        SkysoftClientEvents.onDisconnect("Mouse Lock reset") { locked = false }
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "mouse_lock",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                visible = {
                    config.settings.showDisplay && locked && !MinecraftClient.isGuiHidden(Minecraft.getInstance())
                },
                render = { context, _ -> config.position.renderRenderable(context, renderable()) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "mouse_lock"
            override val label: String = "Mouse Lock"
            override val position get() = config.position
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun width(): Int = renderable().width
            override fun height(): Int = renderable().height
            override fun isVisible(): Boolean = config.settings.showDisplay
            override fun renderDummy(context: GuiGraphicsExtractor) = renderable().render(context)
            override fun openConfig() = SkysoftConfigGui.open("Mouse Lock")
        })
    }

    fun toggle(source: FabricClientCommandSource): Int {
        locked = !locked
        if (!config.settings.hideMessage) {
            SkysoftChat.feedback(
                source,
                if (locked) "Mouse rotation locked. Run /ss mouselock again to unlock it."
                else "Mouse rotation unlocked.",
            )
        }
        return Command.SINGLE_SUCCESS
    }

    @JvmStatic
    fun apply(delta: Double): Double = if (locked) 0.0 else delta

    private fun renderable(): GuiRenderable =
        StringRenderable(MOUSE_LOCK_TEXT, color = config.details.color.get().toColor().rgb)
            .withOverlayPanel(config.details.background)
}

private const val MOUSE_LOCK_TEXT = "Mouselock enabled"
