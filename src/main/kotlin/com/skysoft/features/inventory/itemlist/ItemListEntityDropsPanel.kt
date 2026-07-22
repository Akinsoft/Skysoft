package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockCurrencyStacks
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockEntityDrop
import com.skysoft.data.skyblock.SkyBlockEntityInfo
import com.skysoft.data.skyblock.SkyBlockEntityLootTable
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class ItemListEntityDropsPanel {
    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        grid: ViewerCardGrid,
        entity: SkyBlockEntityInfo,
        page: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Pair<Rect, ItemListEntryKey>> {
        val drops = entityDropListings(entity)
        val visible = drops.drop(page * grid.pageSize).take(grid.pageSize)
        if (visible.isEmpty()) {
            LegacyTextRenderer.draw(context, "§7No drops found", grid.bounds.x + EMPTY_INSET, grid.bounds.y + EMPTY_INSET)
            return emptyList()
        }
        val clickable = mutableListOf<Pair<Rect, ItemListEntryKey>>()
        visible.forEachIndexed { index, listing ->
            val tile = grid.tile(index, visible.size)
            val key = entityDropKey(listing.drop)
            renderDrop(context, font, tile, listing, key, mouseX, mouseY)
            if (key != null) clickable += tile to key
        }
        return clickable
    }

    private fun renderDrop(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        listing: EntityDropListing,
        key: ItemListEntryKey?,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, CARD_BORDER)
        context.fill(
            bounds.x + 1,
            bounds.y + 1,
            bounds.x + bounds.width - 1,
            bounds.y + bounds.height - 1,
            if (hovered) CARD_HOVER else CARD_FILL,
        )
        val icon = Rect(bounds.x + CARD_INSET, bounds.y + (bounds.height - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE)
        renderViewerItem(
            context,
            font,
            entityDropStack(listing.drop, key),
            icon,
            dropAmountDecoration(listing.drop),
        )
        val textX = icon.x + icon.width + TEXT_GAP
        val textWidth = (bounds.x + bounds.width - CARD_INSET - textX).coerceAtLeast(1)
        val name = key?.let(SkyBlockDataRepository::entry)?.displayName ?: listing.drop.displayName
        val lines = buildList {
            add("§f" to name)
            add("§7" to dropAmount(listing.drop))
            listing.drop.chance?.let { add("§a" to "${formatDropChance(it)} chance") }
        }
        val textTop = bounds.y + (bounds.height - font.lineHeight - LINE_HEIGHT * (lines.size - 1)) / 2
        lines.forEachIndexed { index, (color, text) ->
            val visibleText = font.plainSubstrByWidth(text, textWidth)
            LegacyTextRenderer.draw(
                context,
                color + visibleText,
                textX + (textWidth - font.width(visibleText)) / 2,
                textTop + LINE_HEIGHT * index,
            )
        }
        if (hovered) {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                entityDropTooltip(name, listing, key != null),
                mouseX,
                mouseY,
            )
        }
    }
}

internal data class EntityDropListing(
    val table: SkyBlockEntityLootTable,
    val drop: SkyBlockEntityDrop,
)

internal fun entityDropListings(entity: SkyBlockEntityInfo): List<EntityDropListing> =
    entity.lootTables.flatMap { table -> table.drops.map { drop -> EntityDropListing(table, drop) } }

internal fun entityDropCount(entity: SkyBlockEntityInfo): Int = entity.lootTables.sumOf { it.drops.size }

internal fun entityDropKey(drop: SkyBlockEntityDrop): ItemListEntryKey? = drop.itemId
    ?.let(SkyBlockDataRepository::itemKey)
    ?.takeIf { SkyBlockDataRepository.entry(it) != null }

private fun entityDropStack(drop: SkyBlockEntityDrop, key: ItemListEntryKey?): ItemStack {
    val amount = drop.maxAmount.coerceAtLeast(1L)
    return drop.currency?.let { SkyBlockCurrencyStacks.supportedStack(it, amount) }
        ?: key?.let(SkyBlockDataRepository::displayStack)
        ?: ItemStack(Items.PAPER).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal(drop.displayName).withStyle { it.withItalic(false) })
        }
}

private fun entityDropTooltip(
    name: String,
    listing: EntityDropListing,
    canOpen: Boolean,
): List<String> = buildList {
    add("§f$name")
    add("§7${dropAmount(listing.drop)}")
    listing.drop.chance?.let { add("§7Drop chance: §f${formatDropChance(it)}") }
    add("§7Source: §f${listing.table.name}")
    listing.table.mobLevel?.let { add("§7Mob level: §f${ItemListFormatting.number(it.toLong())}") }
    listing.drop.details.forEach { add("§7$it") }
    if (canOpen) {
        add("")
        add("§e§lCLICK TO VIEW")
    }
}

private fun dropAmount(drop: SkyBlockEntityDrop): String = if (drop.minAmount == drop.maxAmount) {
    "Amount: ${ItemListFormatting.number(drop.minAmount)}"
} else {
    "Amount: ${ItemListFormatting.number(drop.minAmount)}–${ItemListFormatting.number(drop.maxAmount)}"
}

private fun dropAmountDecoration(drop: SkyBlockEntityDrop): String? = drop.maxAmount.takeIf { it > 1L }
    ?.let(ItemListFormatting::compactNumber)

private const val CARD_INSET = 5
private const val ICON_SIZE = 18
private const val TEXT_GAP = 4
private const val LINE_HEIGHT = 10
private const val EMPTY_INSET = 8
private val CARD_BORDER = 0xFF111315.toInt()
private val CARD_FILL = 0xD0202428.toInt()
private val CARD_HOVER = 0xD02E3A42.toInt()
