package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.hypixel.TabListEntry
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import com.skysoft.utils.chat.ChatMessageClassifier
import com.skysoft.utils.chat.PrivateMessageDirection
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer

object PlayerBadges {
    fun register() {
        TabListApi.registerConsumer("Player Badges", ::isEnabled)
    }

    fun decorate(content: Component): Component =
        if (isEnabled()) decorate(content, TabListApi.entries) else content

    internal fun decorate(content: Component, entries: List<TabListEntry>): Component {
        val message = ChatMessageClassifier.classify(SkysoftMessage(content, SkysoftMessageSource.GAME))
        val sender = message.sender
            ?.takeUnless { message.privateMessageDirection == PrivateMessageDirection.TO }
            ?: return content
        val badges = entries.firstOrNull { it.skyBlockPlayerName.equals(sender.name, ignoreCase = true) }
            ?.displayName
            ?.badges()
            ?.takeUnless { it.string.isEmpty() }
            ?: return content
        val separator = content.string.indexOf(": ").takeIf { it >= 0 } ?: return content
        val senderEnd = content.string.lastIndexOf(sender.name, separator)
            .takeIf { it >= 0 }
            ?.plus(sender.name.length)
            ?: return content
        return if (content.string.substring(senderEnd, separator).any { it.code in badgeCodePoints }) {
            content
        } else {
            content.insert(senderEnd, Component.literal(" ").append(badges))
        }
    }

    private fun isEnabled(): Boolean = SkysoftConfigGui.config().chat.playerBadges.enabled

    private fun Component.badges(): Component = Component.empty().also { result ->
        StringDecomposer.iterateFormatted(this, Style.EMPTY) { _, style, codePoint ->
            if (codePoint in badgeCodePoints) {
                result.append(Component.literal(codePoint.toChar().toString()).withStyle(style))
            }
            true
        }
    }

    private fun Component.insert(index: Int, insertion: Component): Component = Component.empty().also { result ->
        var offset = 0
        var inserted = false
        for (part in toFlatList()) {
            val localIndex = (index - offset).takeIf { !inserted && it <= part.string.length }
            if (localIndex == null) {
                result.append(part)
            } else {
                result.append(Component.literal(part.string.take(localIndex)).withStyle(part.style))
                result.append(insertion)
                result.append(Component.literal(part.string.drop(localIndex)).withStyle(part.style))
                inserted = true
            }
            offset += part.string.length
        }
    }

    private val badgeCodePoints = setOf('♲'.code, '☀'.code, 'Ⓑ'.code, '⚒'.code, 'ቾ'.code)
}
