package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SoundUtilities
import io.github.notenoughupdates.moulconfig.gui.editors.TextListEntry
import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object ChatNotifier {
    fun decorate(content: Component): Component {
        val config = SkysoftConfigGui.config().chat.notify
        return decorate(content, config.words.get(), config.enabled, ::playPing)
    }

    internal fun decorate(
        content: Component,
        entries: List<TextListEntry>,
        isEnabled: Boolean,
        playPing: (String, Float) -> Unit,
    ): Component {
        if (!isEnabled || entries.isEmpty()) return content
        var decorated = content
        var pingVolumePercent = 0f
        var pingVolume = 0f
        var pingSound = SoundUtilities.CHAT_NOTIFY_DEFAULT_SOUND_ID
        entries.forEach { entry ->
            val text = entry.text.trim()
            if (text.isEmpty() || !content.string.contains(text, ignoreCase = true)) return@forEach
            decorated = highlight(decorated, text, entry.colour.getEffectiveColourRGB())
            if (entry.isSoundEnabled) {
                val volume = entry.soundVolumePercent.coerceIn(
                    TextListEntry.MIN_SOUND_VOLUME_PERCENT,
                    TextListEntry.MAX_SOUND_VOLUME_PERCENT,
                )
                if (volume > pingVolumePercent) {
                    pingVolumePercent = volume
                    pingVolume = entry.playbackVolume
                    pingSound = entry.sound.ifBlank { SoundUtilities.CHAT_NOTIFY_DEFAULT_SOUND_ID }
                }
            }
        }
        if (pingVolume > 0f) playPing(pingSound, pingVolume)
        return decorated
    }

    private fun highlight(content: Component, text: String, colour: Int): Component {
        val result = Component.empty()
        content.visit(
            FormattedText.StyledContentConsumer<Unit> { style, segment ->
                appendHighlighted(result, segment, style, text, colour)
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private fun appendHighlighted(result: MutableComponent, segment: String, style: Style, text: String, colour: Int) {
        var start = 0
        while (start < segment.length) {
            val match = segment.indexOf(text, start, ignoreCase = true)
            if (match < 0) {
                result.append(Component.literal(segment.substring(start)).withStyle(style))
                return
            }
            if (match > start) result.append(Component.literal(segment.substring(start, match)).withStyle(style))
            result.append(Component.literal(segment.substring(match, match + text.length)).withStyle(style.withColor(colour)))
            start = match + text.length
        }
    }

    private fun playPing(sound: String, volume: Float) {
        SoundUtilities.playUiSound(sound, PING_PITCH, volume)
    }

    private const val PING_PITCH = 1.0f
}
