package com.skysoft.features.chat

import com.skysoft.config.ChatTimestampFormat
import com.skysoft.config.SkysoftConfigGui
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

object ChatTimestamps {
    fun decorate(content: Component): Component {
        val config = SkysoftConfigGui.config().chat.timestamps
        return decorate(content, LocalTime.now(), config.enabled, config.settings.format)
    }

    internal fun decorate(
        content: Component,
        time: LocalTime,
        isEnabled: Boolean,
        format: ChatTimestampFormat,
    ): Component {
        if (!isEnabled) return content
        val timestamp = Component.literal("[${formatters.getValue(format).format(time)}] ")
            .withStyle(ChatFormatting.DARK_GRAY)
        return Component.empty().append(timestamp).append(content)
    }

    fun originalContent(content: Component): Component {
        val siblings = content.siblings
        if (siblings.size < 2 || !timestampPattern.matches(siblings.first().string)) return content
        if (siblings.size == 2) return siblings[1]
        return Component.empty().also { original -> siblings.drop(1).forEach(original::append) }
    }

    private val formatters = ChatTimestampFormat.entries.associateWith { format ->
        DateTimeFormatter.ofPattern(format.pattern, Locale.ENGLISH)
    }
    private val timestampPattern = Regex(
        """^\[(?:\d{2}:\d{2}(?::\d{2})?|\d{1,2}:\d{2}(?::\d{2})? [AP]M)] $""",
    )
}
