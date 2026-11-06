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
    private val profitWidths = mutableMapOf<ProfitTrackerWidthKey, ProfitTrackerWidthState>()

    fun clear() {
        itemScrollOffsets.clear()
        profitRenderableTick = Long.MIN_VALUE
        profitRenderables.clear()
        inventoryProfitRenderables.clear()
        profitWidths.clear()
    }

    fun build(target: ProfitTrackerTarget, inventoryOpen: Boolean): ProfitTrackerRenderable {
        val tick = Minecraft.getInstance().level?.gameTime ?: Long.MIN_VALUE
        if (tick != profitRenderableTick) {
            profitRenderableTick = tick
            profitRenderables.clear()
            inventoryProfitRenderables.clear()
            profitWidths.keys.removeAll { !it.target.isAvailable }
        }
        val cache = if (inventoryOpen) inventoryProfitRenderables else profitRenderables
        return cache.getOrPut(target) {
            val config = target.config
            val stats = ProfitTracker.stats(target)
            val items = profitDisplayItems(target, stats)
            val scrollKey = ItemScrollKey(target, ProfitTracker.displayPeriod(target))
            val scrollOffset = itemScrollOffsets.getOrDefault(scrollKey, 0).coerceIn(0, maximumScrollOffset(target, items.size))
            if (scrollOffset == 0) itemScrollOffsets.remove(scrollKey) else itemScrollOffsets[scrollKey] = scrollOffset
            ProfitTrackerRenderable(
                target = target,
                stats = stats,
                items = items,
                maximumItems = maximumItems(target),
                scrollOffset = scrollOffset,
                inventoryOpen = inventoryOpen,
                config = config,
                hudControls = hudControls,
                widthState = profitWidths.getOrPut(ProfitTrackerWidthKey(target, inventoryOpen), ::ProfitTrackerWidthState),
            )
        }
    }

    fun wasScrollHandled(target: ProfitTrackerTarget, verticalAmount: Double): Boolean {
        if (verticalAmount == 0.0) return false
        val maximumOffset = maximumScrollOffset(target, profitDisplayItems(target, ProfitTracker.stats(target)).size)
        if (maximumOffset == 0) return false
        val key = ItemScrollKey(target, ProfitTracker.displayPeriod(target))
        val current = itemScrollOffsets.getOrDefault(key, 0)
        itemScrollOffsets[key] = OverlayListScroll.nextOffset(current, verticalAmount, maximumOffset)
        return true
    }
}

private fun maximumItems(target: ProfitTrackerTarget): Int =
    target.config.settings.maximumItems.coerceIn(MINIMUM_ITEMS, MAXIMUM_ITEMS)

private fun maximumScrollOffset(target: ProfitTrackerTarget, itemCount: Int): Int =
    (itemCount - maximumItems(target)).coerceAtLeast(0)

private data class ItemScrollKey(
    val target: ProfitTrackerTarget,
    val period: ProfitTrackingPeriod,
)

private data class ProfitTrackerWidthKey(
    val target: ProfitTrackerTarget,
    val inventoryOpen: Boolean,
)

internal class ProfitTrackerWidthState {
    private var width = 0
    private var pendingWidth = 0
    private var pendingSince = 0L

    fun update(targetWidth: Int, nowNanos: Long = System.nanoTime()): Int {
        if (targetWidth >= width) {
            width = targetWidth
            pendingWidth = targetWidth
        } else if (targetWidth != pendingWidth) {
            pendingWidth = targetWidth
            pendingSince = nowNanos
        } else if (nowNanos - pendingSince >= WIDTH_SHRINK_DELAY_NANOS) {
            width = targetWidth
        }
        return width
    }
}

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

private const val WIDTH_SHRINK_DELAY_NANOS = 2_000_000_000L
