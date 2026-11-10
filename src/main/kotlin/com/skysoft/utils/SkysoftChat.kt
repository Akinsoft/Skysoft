package com.skysoft.utils

import com.skysoft.SkysoftMod
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MASK
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MIN
import com.skysoft.utils.ColorUtilities.GREEN_SHIFT
import com.skysoft.utils.ColorUtilities.RED_SHIFT
import com.skysoft.utils.ColorUtilities.packRgb
import java.net.URI
import java.util.Optional
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object SkysoftChat {
    internal const val BRAND_BLUE = 0x2BB1FB
    internal const val PREFIX_LEFT = 0x1A87C4
    internal const val PREFIX_RIGHT = BRAND_BLUE
    internal const val MESSAGE_GRADIENT_START = 0xE8E8E8
    internal const val MESSAGE_GRADIENT_END = 0xFFFFFF

    fun chat(message: String): Boolean =
        chat(Component.literal(message))

    fun success(message: String): Boolean =
        chat(message)

    fun error(message: String): Boolean =
        chat(message)

    fun link(message: String, url: String, hover: String = "Open $url"): Boolean =
        chat(
            Component.literal(message).withStyle {
                it.withUnderlined(true)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(hover).withStyle(ChatFormatting.GRAY)))
            },
        )

    fun chat(message: Component): Boolean {
        val text = prefixed(message)
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null) {
            SkysoftMod.LOGGER.info(text.string)
            return false
        }
        MinecraftClient.chat(minecraft).addClientSystemMessage(text)
        return true
    }

    fun feedback(source: FabricClientCommandSource, message: String) {
        source.sendFeedback(prefixed(Component.literal(message)))
    }

    fun feedback(source: FabricClientCommandSource, message: Component) {
        source.sendFeedback(prefixed(message))
    }

    fun error(source: FabricClientCommandSource, message: String) {
        source.sendError(prefixed(Component.literal(message)))
    }

    fun prefixed(message: Component): MutableComponent =
        Component.empty().append(prefix()).append(gradient(message, MESSAGE_GRADIENT_START, MESSAGE_GRADIENT_END))

    private fun prefix(): MutableComponent =
        gradient(Component.literal("[Skysoft] "), PREFIX_LEFT, PREFIX_RIGHT).withStyle(ChatFormatting.BOLD)

    internal fun gradient(component: Component, start: Int, end: Int): MutableComponent {
        val result = Component.empty()
        val plainText = component.string
        val lastIndex = (plainText.codePointCount(0, plainText.length) - 1).coerceAtLeast(1)
        var index = 0
        component.visit({ style: Style, text: String ->
            text.codePoints().forEach { codePoint ->
                val progress = index++.toFloat() / lastIndex
                val color = style.color ?: TextColor.fromRgb(mix(start, end, progress))
                result.append(
                    Component.literal(String(Character.toChars(codePoint))).withStyle(
                        style.withColor(color),
                    ),
                )
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return result
    }

    internal fun mix(start: Int, end: Int, progress: Float): Int {
        val r = channel(start, end, RED_SHIFT, progress)
        val g = channel(start, end, GREEN_SHIFT, progress)
        val b = channel(start, end, BLUE_SHIFT, progress)
        return packRgb(r, g, b)
    }

    private fun channel(start: Int, end: Int, shift: Int, progress: Float): Int {
        val from = (start shr shift) and COLOR_CHANNEL_MASK
        val to = (end shr shift) and COLOR_CHANNEL_MASK
        return (from + (to - from) * progress).toInt().coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX)
    }

    private const val BLUE_SHIFT = 0
}
