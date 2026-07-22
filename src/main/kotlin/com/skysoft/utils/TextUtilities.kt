package com.skysoft.utils

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional
import java.util.UUID

object TextUtilities {
    private val resetPattern = Regex("\u00A7r", RegexOption.IGNORE_CASE)
    fun CharSequence.removeColor(): String = ChatFormatting.stripFormatting(toString()).orEmpty()
    fun CharSequence.removeResets(): String = resetPattern.replace(this, "")
    fun CharSequence.cleanSkyBlockText(): String = removeColor().trim()
    fun Component.cleanSkyBlockText(): String = string.cleanSkyBlockText()

    fun CharSequence.truncateLegacyText(maximumLength: Int): String {
        if (removeColor().length <= maximumLength) return toString()
        return buildString {
            var index = 0
            var visibleLength = 0
            while (index < this@truncateLegacyText.length && visibleLength < maximumLength) {
                val character = this@truncateLegacyText[index]
                append(character)
                index++
                if (character == LEGACY_FORMAT_PREFIX && index < this@truncateLegacyText.length) {
                    append(this@truncateLegacyText[index])
                    index++
                } else {
                    visibleLength++
                }
            }
            append("...")
        }
    }

    fun String.parseUUIDOrNull(): UUID? = runCatching {
        if (length == COMPACT_UUID_LENGTH) {
            UUID.fromString(
                substring(UUID_FIRST_GROUP_START, UUID_FIRST_GROUP_END) + "-" +
                    substring(UUID_SECOND_GROUP_START, UUID_SECOND_GROUP_END) + "-" +
                    substring(UUID_THIRD_GROUP_START, UUID_THIRD_GROUP_END) + "-" +
                    substring(UUID_FOURTH_GROUP_START, UUID_FOURTH_GROUP_END) + "-" +
                    substring(UUID_FIFTH_GROUP_START),
            )
        } else {
            UUID.fromString(this)
        }
    }.getOrNull()

    fun Component.formattedText(): String {
        val builder = StringBuilder()
        visit({ style: Style, text: String ->
            builder.append(style.legacyCodes())
            builder.append(text)
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return builder.toString()
    }

    private fun Style.legacyCodes(): String = buildString {
        color?.legacyCode()?.let { append('\u00A7').append(it) }
        if (isObfuscated) append("\u00A7k")
        if (isBold) append("\u00A7l")
        if (isStrikethrough) append("\u00A7m")
        if (isUnderlined) append("\u00A7n")
        if (isItalic) append("\u00A7o")
    }

    private fun TextColor.legacyCode(): Char? = ChatFormatting.entries
        .firstOrNull { TextColor.fromLegacyFormat(it) == this }
        ?.toString()
        ?.last()

    private const val LEGACY_FORMAT_PREFIX = '\u00A7'
    private const val COMPACT_UUID_LENGTH = 32
    private const val UUID_FIRST_GROUP_START = 0
    private const val UUID_FIRST_GROUP_END = 8
    private const val UUID_SECOND_GROUP_START = UUID_FIRST_GROUP_END
    private const val UUID_SECOND_GROUP_END = 12
    private const val UUID_THIRD_GROUP_START = UUID_SECOND_GROUP_END
    private const val UUID_THIRD_GROUP_END = 16
    private const val UUID_FOURTH_GROUP_START = UUID_THIRD_GROUP_END
    private const val UUID_FOURTH_GROUP_END = 20
    private const val UUID_FIFTH_GROUP_START = UUID_FOURTH_GROUP_END
}
