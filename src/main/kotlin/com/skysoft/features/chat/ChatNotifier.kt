package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageClassifier
import com.skysoft.utils.chat.ChatMessageType
import com.skysoft.utils.chat.PrivateMessageDirection
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.gui.editors.TextListEntry
import java.util.Optional
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

object ChatNotifier {
    fun decorate(content: Component): Component {
        val config = SkysoftConfigGui.config().chat.notify
        return decorate(
            content = content,
            entries = config.settings.words.get(),
            isEnabled = config.enabled,
            isOwnMessagesIgnored = config.settings.isOwnMessagesIgnored,
            ownPlayerName = Minecraft.getInstance().player?.gameProfile?.name,
            playPing = ::playPing,
        )
    }

    internal fun decorate(
        content: Component,
        entries: List<TextListEntry>,
        isEnabled: Boolean,
        isOwnMessagesIgnored: Boolean = false,
        ownPlayerName: String? = null,
        playPing: (String, Float) -> Unit,
    ): Component {
        if (!isEnabled) return content
        if (entries.isEmpty()) return content
        if (isOwnMessagesIgnored) {
            val message = ChatMessageClassifier.classify(SkysoftMessage(content, SkysoftMessageSource.GAME))
            if (isOwnMessage(message, ownPlayerName)) return content
        }
        val plainText = content.string
        val matchingEntries = entries.mapIndexedNotNull { index, entry ->
            val text = entry.text.trim()
            if (text.isEmpty() || !plainText.contains(text, ignoreCase = true)) {
                null
            } else {
                MatchingNotificationEntry(entry, text, index)
            }
        }
        var pingVolumePercent = 0f
        var pingVolume = 0f
        var pingSound = SoundUtilities.CHAT_NOTIFY_DEFAULT_SOUND_ID
        matchingEntries.forEach { match ->
            val entry = match.entry
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
        return if (matchingEntries.isEmpty()) content else highlight(content, matchingEntries)
    }

    internal fun isOwnMessage(message: ChatMessage, ownPlayerName: String?): Boolean {
        return when (message.type) {
            ChatMessageType.ALL,
            ChatMessageType.PARTY,
            ChatMessageType.GUILD,
            ChatMessageType.COOP,
            -> ownPlayerName != null && message.sender?.name.equals(ownPlayerName, ignoreCase = true)
            ChatMessageType.PRIVATE_MESSAGE -> message.privateMessageDirection == PrivateMessageDirection.TO
            else -> false
        }
    }

    private fun highlight(content: Component, entries: List<MatchingNotificationEntry>): Component {
        val ranges = resolveHighlightRanges(content.string, entries)
        val result = Component.empty()
        var segmentStart = 0
        content.visit(
            FormattedText.StyledContentConsumer<Unit> { style, segment ->
                appendHighlighted(result, segment, segmentStart, style, ranges)
                segmentStart += segment.length
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private fun resolveHighlightRanges(
        content: String,
        entries: List<MatchingNotificationEntry>,
    ): List<HighlightRange> {
        val candidates = buildList {
            entries.forEach { entry ->
                var searchStart = 0
                while (searchStart < content.length) {
                    val start = content.indexOf(entry.text, searchStart, ignoreCase = true)
                    if (start < 0) break
                    add(HighlightRange(start, start + entry.text.length, entry.entry.colour, entry.index))
                    searchStart = start + 1
                }
            }
        }
        val accepted = mutableListOf<HighlightRange>()
        candidates.sortedWith(
            compareByDescending<HighlightRange> { it.endExclusive - it.start }
                .thenBy(HighlightRange::entryIndex)
                .thenBy(HighlightRange::start),
        ).forEach { candidate ->
            if (accepted.none(candidate::overlaps)) accepted += candidate
        }
        return accepted.sortedBy(HighlightRange::start)
    }

    private fun appendHighlighted(
        result: MutableComponent,
        segment: String,
        segmentStart: Int,
        style: Style,
        ranges: List<HighlightRange>,
    ) {
        val segmentEnd = segmentStart + segment.length
        var cursor = 0
        ranges.forEach { range ->
            if (range.endExclusive <= segmentStart || range.start >= segmentEnd) return@forEach
            val matchStart = maxOf(range.start, segmentStart) - segmentStart
            val matchEnd = minOf(range.endExclusive, segmentEnd) - segmentStart
            if (matchStart > cursor) {
                result.append(Component.literal(segment.substring(cursor, matchStart)).withStyle(style))
            }
            result.append(
                Component.literal(segment.substring(matchStart, matchEnd))
                    .withStyle(ChatNotifyChromaRendering.apply(style, range.colour).withBold(true)),
            )
            cursor = matchEnd
        }
        if (cursor < segment.length) {
            result.append(Component.literal(segment.substring(cursor)).withStyle(style))
        }
    }

    private data class MatchingNotificationEntry(
        val entry: TextListEntry,
        val text: String,
        val index: Int,
    )

    private data class HighlightRange(
        val start: Int,
        val endExclusive: Int,
        val colour: ChromaColour,
        val entryIndex: Int,
    ) {
        fun overlaps(other: HighlightRange): Boolean = start < other.endExclusive && other.start < endExclusive
    }

    private fun playPing(sound: String, volume: Float) {
        SoundUtilities.playUiSound(sound, PING_PITCH, volume)
    }

    private const val PING_PITCH = 1.0f
}
