package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockDropSource
import com.skysoft.data.skyblock.SkyBlockInfoSource
import com.skysoft.data.skyblock.SkyBlockInfoSourceKind
import com.skysoft.data.skyblock.SkyBlockObtainInfo
import com.skysoft.data.skyblock.SkyBlockObtainSource
import com.skysoft.data.skyblock.SkyBlockObtainStatus
import com.skysoft.data.skyblock.SkyBlockRecipe
import com.skysoft.data.skyblock.SkyBlockRecipeType
import com.skysoft.data.skyblock.price.BazaarProductAvailability
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class ItemListObtainSourcesPanel {
    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
    ): List<Pair<Rect, String>> {
        val sources = otherObtainSources(key)
        if (sources.isEmpty()) {
            LegacyTextRenderer.draw(
                context,
                "§8No other obtain sources found",
                bounds.x + CONTENT_INSET,
                bounds.y + EMPTY_MESSAGE_Y,
            )
            return emptyList()
        }
        LegacyTextRenderer.draw(context, "§7Obtained from:", bounds.x + CONTENT_INSET, bounds.y + LABEL_Y)
        val startX = bounds.x + CONTENT_INSET + font.width("Obtained from:") + LABEL_GAP
        val availableWidth = bounds.x + bounds.width - CONTENT_INSET - startX
        val columns = (availableWidth / SLOT_SIZE).coerceAtLeast(1)
        val clickable = mutableListOf<Pair<Rect, String>>()
        sources.forEachIndexed { index, source ->
            val slot = Rect(
                startX + index % columns * SLOT_SIZE,
                bounds.y + index / columns * SLOT_SIZE,
                SLOT_SIZE,
                SLOT_SIZE,
            )
            if (source.kind == SkyBlockInfoSourceKind.ENTITY && source.entityId != null) {
                renderEntityIcon(
                    context,
                    font,
                    slot,
                    source.entityId,
                    dropSources(key, source.entityId),
                    mouseX,
                    mouseY,
                )
                clickable += slot to source.entityId
            } else {
                renderSourceIcon(context, font, slot, source, mouseX, mouseY)
            }
        }
        return clickable
    }

    private fun renderSourceIcon(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        source: SkyBlockInfoSource,
        mouseX: Int,
        mouseY: Int,
    ) {
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, SLOT_BORDER)
        context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, SLOT_FILL)
        context.item(sourceStack(source), bounds.x + 1, bounds.y + 1)
        if (bounds.contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(context, sourceTooltip(font, source), mouseX, mouseY)
        }
    }

    private companion object {
        const val CONTENT_INSET = 8
        const val EMPTY_MESSAGE_Y = 8
        const val LABEL_Y = 5
        const val LABEL_GAP = 5
        const val SLOT_SIZE = 18
        val SLOT_BORDER = 0xFF111315.toInt()
        val SLOT_FILL = 0xD0202428.toInt()
    }
}

internal fun hasOtherObtainSources(key: ItemListEntryKey): Boolean = otherObtainSources(key).isNotEmpty()

internal fun hasBazaarObtainSource(key: ItemListEntryKey): Boolean {
    val isDeclared = SkyBlockDataRepository.info(key)?.enchantment?.sources.orEmpty()
        .any { it.kind == SkyBlockInfoSourceKind.BAZAAR }
    return isDeclared || SkyBlockPriceData.bazaarAvailability(key.id) != BazaarProductAvailability.UNAVAILABLE
}

private fun otherObtainSources(key: ItemListEntryKey): List<SkyBlockInfoSource> {
    val info = SkyBlockDataRepository.info(key) ?: return emptyList()
    val recipes = SkyBlockDataRepository.recipesFor(key)
    val representedEntities = representedObtainEntityIds(recipes)
    val structured = buildList {
        addAll(info.enchantment?.sources.orEmpty().filterNot { it.kind == SkyBlockInfoSourceKind.BAZAAR })
        info.dropSources.forEach { source -> add(entitySource(source.entityId)) }
        info.droppedBy.forEach { entityId -> add(entitySource(entityId)) }
        info.soldBy.forEach { entityId -> add(entitySource(entityId)) }
    }.filterNot { it.entityId in representedEntities }
        .distinctBy { Triple(it.kind, it.displayName, it.entityId) }
    val catalogSource = info.obtain?.takeUnless {
        it.source == SkyBlockObtainSource.STRUCTURED_CATALOG ||
            isCraftingSourceRepresentedByRecipes(it.summary, recipes)
    }
    if (key.id.startsWith("ENCHANTMENT_")) {
        return structured + listOfNotNull(catalogSource?.toInfoSource())
    }
    if (structured.isNotEmpty() || representedEntities.isNotEmpty() || hasBazaarObtainSource(key)) return structured
    return listOfNotNull(catalogSource?.toInfoSource())
}

internal fun representedObtainEntityIds(key: ItemListEntryKey): Set<String> =
    representedObtainEntityIds(SkyBlockDataRepository.recipesFor(key))

internal fun isCraftingSourceRepresentedByRecipes(summary: String, recipes: List<SkyBlockRecipe>): Boolean =
    recipes.any { it.type == SkyBlockRecipeType.CRAFTING } &&
        (summary == "Crafting" || summary.startsWith("Crafting:")) &&
        !OTHER_OBTAIN_METHOD.containsMatchIn(summary)

internal fun representedObtainEntityIds(recipes: List<SkyBlockRecipe>): Set<String> =
    recipes.asSequence()
        .filterIsInstance<SkyBlockRecipe.Process>()
        .filter { it.type == SkyBlockRecipeType.SHOP }
        .mapNotNull(SkyBlockRecipe.Process::sourceId)
        .toSet()

private fun entitySource(entityId: String): SkyBlockInfoSource {
    val entity = SkyBlockDataRepository.entity(entityId)
    return SkyBlockInfoSource(SkyBlockInfoSourceKind.ENTITY, entity?.name ?: entityId, entityId)
}

private fun dropSources(key: ItemListEntryKey, entityId: String): List<SkyBlockDropSource> =
    SkyBlockDataRepository.info(key)?.dropSources.orEmpty().filter { it.entityId == entityId }

private fun sourceStack(source: SkyBlockInfoSource): ItemStack = when (source.kind) {
    SkyBlockInfoSourceKind.BAZAAR -> ItemStack(Items.EMERALD)
    SkyBlockInfoSourceKind.EXPERIMENTATION_TABLE,
    SkyBlockInfoSourceKind.ENCHANTMENT_TABLE,
    -> ItemStack(Items.ENCHANTING_TABLE)
    SkyBlockInfoSourceKind.ENTITY -> error("Entity sources must use the entity renderer")
    SkyBlockInfoSourceKind.CATALOG -> source.itemId?.let(SkyBlockDataRepository::itemKey)
        ?.let(SkyBlockDataRepository::displayStack)
        ?: ItemStack(if (source.obtainStatus == SkyBlockObtainStatus.UNKNOWN) Items.BARRIER else Items.WRITABLE_BOOK)
}

private fun SkyBlockObtainInfo.toInfoSource(): SkyBlockInfoSource = SkyBlockInfoSource(
    kind = SkyBlockInfoSourceKind.CATALOG,
    displayName = when (status) {
        SkyBlockObtainStatus.OBTAINABLE -> "Obtaining"
        SkyBlockObtainStatus.UNOBTAINABLE -> "Unobtainable"
        SkyBlockObtainStatus.UNKNOWN -> "Source unknown"
    },
    itemId = sourceItemId,
    details = listOf(summary),
    obtainStatus = status,
)

private fun sourceTooltip(font: Font, source: SkyBlockInfoSource): List<String> = buildList {
    val color = when (source.obtainStatus) {
        SkyBlockObtainStatus.UNOBTAINABLE -> "§c"
        SkyBlockObtainStatus.UNKNOWN -> "§8"
        else -> "§f"
    }
    add("$color${source.displayName}")
    source.details.forEach { detail -> addAll(wrapTooltipLine(font, "§7$detail", TOOLTIP_WIDTH)) }
}

private fun wrapTooltipLine(font: Font, text: String, maximumWidth: Int): List<String> {
    val words = text.split(' ')
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && font.width(candidate) > maximumWidth) {
            lines += current
            current = "§7$word"
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) lines += current
    return lines
}

private const val TOOLTIP_WIDTH = 220
private val OTHER_OBTAIN_METHOD = Regex(
    """\b(?:obtained\s+(?:as|from|through|via)|purchased\s+(?:at|for|from|through|via)|""" +
        """bought\s+(?:at|for|from)|sold\s+(?:at|by|for|from)|given\s+(?:after|by|for|from|when)|""" +
        """rewarded\s+(?:after|by|for|from|when|with)|dropped\s+(?:by|from)|""" +
        """found\s+(?:at|by|from|in|on)|traded\s+(?:by|for|from|through|via|with)|""" +
        """chance\s+to\s+drop|trading\s+with)\b""",
    RegexOption.IGNORE_CASE,
)
