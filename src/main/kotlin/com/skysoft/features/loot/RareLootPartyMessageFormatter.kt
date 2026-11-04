package com.skysoft.features.loot

import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.utils.NumberUtilities.coinFormat

internal object RareLootPartyMessageFormatter {
    fun format(
        drop: RareLootDrop,
        value: Double?,
        lootshare: Boolean,
        dropCount: RareLootDropCount?,
    ): String = buildString {
        append(if (lootshare) "LOOTSHARE DROP!" else "RARE DROP!")
        append(' ')
        append(drop.itemText())
        drop.context?.trimParentheses()?.takeIf { it.isNotBlank() }?.let { context ->
            append(" (")
            append(SkyBlockStatGlyph.forServerChat(context))
            append(')')
        }
        dropCount?.takeIf { it.count > 0L }?.let { count ->
            append(" #")
            append(count.count)
        }
        if (value != null) {
            append(" (+")
            append(value.coinFormat())
            append(" coins)")
        }
    }

    private fun RareLootDrop.itemText(): String =
        if (amount > 1) "${amount}x $displayName" else displayName
}

internal data class RareLootDropCount(
    val count: Long,
)
