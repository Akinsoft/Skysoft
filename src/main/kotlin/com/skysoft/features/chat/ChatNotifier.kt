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
        var decorated = content
        var pingVolumePercent = 0f
        var pingVolume = 0f
        var pingSound = SoundUtilities.CHAT_NOTIFY_DEFAULT_SOUND_ID
        entries.forEach { entry ->
            val text = entry.text.trim()
            if (text.isEmpty()) return@forEach
            if (!content.string.contains(text, ignoreCase = true)) return@forEach
            decorated = highlight(decorated, text, entry.colour)
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

    private fun highlight(content: Component, text: String, colour: ChromaColour): Component {
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

    private fun appendHighlighted(
        result: MutableComponent,
        segment: String,
        style: Style,
        text: String,
        colour: ChromaColour,
    ) {
        var start = 0
        while (start < segment.length) {
            val match = segment.indexOf(text, start, ignoreCase = true)
            if (match < 0) {
                result.append(Component.literal(segment.substring(start)).withStyle(style))
                return
            }
            if (match > start) result.append(Component.literal(segment.substring(start, match)).withStyle(style))
            result.append(
                Component.literal(segment.substring(match, match + text.length))
                    .withStyle(ChatNotifyChromaRendering.apply(style, colour).withBold(true)),
            )
            start = match + text.length
        }
    }

    private fun playPing(sound: String, volume: Float) {
        SoundUtilities.playUiSound(sound, PING_PITCH, volume)
    }

    private const val PING_PITCH = 1.0f
}
