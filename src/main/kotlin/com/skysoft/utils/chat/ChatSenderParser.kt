package com.skysoft.utils.chat

import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Optional
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

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
        senderBeforeMarker(message, marker)

    private fun senderBeforeMarker(message: String, marker: String): ChatMessageSender? {
        val prefix = message.substringBefore(marker, "").trim().removeSuffix(":").trim()
        if (prefix.isBlank()) return null
        val sender = prefix.substringAfterLast(">").trim().removeSuffix(":").trim()
        val rawName = senderPattern.find(sender)?.groups["name"]?.value ?: return null
        return ChatMessageSender(rawName.cleanSkyBlockText(), rawName.legacyColor())
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
                ChatFormatting.getByCode(this[index + 1])
                    ?.let(TextColor::fromLegacyFormat)
                    ?.let { color = it.value }
                index += LEGACY_FORMATTING_CODE_LENGTH
            } else {
                index++
            }
        }
        return color
    }

    private val senderPattern = Regex("""(?<name>(?:§.)*[A-Za-z0-9_]{1,16})(?:§.)*$""")
    private const val LEGACY_FORMATTING_CODE_LENGTH = 2
}
