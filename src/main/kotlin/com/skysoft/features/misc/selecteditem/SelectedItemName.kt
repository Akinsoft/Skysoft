package com.skysoft.features.misc.selecteditem

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.RenderableHudEditorElement
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.fillOverlayBackground
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.renderRenderable
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack

object SelectedItemName {
    private val config get() = SkysoftConfigGui.config().gui.playerHud.selectedItemName

    fun register() {
        HudEditorRegistry.register(object : RenderableHudEditorElement {
            override val id: String = "selected_item_name"
            override val label: String = "Selected Item Name"
            override val position get() = config.position
            override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun hudRenderable() = currentRenderable()
            override fun isVisible(): Boolean = currentRenderable() != null
            override fun openConfig() = SkysoftConfigGui.open("Selected Item Name")
        })
    }

    fun isEnabled(): Boolean = config.enabled

    fun render(context: GuiGraphicsExtractor) {
        val renderable = currentRenderable() ?: return
        config.position.renderRenderable(context, renderable)
    }

    private fun itemName(stack: ItemStack): MutableComponent =
        Component.empty().append(stack.hoverName).withStyle(stack.rarity.color()).also { name ->
            if (stack.has(DataComponents.CUSTOM_NAME)) name.withStyle(ChatFormatting.ITALIC)
        }

    private fun currentRenderable(): SelectedItemNameRenderable? {
        val minecraft = Minecraft.getInstance()
        if (!config.enabled || minecraft.player == null || MinecraftClient.isGuiHidden(minecraft)) return null
        val highlight = MinecraftClient.selectedItemNameState(minecraft)
        val stack = highlight.skysoftSelectedItemNameStack()
        val alpha = selectedItemNameAlpha(highlight.skysoftSelectedItemNameTimer(), config.settings.alwaysVisible)
        if (stack.isEmpty || alpha <= 0) return null
        return SelectedItemNameRenderable(itemName(stack), alpha)
    }

    private class SelectedItemNameRenderable(
        private val name: Component,
        private val alpha: Int,
    ) : GuiRenderable {
        private val font get() = Minecraft.getInstance().font
        override val width: Int get() = font.width(name) + HORIZONTAL_PADDING * 2
        override val height: Int get() = font.lineHeight + VERTICAL_PADDING * 2

        override fun render(context: GuiGraphicsExtractor) {
            if (config.details.background) {
                context.fillOverlayBackground(
                    0,
                    0,
                    width,
                    height,
                    OverlayPanelStyle.fadedHudBackgroundColor(alpha, config.details.backgroundOpacity),
                    config.details.roundedCorners,
                )
                context.nextStratum()
            }
            context.text(font, name, HORIZONTAL_PADDING, VERTICAL_PADDING, ARGB.white(alpha), true)
        }
    }
}

interface SelectedItemNameState {
    fun skysoftSelectedItemNameStack(): ItemStack
    fun skysoftSelectedItemNameTimer(): Int
}

private fun selectedItemNameAlpha(timer: Int, alwaysVisible: Boolean): Int =
    if (alwaysVisible) COLOR_CHANNEL_MAX else
        (timer * FADE_ALPHA_SCALE / FADE_TICKS).coerceIn(0, COLOR_CHANNEL_MAX)

private const val HORIZONTAL_PADDING = 4
private const val VERTICAL_PADDING = 3
private const val FADE_TICKS = 10
private const val FADE_ALPHA_SCALE = 256
