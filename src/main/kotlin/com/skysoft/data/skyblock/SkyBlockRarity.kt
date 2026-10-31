package com.skysoft.data.skyblock

import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.TextUtilities.formattedText
import net.minecraft.network.chat.Component
import java.awt.Color

enum class SkyBlockRarity(
    val id: Int,
    val chatColorCode: String,
    val color: Color,
) {
    COMMON(0, "\u00A7f", Color(255, 255, 255)),
    UNCOMMON(1, "\u00A7a", Color(85, 255, 85)),
    RARE(2, "\u00A79", Color(85, 85, 255)),
    EPIC(3, "\u00A75", Color(170, 0, 170)),
    LEGENDARY(4, "\u00A76", Color(255, 170, 0)),
    MYTHIC(5, "\u00A7d", Color(255, 85, 255)),
    DIVINE(6, "\u00A7b", Color(85, 255, 255)),
    SPECIAL(7, "\u00A7c", Color(255, 85, 85)),
    VERY_SPECIAL(8, "\u00A7c", Color(255, 85, 85)),
    ;

    val rgb: Int = color.rgb and RGB_MASK

    fun oneAbove(): SkyBlockRarity? = getById(id + 1)

    companion object {
        fun getById(id: Int): SkyBlockRarity? = entries.firstOrNull { it.id == id }

        fun getByColorCode(code: Char): SkyBlockRarity? = entries.firstOrNull {
            it.chatColorCode.last().equals(code, ignoreCase = true)
        }

        fun getByName(name: String): SkyBlockRarity? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

        fun getByComponent(component: Component, containedText: String): SkyBlockRarity? {
            val formatted = component.formattedText()
            val index = formatted.indexOf(containedText)
            if (index < 0) return null
            for (i in index - 1 downTo 1) {
                if (formatted[i - 1] == '\u00A7') {
                    getByColorCode(formatted[i])?.let { return it }
                }
            }
            val color = component.style.color?.value ?: return null
            return entries.firstOrNull { rarity -> rarity.rgb == color }
        }
    }
}
