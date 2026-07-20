package com.skysoft.utils

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional
import java.util.UUID

object TextUtilities {
    private val colorPattern = Regex("\u00A7[0-9a-fk-or]", RegexOption.IGNORE_CASE)
    private val resetPattern = Regex("\u00A7r", RegexOption.IGNORE_CASE)
    private val legacyColorCodes = mapOf(
        0x000000 to '0',
        0x0000AA to '1',
        0x00AA00 to '2',
        0x00AAAA to '3',
        0xAA0000 to '4',
        0xAA00AA to '5',
        0xFFAA00 to '6',
        0xAAAAAA to '7',
        0x555555 to '8',
        0x5555FF to '9',
        0x55FF55 to 'a',
        0x55FFFF to 'b',
        0xFF5555 to 'c',
        0xFF55FF to 'd',
        0xFFFF55 to 'e',
        0xFFFFFF to 'f',
    )

    fun CharSequence.removeColor(): String = colorPattern.replace(this, "")
    fun CharSequence.removeResets(): String = resetPattern.replace(this, "")
    fun CharSequence.cleanSkyBlockText(): String = removeColor().removeResets().trim()
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

    private fun TextColor.legacyCode(): Char? = legacyColorCodes[value]

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
