package com.skysoft.features.inventory

import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.ItemStackComponentCache
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

internal class InventoryItemSearchQuery private constructor(internal val terms: List<String>) {
    val hasTerms: Boolean
        get() = terms.isNotEmpty()

    fun matches(stack: ItemStack): Boolean = hasTerms && matchesSearchableText(InventoryItemSearchIndex.text(stack))

    fun matchesSearchableText(text: String): Boolean = hasTerms && terms.all(text::contains)

    companion object {
        val EMPTY = InventoryItemSearchQuery(emptyList())

        fun from(text: String): InventoryItemSearchQuery {
            val terms = text.cleanSkyBlockText()
                .lowercase()
                .trim()
                .split(WHITESPACE_PATTERN)
                .filter(String::isNotEmpty)
            return if (terms.isEmpty()) EMPTY else InventoryItemSearchQuery(terms)
        }

        private val WHITESPACE_PATTERN = Regex("""\s+""")
    }
}

internal object InventoryItemSearchIndex {
    private val textCache = ItemStackComponentCache<String>()

    fun text(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return textCache.getOrPut(stack) { searchableText(stack) }
    }

    private fun searchableText(stack: ItemStack): String = buildString {
        append(stack.formattedHoverName().cleanSkyBlockText()).append('\n')
        stack.loreLines().forEach { append(it.cleanSkyBlockText()).append('\n') }
    }.lowercase()

}

internal object InventoryItemSearchHighlight {
    const val OUTLINE_COLOR = 0xFF30FF30.toInt()
    private const val FILL_COLOR = 0x6030FF30
    private const val INSET = 1
    private const val SIZE = 18
    private const val END_OFFSET = SIZE - INSET

    fun render(context: GuiGraphicsExtractor, itemX: Int, itemY: Int) {
        context.fill(
            itemX - INSET,
            itemY - INSET,
            itemX + END_OFFSET,
            itemY + END_OFFSET,
            FILL_COLOR,
        )
    }
}

object ContainerSearchHighlighter {
    private var active = false
    private var query = InventoryItemSearchQuery.EMPTY

    @JvmStatic
    fun toggle(text: String) {
        if (active) {
            active = false
            query = InventoryItemSearchQuery.EMPTY
            return
        }
        active = true
        query = InventoryItemSearchQuery.from(text)
    }

    @JvmStatic
    fun update(text: String) {
        if (!active) return
        query = InventoryItemSearchQuery.from(text)
    }

    @JvmStatic
    fun clear() {
        if (!active) return
        active = false
        query = InventoryItemSearchQuery.EMPTY
    }

    @JvmStatic
    fun isActive(): Boolean = active

    internal fun matches(slot: Slot): Boolean = active && slot.isActive && query.matches(slot.item)

    @JvmStatic
    fun renderBackground(context: GuiGraphicsExtractor, slot: Slot) {
        if (!matches(slot)) return
        InventoryItemSearchHighlight.render(context, slot.x, slot.y)
    }
}
