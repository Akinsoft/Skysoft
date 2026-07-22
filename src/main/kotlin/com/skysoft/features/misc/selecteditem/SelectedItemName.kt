package com.skysoft.features.misc.selecteditem

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.utils.ColorUtilities.ARGB_ALPHA_SHIFT
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.gui.fillOverlayBackground
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.renderRenderable
import kotlin.math.min
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack

object SelectedItemName {
    private val config get() = SkysoftConfigGui.config().gui.selectedItemName

    fun register() {
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "selected_item_name"
            override val label: String = "Selected Item Name"
            override val position get() = config.position
            override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
            override val hasEditorBackground: Boolean get() = !config.details.background
            override fun width(): Int = currentRenderable()?.width ?: 0
            override fun height(): Int = currentRenderable()?.height ?: 0
            override fun isVisible(): Boolean = config.enabled && currentRenderable() != null
            override fun renderDummy(context: GuiGraphicsExtractor) {
                currentRenderable()?.render(context)
            }
            override fun openConfig() = SkysoftConfigGui.open("Selected Item Name")
        })
    }

    fun isEnabled(): Boolean = config.enabled

    fun render(context: GuiGraphicsExtractor, stack: ItemStack, timer: Int) {
        val alpha = selectedItemNameAlpha(timer, config.settings.alwaysVisible)
        if (!stack.isEmpty && alpha > 0) {
            config.position.renderRenderable(context, renderable(itemName(stack), alpha))
        }
    }

    private fun itemName(stack: ItemStack): MutableComponent =
        Component.empty().append(stack.hoverName).withStyle(stack.rarity.color()).also { name ->
            if (stack.has(DataComponents.CUSTOM_NAME)) name.withStyle(ChatFormatting.ITALIC)
        }

    private fun currentRenderable(): SelectedItemNameRenderable? =
        Minecraft.getInstance().player?.mainHandItem
            ?.takeUnless(ItemStack::isEmpty)
            ?.let { renderable(itemName(it), COLOR_CHANNEL_MAX) }

    private fun renderable(name: Component, alpha: Int) = SelectedItemNameRenderable(name, alpha)

    private class SelectedItemNameRenderable(
        private val name: Component,
        private val alpha: Int,
    ) : GuiRenderable {
        private val font get() = Minecraft.getInstance().font
        override val width: Int get() = font.width(name) + HORIZONTAL_PADDING * 2
        override val height: Int get() = font.lineHeight + VERTICAL_PADDING * 2

        override fun render(context: GuiGraphicsExtractor) {
            if (config.details.background) {
                val backgroundAlpha = min(
                    alpha,
                    config.details.backgroundOpacity * COLOR_CHANNEL_MAX / PERCENT_MAX,
                )
                context.fillOverlayBackground(
                    0,
                    0,
                    width,
                    height,
                    (backgroundAlpha shl ARGB_ALPHA_SHIFT) or BACKGROUND_RGB,
                    config.details.roundedCorners,
                )
                context.nextStratum()
            }
            context.text(font, name, HORIZONTAL_PADDING, VERTICAL_PADDING, ARGB.white(alpha), true)
        }
    }
}

internal fun selectedItemNameAlpha(timer: Int, alwaysVisible: Boolean): Int =
    if (alwaysVisible) COLOR_CHANNEL_MAX else
        (timer * FADE_ALPHA_SCALE / FADE_TICKS).coerceIn(0, COLOR_CHANNEL_MAX)

private const val BACKGROUND_RGB = 0x101010
private const val HORIZONTAL_PADDING = 4
private const val VERTICAL_PADDING = 3
private const val PERCENT_MAX = 100
private const val FADE_TICKS = 10
private const val FADE_ALPHA_SCALE = 256
