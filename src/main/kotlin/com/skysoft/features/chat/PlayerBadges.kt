package com.skysoft.features.chat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.SkysoftMessage
import com.skysoft.utils.SkysoftMessageSource
import com.skysoft.utils.chat.ChatMessageClassifier
import com.skysoft.utils.chat.PrivateMessageDirection
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer
import java.util.Locale

object PlayerBadges {
    private var badgesByPlayerName: Map<String, Component> = emptyMap()

    fun register() {
        TabListApi.onChange(
            "Player Badges",
            isActive = ::isEnabled,
            listener = ::updateBadges,
        )
    }

    fun decorate(content: Component): Component {
        if (!isEnabled()) return content
        val message = ChatMessageClassifier.classify(SkysoftMessage(content, SkysoftMessageSource.GAME))
        val sender = message.sender
            ?.takeUnless { message.privateMessageDirection == PrivateMessageDirection.TO }
            ?: return content
        val badges = badgesByPlayerName[playerKey(sender.name)] ?: return content
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

    private fun updateBadges() {
        val next = mutableMapOf<String, Component>()
        val seenPlayerNames = mutableSetOf<String>()
        for (entry in TabListApi.entries) {
            val playerName = entry.skyBlockPlayerName ?: continue
            val key = playerKey(playerName)
            if (!seenPlayerNames.add(key)) continue
            val badges = entry.displayName.badges().takeUnless { it.string.isEmpty() } ?: continue
            next[key] = badges
        }
        badgesByPlayerName = next
    }

    private fun isEnabled(): Boolean = SkysoftConfigGui.config().chat.playerBadges.enabled

    private fun playerKey(name: String): String = name.lowercase(Locale.ROOT)

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
