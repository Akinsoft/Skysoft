package com.skysoft.utils.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.StringDecomposer

object LegacyTextRenderer {
    fun width(text: String): Int = Minecraft.getInstance().font.width(text)

    fun draw(
        context: GuiGraphicsExtractor,
        text: String,
        x: Int,
        y: Int,
        shadow: Boolean = true,
        defaultColor: Int = 0xFFFFFFFF.toInt(),
    ) {
        context.text(Minecraft.getInstance().font, text, x, y, defaultColor, shadow)
    }

    fun stripFormatting(text: String): String = text.replace(Regex("§."), "")

    fun formattedSequence(text: String): FormattedCharSequence =
        FormattedCharSequence { sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink) }

    fun wrap(font: Font, text: String, maximumWidth: Int): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && font.width(candidate) > maximumWidth) {
                lines += current
                current = "§7$word"
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
