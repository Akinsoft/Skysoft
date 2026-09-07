package com.skysoft.features.profit

import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.utils.gui.OverlayListScroll
import net.minecraft.client.Minecraft

internal class ProfitTrackerHudContent(private val hudControls: ProfitTrackerHudControls) {
    private val itemScrollOffsets = mutableMapOf<ItemScrollKey, Int>()
    private var profitRenderableTick = Long.MIN_VALUE
    private val profitRenderables = mutableMapOf<ProfitTrackerTarget, ProfitTrackerRenderable>()
    private val inventoryProfitRenderables = mutableMapOf<ProfitTrackerTarget, ProfitTrackerRenderable>()

    fun clear() {
        itemScrollOffsets.clear()
        profitRenderableTick = Long.MIN_VALUE
        profitRenderables.clear()
        inventoryProfitRenderables.clear()
    }

    fun build(target: ProfitTrackerTarget, inventoryOpen: Boolean): ProfitTrackerRenderable {
        val tick = Minecraft.getInstance().level?.gameTime ?: Long.MIN_VALUE
        if (tick != profitRenderableTick) {
            profitRenderableTick = tick
            profitRenderables.clear()
            inventoryProfitRenderables.clear()
        }
        val cache = if (inventoryOpen) inventoryProfitRenderables else profitRenderables
        return cache.getOrPut(target) {
            val config = target.config
            val stats = ProfitTracker.stats(target)
            val items = profitDisplayItems(target, stats)
            val maximumItems = config.settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
            val scrollKey = ItemScrollKey(target, ProfitTracker.displayPeriod(target))
            val maximumOffset = (items.size - maximumItems).coerceAtLeast(0)
            val scrollOffset = itemScrollOffsets.getOrDefault(scrollKey, 0).coerceIn(0, maximumOffset)
            if (scrollOffset == 0) itemScrollOffsets.remove(scrollKey) else itemScrollOffsets[scrollKey] = scrollOffset
            ProfitTrackerRenderable(
                target = target,
                stats = stats,
                items = items,
                maximumItems = maximumItems,
                scrollOffset = scrollOffset,
                inventoryOpen = inventoryOpen,
                config = config,
                background = config.details.showBackground,
                hudControls = hudControls,
            )
        }
    }

    fun wasScrollHandled(target: ProfitTrackerTarget, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return false
        val period = ProfitTracker.displayPeriod(target)
        val maximumItems = target.config.settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
        val maximumOffset = (profitDisplayItems(target, ProfitTracker.stats(target)).size - maximumItems).coerceAtLeast(0)
        if (maximumOffset == 0) return false
        val key = ItemScrollKey(target, period)
        val current = itemScrollOffsets.getOrDefault(key, 0)
        itemScrollOffsets[key] = OverlayListScroll.nextOffset(current, verticalAmount, maximumOffset)
        return true
    }
}

private data class ItemScrollKey(
    val target: ProfitTrackerTarget,
    val period: ProfitTrackingPeriod,
)

private fun profitDisplayItems(
    target: ProfitTrackerTarget,
    stats: ProfileStorageView.ProfitTrackerStats,
): List<ProfitDisplayItem> {
    val trackedItemIds = ProfitTracker.trackedItemIds(target)
    return stats.itemCounts.mapNotNull { (itemId, amount) ->
        if (itemId !in trackedItemIds || ProfitTrackerItemCustomizations.isExcluded(target, itemId)) {
            return@mapNotNull null
        }
        val key = SkyBlockDataRepository.itemKey(itemId)
        val stack = SkyBlockDataRepository.displayStack(key) ?: return@mapNotNull null
        val name = (SkyBlockItemNames.displayName(itemId) ?: itemId)
            .replace("Enchanted ", "Ench ")
        val unitValue = ProfitTracker.unitValue(target, itemId)
        ProfitDisplayItem(itemId, name, stack, amount, unitValue?.times(amount))
    }.sortedWith(compareByDescending<ProfitDisplayItem> { it.value ?: Double.NEGATIVE_INFINITY }.thenBy { it.name })
}

private const val MAXIMUM_ITEMS = 15
