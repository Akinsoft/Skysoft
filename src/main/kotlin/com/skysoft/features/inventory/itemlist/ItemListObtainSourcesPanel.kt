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
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class ItemListObtainSourcesPanel {
    private var huntingKey: ItemListEntryKey? = null
    private var huntingPage = 0
    private var huntingLayout: ItemListHuntingLayout? = null

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
        detailMarkers: List<String>? = null,
    ): List<Pair<Rect, String>> {
        val sources = otherObtainSources(key, detailMarkers)
        if (sources.isEmpty()) {
            LegacyTextRenderer.draw(
                context,
                "§8No other obtain sources found",
                bounds.x + CONTENT_INSET,
                bounds.y + EMPTY_MESSAGE_Y,
            )
            return emptyList()
        }
        if (detailMarkers != null) {
            return renderHuntingSources(context, font, bounds, key, sources, mouseX, mouseY)
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

    private fun renderHuntingSources(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        sources: List<SkyBlockInfoSource>,
        mouseX: Int,
        mouseY: Int,
    ): List<Pair<Rect, String>> {
        LegacyTextRenderer.draw(context, "§7Hunt these mobs:", bounds.x + CONTENT_INSET, bounds.y + LABEL_Y)
        if (huntingKey != key) {
            huntingKey = key
            huntingPage = 0
        }
        val layout = ItemListHuntingLayout.create(bounds, sources.size, huntingPage)
        huntingPage = layout.page
        huntingLayout = layout
        val visibleSources = sources.drop(layout.page * layout.pageSize).take(layout.pageSize)
        val clickable = mutableListOf<Pair<Rect, String>>()
        layout.cards.zip(visibleSources).forEach { (card, source) ->
            context.fill(card.x, card.y, card.x + card.width, card.y + card.height, SLOT_BORDER)
            context.fill(card.x + 1, card.y + 1, card.x + card.width - 1, card.y + card.height - 1, SLOT_FILL)
            val entityId = source.entityId
            if (source.kind == SkyBlockInfoSourceKind.ENTITY && entityId != null) {
                renderHuntingEntity(context, font, card, key, entityId, mouseX, mouseY)
                clickable += card to entityId
            } else {
                renderSourceIcon(context, font, huntingCardIcon(card), source, mouseX, mouseY)
                LegacyTextRenderer.draw(
                    context,
                    font.plainSubstrByWidth(source.displayName, card.width - HUNTING_TEXT_INSET),
                    card.x + HUNTING_TEXT_X,
                    card.y + HUNTING_NAME_Y,
                )
            }
        }
        if (layout.pageCount > 1) renderHuntingNavigation(context, font, layout, mouseX, mouseY)
        return clickable
    }

    fun clickHunting(key: ItemListEntryKey, mouseX: Int, mouseY: Int): ViewerInputResult {
        val layout = huntingLayout?.takeIf { huntingKey == key } ?: return ViewerInputResult.IGNORED
        return when {
            layout.previous.contains(mouseX, mouseY) && huntingPage > 0 -> {
                huntingPage--
                ViewerInputResult.PREVIOUS_PAGE
            }
            layout.next.contains(mouseX, mouseY) && huntingPage + 1 < layout.pageCount -> {
                huntingPage++
                ViewerInputResult.NEXT_PAGE
            }
            else -> ViewerInputResult.IGNORED
        }
    }

    private fun renderHuntingNavigation(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: ItemListHuntingLayout,
        mouseX: Int,
        mouseY: Int,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            layout.previous,
            "<",
            false,
            layout.previous.contains(mouseX, mouseY),
            layout.page > 0,
        )
        PixelButtonRenderer.draw(
            context,
            font,
            layout.next,
            ">",
            false,
            layout.next.contains(mouseX, mouseY),
            layout.page + 1 < layout.pageCount,
        )
        val label = "${layout.page + 1} / ${layout.pageCount}"
        LegacyTextRenderer.draw(
            context,
            "§7$label",
            layout.pageLabel.x + (layout.pageLabel.width - font.width(label)) / 2,
            layout.pageLabel.y + HUNTING_PAGE_Y,
        )
    }

    private fun renderHuntingEntity(
        context: GuiGraphicsExtractor,
        font: Font,
        card: Rect,
        key: ItemListEntryKey,
        entityId: String,
        mouseX: Int,
        mouseY: Int,
    ) {
        val icon = huntingCardIcon(card)
        renderEntityIcon(context, font, icon, entityId, dropSources(key, entityId), mouseX, mouseY)
        val entity = SkyBlockDataRepository.entity(entityId)
        val textWidth = card.width - HUNTING_TEXT_INSET
        LegacyTextRenderer.draw(
            context,
            font.plainSubstrByWidth(entity?.name ?: entityId, textWidth),
            card.x + HUNTING_TEXT_X,
            card.y + HUNTING_NAME_Y,
        )
        val location = entity?.location ?: entity?.details?.firstOrNull() ?: "Location unknown"
        LegacyTextRenderer.draw(
            context,
            "§8${font.plainSubstrByWidth(location, textWidth)}",
            card.x + HUNTING_TEXT_X,
            card.y + HUNTING_LOCATION_Y,
        )
        if (card.contains(mouseX, mouseY) && !icon.contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                entityTooltipLines(entityId, entity, entity?.canNavigateToEntity() == true, dropSources(key, entityId)),
                mouseX,
                mouseY,
            )
        }
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
        const val HUNTING_TEXT_X = 23
        const val HUNTING_TEXT_INSET = 27
        const val HUNTING_NAME_Y = 4
        const val HUNTING_LOCATION_Y = 15
        const val HUNTING_PAGE_Y = 5
        val SLOT_BORDER = 0xFF111315.toInt()
        val SLOT_FILL = 0xD0202428.toInt()
    }
}

private fun huntingCardIcon(card: Rect): Rect = Rect(
    card.x + HUNTING_ICON_X,
    card.y + HUNTING_ICON_Y,
    HUNTING_ICON_SIZE,
    HUNTING_ICON_SIZE,
)

private const val HUNTING_ICON_X = 3
private const val HUNTING_ICON_Y = 5
private const val HUNTING_ICON_SIZE = 18

internal fun hasOtherObtainSources(key: ItemListEntryKey): Boolean = otherObtainSources(key).isNotEmpty()

internal fun hasObtainSourcesMatching(key: ItemListEntryKey, detailMarkers: List<String>): Boolean =
    otherObtainSources(key, detailMarkers).isNotEmpty()

internal fun hasBazaarObtainSource(key: ItemListEntryKey): Boolean {
    val isDeclared = SkyBlockDataRepository.info(key)?.enchantment?.sources.orEmpty()
        .any { it.kind == SkyBlockInfoSourceKind.BAZAAR }
    return isDeclared || SkyBlockPriceData.bazaarAvailability(key.id) != BazaarProductAvailability.UNAVAILABLE
}

private fun otherObtainSources(key: ItemListEntryKey, detailMarkers: List<String>? = null): List<SkyBlockInfoSource> {
    val info = SkyBlockDataRepository.info(key) ?: return emptyList()
    val recipes = SkyBlockDataRepository.recipesFor(key)
    val representedEntities = representedObtainEntityIds(recipes)
    val structured = buildList {
        addAll(info.enchantment?.sources.orEmpty().filterNot { it.kind == SkyBlockInfoSourceKind.BAZAAR })
        info.dropSources.forEach { source -> add(entitySource(source.entityId)) }
        info.droppedBy.forEach { entityId -> add(entitySource(entityId)) }
        info.soldBy.forEach { entityId -> add(entitySource(entityId)) }
    }.filterNot { it.entityId in representedEntities }
        .filter { source -> sourceMatchesSection(info.dropSources, source, detailMarkers) }
        .distinctBy { Triple(it.kind, it.displayName, it.entityId) }
    if (detailMarkers != null) {
        if (structured.isNotEmpty()) return structured
        return listOfNotNull(
            info.obtain?.withOnlyMethodsMatching(detailMarkers)?.toInfoSource(),
        )
    }
    val catalogSource = info.obtain?.withoutSpecializedMethods(recipes)?.takeUnless {
        it.source == SkyBlockObtainSource.STRUCTURED_CATALOG ||
            isCraftingSourceRepresentedByRecipes(it.summary, recipes) ||
            hasAuctionHouseObtainSource(key) && it.summary.equals(AUCTION_HOUSE_SUMMARY, ignoreCase = true)
    }
    if (key.id.startsWith("ENCHANTMENT_")) {
        return structured + listOfNotNull(catalogSource?.toInfoSource())
    }
    if (structured.isNotEmpty() || representedEntities.isNotEmpty() || hasBazaarObtainSource(key)) return structured
    return listOfNotNull(catalogSource?.toInfoSource())
}

private fun sourceMatchesSection(
    drops: List<SkyBlockDropSource>,
    source: SkyBlockInfoSource,
    detailMarkers: List<String>?,
): Boolean {
    val entityId = source.entityId ?: return detailMarkers == null
    val details = drops.filter { it.entityId == entityId }.flatMap(SkyBlockDropSource::details)
    return if (detailMarkers == null) {
        SPECIALIZED_DETAIL_MARKERS.none { marker -> details.any { it.contains(marker, ignoreCase = true) } }
    } else {
        detailMarkers.any { marker -> details.any { it.contains(marker, ignoreCase = true) } }
    }
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
    source.details.forEach { detail -> addAll(LegacyTextRenderer.wrap(font, "§7$detail", TOOLTIP_WIDTH)) }
}

private const val TOOLTIP_WIDTH = 220
private const val AUCTION_HOUSE_SUMMARY = "Traded on the Auction House"
private val OTHER_OBTAIN_METHOD = Regex(
    """\b(?:obtained\s+(?:as|from|through|via)|purchased\s+(?:at|for|from|through|via)|""" +
        """bought\s+(?:at|for|from)|sold\s+(?:at|by|for|from)|given\s+(?:after|by|for|from|when)|""" +
        """rewarded\s+(?:after|by|for|from|when|with)|dropped\s+(?:by|from)|""" +
        """found\s+(?:at|by|from|in|on)|traded\s+(?:by|for|from|through|via|with)|""" +
        """chance\s+to\s+drop|trading\s+with)\b""",
    RegexOption.IGNORE_CASE,
)
