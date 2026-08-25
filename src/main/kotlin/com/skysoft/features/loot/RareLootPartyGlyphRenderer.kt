package com.skysoft.features.loot

import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.utils.chat.ChatMessage
import com.skysoft.utils.chat.ChatMessageType
import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style

internal object RareLootPartyGlyphRenderer {
    fun render(message: ChatMessage): Component {
        if (message.type != ChatMessageType.PARTY && message.type != ChatMessageType.GUILD) return message.component
        if (!rareLootWithMagicFindPattern.matches(message.body)) return message.component
        return message.component.replaceTextSegments(MAGIC_FIND_SUFFIX, RENDERED_MAGIC_FIND_SUFFIX)
    }

    private fun Component.replaceTextSegments(target: String, replacement: String): Component {
        val result = Component.empty()
        visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                result.append(Component.literal(text.replace(target, replacement)).withStyle(style))
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private const val MAGIC_FIND_SUFFIX = " MF)"
    private val RENDERED_MAGIC_FIND_SUFFIX = " ${SkyBlockStatGlyph.MAGIC_FIND} MF)"
    private val rareLootWithMagicFindPattern = Regex(
        """^(?:(?:VERY|CRAZY)\s+)?(?:RARE|LOOTSHARE) DROP! .+\([^)]*\bMF\)(?:\s+.*)?$""",
        RegexOption.IGNORE_CASE,
    )
}
