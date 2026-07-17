package com.skysoft.utils

import com.skysoft.SkysoftMod
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
    private const val PREFIX_LEFT = 0x1A87C4
    private const val PREFIX_RIGHT = 0x2BB1FB
    private const val MESSAGE_LEFT = 0xE8E8E8
    private const val MESSAGE_RIGHT = 0xFFFFFF

    fun chat(message: String): ChatDeliveryResult =
        chat(Component.literal(message))

    fun success(message: String): ChatDeliveryResult =
        chat(message)

    fun error(message: String): ChatDeliveryResult =
        chat(message)

    fun link(message: String, url: String, hover: String = "Open $url"): ChatDeliveryResult =
        chat(
            Component.literal(message).withStyle {
                it.withUnderlined(true)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(hover).withStyle(ChatFormatting.GRAY)))
            },
        )

    fun chat(message: Component): ChatDeliveryResult {
        val text = prefixed(message)
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null) {
            SkysoftMod.LOGGER.info(text.string)
            return ChatDeliveryResult.LOGGED_ONLY
        }
        MinecraftClient.chat(minecraft).addClientSystemMessage(text)
        return ChatDeliveryResult.DELIVERED
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
        Component.empty().append(prefix()).append(gradient(message, MESSAGE_LEFT, MESSAGE_RIGHT))

    private fun prefix(): MutableComponent =
        gradient(Component.literal("[Skysoft] "), PREFIX_LEFT, PREFIX_RIGHT).withStyle(ChatFormatting.BOLD)

    private fun gradient(component: Component, start: Int, end: Int): MutableComponent {
        val result = Component.empty()
        val lastIndex = (component.string.length - 1).coerceAtLeast(1)
        var index = 0
        component.visit({ style: Style, text: String ->
            text.forEach { char ->
                val progress = index++.toFloat() / lastIndex
                val color = style.color ?: TextColor.fromRgb(mix(start, end, progress))
                result.append(
                    Component.literal(char.toString()).withStyle(
                        style.withColor(color),
                    ),
                )
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return result
    }

    private fun mix(start: Int, end: Int, progress: Float): Int {
        val r = channel(start, end, RED_SHIFT, progress)
        val g = channel(start, end, GREEN_SHIFT, progress)
        val b = channel(start, end, BLUE_SHIFT, progress)
        return (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }

    private fun channel(start: Int, end: Int, shift: Int, progress: Float): Int {
        val from = (start shr shift) and RGB_CHANNEL_MASK
        val to = (end shr shift) and RGB_CHANNEL_MASK
        return (from + (to - from) * progress).toInt().coerceIn(RGB_CHANNEL_MIN, RGB_CHANNEL_MAX)
    }

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val RGB_CHANNEL_MASK = 0xFF
    private const val RGB_CHANNEL_MIN = 0
    private const val RGB_CHANNEL_MAX = 255
}

enum class ChatDeliveryResult {
    DELIVERED,
    LOGGED_ONLY,
}
