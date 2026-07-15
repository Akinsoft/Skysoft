package com.skysoft.utils.chat

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.ChatFormatting
import java.util.Optional

data class ChatMessageSender(
    val name: String,
    val color: Int?,
) {
    fun nameComponent(): Component {
        val component = Component.literal(name)
        return if (color != null) {
            component.withColor(color)
        } else {
            component.withStyle(ChatFormatting.WHITE)
        }
    }
}

object ChatSenderParser {
    fun senderBefore(message: ChatMessage, marker: String): ChatMessageSender? =
        senderBefore(message.component, marker)
            ?: senderBefore(message.cleanText, marker)
            ?: message.sender

    fun senderBefore(component: Component, marker: String): ChatMessageSender? {
        val sender = senderBeforeMarker(component.string, marker) ?: return null
        return ChatMessageSender(sender.name, colorAtText(component, sender.name, marker) ?: sender.color)
    }

    fun senderBefore(message: String, marker: String): ChatMessageSender? =
        senderBeforeMarker(message, marker)?.let { ChatMessageSender(it.name, it.color) }

    private fun senderBeforeMarker(message: String, marker: String): ParsedSender? {
        val prefix = message.substringBefore(marker, "").trim().removeSuffix(":").trim()
        if (prefix.isBlank()) return null
        val sender = prefix.substringAfterLast(">").trim().removeSuffix(":").trim()
        val rawName = senderPattern.find(sender)?.groups["name"]?.value ?: return null
        return ParsedSender(rawName.cleanSkyBlockText(), rawName.legacyColor())
    }

    private fun colorAtText(component: Component, text: String, marker: String): Int? {
        val messageText = component.string
        val markerIndex = messageText.indexOf(marker)
            .takeIf { it >= 0 }
            ?: messageText.length
        val textStart = messageText.lastIndexOf(text, markerIndex)
        if (textStart < 0) return null

        var cursor = 0
        var color: Int? = null
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, segment ->
                val segmentEnd = cursor + segment.length
                if (textStart in cursor until segmentEnd) {
                    color = style.color?.value
                }
                cursor = segmentEnd
                Optional.empty()
            },
            Style.EMPTY,
        )
        return color
    }

    private fun String.legacyColor(): Int? {
        var color: Int? = null
        var index = 0
        while (index < lastIndex) {
            if (this[index] == '§') {
                legacyColors[this[index + 1].lowercaseChar()]?.let { color = it }
                index += LEGACY_FORMATTING_CODE_LENGTH
            } else {
                index++
            }
        }
        return color
    }

    private data class ParsedSender(
        val name: String,
        val color: Int?,
    )

    private val senderPattern = Regex("""(?<name>(?:§.)*[A-Za-z0-9_]{1,16})(?:§.)*$""")
    private const val LEGACY_FORMATTING_CODE_LENGTH = 2
    private val legacyColors = mapOf(
        '0' to 0x000000,
        '1' to 0x0000AA,
        '2' to 0x00AA00,
        '3' to 0x00AAAA,
        '4' to 0xAA0000,
        '5' to 0xAA00AA,
        '6' to 0xFFAA00,
        '7' to 0xAAAAAA,
        '8' to 0x555555,
        '9' to 0x5555FF,
        'a' to 0x55FF55,
        'b' to 0x55FFFF,
        'c' to 0xFF5555,
        'd' to 0xFF55FF,
        'e' to 0xFFFF55,
        'f' to 0xFFFFFF,
    )
}
