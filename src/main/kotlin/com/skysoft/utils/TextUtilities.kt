package com.skysoft.utils

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
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

    fun nonItalicText(text: String): MutableComponent = Component.literal(text).withStyle { it.withItalic(false) }

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
        UUID.fromString(if (length == COMPACT_UUID_LENGTH) replace(COMPACT_UUID_PATTERN, "$1-$2-$3-$4-$5") else this)
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
    private val COMPACT_UUID_PATTERN = Regex("(.{8})(.{4})(.{4})(.{4})(.{12})")
}
