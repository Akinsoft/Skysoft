package com.skysoft.features.profit

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.config.ProfitTrackerConfig
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.ProfitTrackerQuantityPosition
import com.skysoft.config.ProfitTrackerSummaryLine
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.slayer.SlayerTimeToKill
import com.skysoft.features.slayer.formatSlayerKillTimeForHud
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.DurationParts
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.NumberUtilities.signedCoinFormat
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.item.ItemStack

private var hoveredControl: ProfitTrackerHoveredControl? = null
private var hoveredTracker: ProfitTrackerTarget? = null
private var itemPanelTarget: ProfitTrackerTarget? = null
private var isTrackerHovered = false
private val itemPanel = ProfitTrackerItemPanel()
private val hudControls = ProfitTrackerHudControls(itemPanel)
private val itemScrollOffsets = mutableMapOf<ItemScrollKey, Int>()
private var profitRenderableTick = Long.MIN_VALUE
private val profitRenderables = mutableMapOf<ProfitTrackerTarget, ProfitTrackerRenderable>()
private val inventoryProfitRenderables = mutableMapOf<ProfitTrackerTarget, ProfitTrackerRenderable>()

object ProfitTrackerHudInput {
    @JvmStatic
    fun handleKeyPress(event: KeyEvent): InputHandlingResult {
        val target = itemPanelTarget?.takeIf { it.isVisible() } ?: return InputHandlingResult.IGNORED
        return if (hudControls.wasKeyPressHandled(target, event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
    }

    @JvmStatic
    fun handleCharTyped(event: CharacterEvent): InputHandlingResult =
        if (itemPanelTarget?.takeIf { it.isVisible() } != null && hudControls.wasCharTypedHandled(event)) {
            InputHandlingResult.CONSUMED
        } else {
            InputHandlingResult.IGNORED
        }
}

internal fun registerProfitTrackerHud() {
    registerMouseCapture()
    ProfitTrackerPreset.entries.map(ProfitTrackerTarget::preset).forEach { target ->
        HudEditorRegistry.register(profitTrackerHudEditorElement(target))
    }
    HudEditorRegistry.registerProvider("custom_profit_trackers") {
        customTrackerTargets().map(::profitTrackerHudEditorElement)
    }
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "profit_tracker",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.entries.toSet(),
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderProfitTracker(context) },
        ),
    )
}

private fun profitTrackerHudEditorElement(target: ProfitTrackerTarget): HudEditorElement {
    val config = target.config
    return object : HudEditorElement {
        override val id: String = "profit_tracker_${target.storageKey.lowercase()}"
        override val label: String get() = "${target.displayName} Profit Tracker"
        override val position get() = config.position
        override val hasEditorBackground: Boolean get() = !config.details.showBackground
        override fun width(): Int = buildProfitRenderable(target, false).width
        override fun height(): Int = buildProfitRenderable(target, false).height
        override fun isVisible(): Boolean = target.isVisible()
        override fun renderEditor(context: GuiGraphicsExtractor) = buildProfitRenderable(target, false).render(context)
        override fun openConfig() = target.customId?.let(CustomProfitTrackerConfigScreen::open)
            ?: SkysoftConfigGui.open(target.displayName)
    }
}

private fun renderProfitTracker(context: GuiGraphicsExtractor) {
    val minecraft = Minecraft.getInstance()
    if (!HypixelLocationState.inSkyBlock || MinecraftClient.isGuiHidden(minecraft)) {
        clearProfitTrackerInteraction()
        return
    }
    val targets = visibleProfitTrackerTargets()
    if (targets.isEmpty()) {
        clearProfitTrackerInteraction()
        return
    }
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    if (!inventoryOpen) clearProfitTrackerInteraction()
    if (itemPanelTarget?.takeIf { it.isVisible() } == null) {
        itemPanelTarget = null
        itemPanel.clear()
    }
    hoveredControl = null
    hoveredTracker = null
    isTrackerHovered = false
    val (mouseX, mouseY) = InputUtilities.scaledMousePosition(minecraft)
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = inventoryScreen != null &&
        !InventoryOverlayInput.isPointCovered(inventoryScreen, screenMouseX.toDouble(), screenMouseY.toDouble())
    targets.forEach { target ->
        context.nextStratum()
        renderPositioned(
            context,
            buildProfitRenderable(target, inventoryOpen),
            target,
            interactive,
            normalMouseX,
            normalMouseY,
        )
    }
    if (interactive) {
        context.nextStratum()
        hoveredControl?.area?.let { area ->
            val managedItem = area.action as? ProfitTrackerControl.ManageItem
            if (managedItem != null) {
                SkysoftNativeTooltip.setItemActionForNextFrame(
                    context,
                    managedItem.stack,
                    "Manage",
                    managedItem.formattedName,
                    screenMouseX,
                    screenMouseY,
                )
            } else {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    area.tooltipLines,
                    screenMouseX,
                    screenMouseY,
                    scrollable = false,
                )
            }
        }
    }
}

private fun clearProfitTrackerInteraction() {
    hoveredControl = null
    hoveredTracker = null
    itemPanelTarget = null
    isTrackerHovered = false
    itemPanel.clear()
    hudControls.clearResetConfirmation()
}

private fun renderPositioned(
    context: GuiGraphicsExtractor,
    renderable: ProfitTrackerRenderable,
    target: ProfitTrackerTarget,
    interactive: Boolean,
    mouseX: Int,
    mouseY: Int,
) {
    val position = target.config.position
    val scale = position.effectiveScale
    val scaledWidth = (renderable.width * scale).roundToInt()
    val scaledHeight = (renderable.height * scale).roundToInt()
    val x = position.getAbsX0AllowingOverflow(scaledWidth)
    val y = position.getAbsY0AllowingOverflow(scaledHeight)
    val localMouseX = floor((mouseX - x) / scale).toInt()
    val localMouseY = floor((mouseY - y) / scale).toInt()
    val placePanelRight = x + ((renderable.width + SIDE_PANEL_ESTIMATED_WIDTH) * scale).roundToInt() <=
        Minecraft.getInstance().window.guiScaledWidth
    val localControl = context.withIsolatedPose {
        pose().translate(x.toFloat(), y.toFloat())
        pose().scale(scale, scale)
        val trackerControl = renderable.renderInteractive(
            context,
            if (interactive) localMouseX else null,
            if (interactive) localMouseY else null,
        )
        val panelControl = if (itemPanelTarget == target) {
            itemPanel.render(
                context,
                target,
                renderable.width,
                placePanelRight,
                if (interactive) localMouseX else Int.MIN_VALUE,
                if (interactive) localMouseY else Int.MIN_VALUE,
            )
        } else {
            null
        }
        panelControl?.let { control ->
            LocalControlArea(
                control.action,
                control.bounds,
                control.tooltipLines,
            )
        } ?: trackerControl
    }
    val trackerHovered = interactive && localMouseX in 0..renderable.width && localMouseY in 0..renderable.height
    if (trackerHovered || localControl != null) {
        hoveredTracker = target
        isTrackerHovered = trackerHovered
    }
    localControl?.let { area ->
        hoveredControl = ProfitTrackerHoveredControl(
            target,
            OverlayControlArea(
                action = area.action,
                bounds = Rect(
                    x = x + (area.bounds.x * scale).roundToInt(),
                    y = y + (area.bounds.y * scale).roundToInt(),
                    width = (area.bounds.width * scale).roundToInt().coerceAtLeast(1),
                    height = (area.bounds.height * scale).roundToInt().coerceAtLeast(1),
                ),
                tooltipLines = area.tooltipLines,
            ),
        )
    }
}

private fun buildProfitRenderable(target: ProfitTrackerTarget, inventoryOpen: Boolean): ProfitTrackerRenderable {
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
        )
    }
}

private fun registerMouseCapture() {
    val isActive = { SkysoftConfigGui.config().profitTrackers.isAnyEnabled() }
    InventoryOverlayInput.registerClickHandler("Profit Tracker mouse click", isActive) { screen, click ->
        val hovered = hoveredControl
        val target = hovered?.target ?: itemPanelTarget
        if (target != null && hovered?.area?.action.usesItemPanel()) selectItemPanelTarget(target)
        val allowed = InventoryOverlayInput.isPointCovered(screen, click.x(), click.y()) ||
            target == null || !target.isVisible() ||
            !hudControls.wasClickHandled(screen, target, hovered?.area?.action, click.button())
        if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
    InventoryOverlayInput.registerScrollHandler("Profit Tracker mouse scroll", isActive) {
            screen, mouseX, mouseY, verticalAmount ->
        val allowed = InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
            itemPanelTarget?.takeIf { it.isVisible() } == null && hoveredTracker == null ||
            itemPanelTarget?.let { itemPanel.wasSearchScrollHandled(it, verticalAmount) } != true &&
            (!isTrackerHovered || !wasItemScrollHandled(verticalAmount))
        if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
    }
}

private fun wasItemScrollHandled(verticalAmount: Double): Boolean {
    if (verticalAmount == 0.0) return false
    val target = hoveredTracker ?: return false
    val period = ProfitTracker.displayPeriod(target)
    val maximumItems = target.config.settings.maximumItems.coerceIn(1, MAXIMUM_ITEMS)
    val maximumOffset = (profitDisplayItems(target, ProfitTracker.stats(target)).size - maximumItems).coerceAtLeast(0)
    if (maximumOffset == 0) return false
    val key = ItemScrollKey(target, period)
    val current = itemScrollOffsets.getOrDefault(key, 0)
    itemScrollOffsets[key] = profitTrackerScrollOffset(current, verticalAmount, maximumOffset)
    return true
}

private fun profitDisplayItems(
    target: ProfitTrackerTarget,
    stats: ProfileStorage.ProfitTrackerStats,
): List<ProfitDisplayItem> {
    val trackedItemIds = ProfitTracker.trackedItemIds(target)
    return stats.itemCounts.mapNotNull { (itemId, amount) ->
        if (itemId !in trackedItemIds || ProfitTrackerItemCustomizations.isExcluded(target, itemId)) {
            return@mapNotNull null
        }
        val key = SkyBlockDataRepository.itemKey(itemId)
        val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId) ?: return@mapNotNull null
        val name = (SkyBlockDataRepository.entry(key)?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
            .replace("Enchanted ", "Ench ")
        val unitValue = ProfitTracker.unitValue(target, itemId)
        ProfitDisplayItem(itemId, name, stack, amount, unitValue?.times(amount))
    }.sortedWith(compareByDescending<ProfitDisplayItem> { it.value ?: Double.NEGATIVE_INFINITY }.thenBy { it.name })
}

private class ProfitTrackerRenderable(
    private val target: ProfitTrackerTarget,
    private val stats: ProfileStorage.ProfitTrackerStats,
    items: List<ProfitDisplayItem>,
    maximumItems: Int,
    scrollOffset: Int,
    private val inventoryOpen: Boolean,
    private val config: ProfitTrackerConfig,
    private val background: Boolean,
) : GuiRenderable {
    private val displayedItems = items.drop(scrollOffset).take(maximumItems)
    private val remainingItems = (items.size - scrollOffset - displayedItems.size).coerceAtLeast(0)
    private val hiddenItemsAbove = scrollOffset
    private val revenue = items.sumOf { it.value ?: 0.0 } + stats.coins
    private val hasUnknownPrices = items.any { it.value == null }
    private val profitLabel = when {
        stats.costs.keys.any { it != COIN_CURRENCY } -> "Coin Profit"
        hasUnknownPrices -> "Known Profit"
        else -> "Total Profit"
    }
    private val coinCosts = stats.costs[COIN_CURRENCY]?.toDouble() ?: 0.0
    private val profit = revenue - coinCosts
    private val period = ProfitTracker.displayPeriod(target)
    private val killTimeDisplay = target.slayerType?.let { SlayerTimeToKill.displayStats(it, period) }
    private val summaryLines = config.details.summaryLines.get().distinct().filter { summaryLine ->
        killTimeDisplay != null || !summaryLine.requiresKillTime
    }
    private val renderItemIcons = config.details.showItemIcons
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val resetLine = ProfitLine(
        "§c[Reset ${period.displayName}]",
        right = "§7...",
        control = ProfitTrackerControl.Reset,
        secondaryControl = ProfitTrackerControl.More,
    )
    private val resetConfirmationLine = ProfitLine(
        "§c[Cancel]",
        right = "§a[Confirm]",
        control = ProfitTrackerControl.CancelReset,
        secondaryControl = ProfitTrackerControl.ConfirmReset,
    )
    private val lines = buildLines()

    override val width: Int = maxOf(
        MINIMUM_WIDTH,
        lines.maxOfOrNull(ProfitLine::width) ?: 0,
        resetLine.width.takeIf { inventoryOpen } ?: 0,
        resetConfirmationLine.width.takeIf { inventoryOpen } ?: 0,
    ) + padding * 2
    override val height: Int = lines.sumOf(ProfitLine::height) +
        (if (inventoryOpen) resetLine.height else 0) + padding * 2

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalControlArea? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        var hovered: LocalControlArea? = null
        lines.forEach { line ->
            renderLine(context, line, y, mouseX, mouseY)?.let { hovered = it }
            y += line.height
        }
        if (inventoryOpen) renderResetLine(context, y, mouseX, mouseY)?.let { hovered = it }
        return hovered
    }

    private fun renderResetLine(
        context: GuiGraphicsExtractor,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalControlArea? {
        val confirmationOpacity = hudControls.resetConfirmationOpacity(target, period)
        val confirmationPending = hudControls.isResetConfirmationPending(target, period)
        val confirmationInteractive = hudControls.isResetConfirmationInteractive(target, period)
        val resetArea = renderLine(
            context,
            resetLine,
            y,
            mouseX.takeUnless { confirmationPending },
            mouseY.takeUnless { confirmationPending },
            1.0 - confirmationOpacity,
        )
        val confirmationArea = renderLine(
            context,
            resetConfirmationLine,
            y,
            mouseX.takeIf { confirmationInteractive },
            mouseY.takeIf { confirmationInteractive },
            confirmationOpacity,
        )
        return confirmationArea ?: resetArea
    }

    private fun renderLine(
        context: GuiGraphicsExtractor,
        line: ProfitLine,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
        opacity: Double = 1.0,
    ): LocalControlArea? {
        val primaryWidth = line.primaryControlWidth(width, padding)
        val rightWidth = line.right?.let(LegacyTextRenderer::width) ?: 0
        val secondaryX = width - padding - rightWidth
        val primaryArea = line.control?.let { action ->
            LocalControlArea(action, Rect(padding, y, primaryWidth, line.height), emptyList())
        }
        val secondaryArea = line.secondaryControl?.let { action ->
            LocalControlArea(action, Rect(secondaryX, y, rightWidth, line.height), emptyList())
        }
        primaryArea?.takeIf { it.contains(mouseX, mouseY) }?.let { area ->
            OverlayTextStyle.drawControlHover(context, area.bounds, opacity)
        }
        secondaryArea?.takeIf { it.contains(mouseX, mouseY) }?.let { area ->
            OverlayTextStyle.drawControlHover(context, area.bounds, opacity)
        }
        val textColor = TEXT_COLOR.withScaledAlpha(opacity)
        line.leading?.let {
            LegacyTextRenderer.draw(context, it, padding, y + line.textYOffset, defaultColor = textColor)
        }
        line.icon?.let {
            ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, padding + line.contentOffset, y)
        }
        val textX = if (line.centered) {
            (width - LegacyTextRenderer.width(line.left)) / 2
        } else {
            padding + line.contentOffset + if (line.icon == null) 0 else OverlayItemRowStyle.ICON_TEXT_OFFSET
        }
        LegacyTextRenderer.draw(context, line.left, textX, y + line.textYOffset, defaultColor = textColor)
        line.middle?.let { middle ->
            LegacyTextRenderer.draw(
                context,
                middle,
                textX + line.leftColumnWidth + OverlayItemRowStyle.QUANTITY_COLUMN_GAP,
                y + line.textYOffset,
                defaultColor = textColor,
            )
        }
        line.right?.let { right ->
            LegacyTextRenderer.draw(
                context,
                right,
                width - padding - LegacyTextRenderer.width(right),
                y + line.textYOffset,
                defaultColor = textColor,
            )
        }
        val hoveredArea = secondaryArea?.takeIf { it.contains(mouseX, mouseY) }
            ?: primaryArea?.takeIf { it.contains(mouseX, mouseY) }
        return hoveredArea?.copy(tooltipLines = controlTooltip(hoveredArea.action))
    }

    private fun buildLines(): List<ProfitLine> = buildList {
        val itemRows = displayedItems.map { item -> item to item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH) }
        val itemNameColumnWidth = itemRows.maxOfOrNull { (_, name) -> LegacyTextRenderer.width(name) } ?: 0
        add(ProfitLine(OverlayTextStyle.title("${target.displayName} Profit"), height = OverlayTextStyle.TITLE_HEIGHT))
        if (displayedItems.isEmpty()) {
            add(ProfitLine("§7No tracked drops yet."))
        } else {
            itemRows.forEach { (item, name) ->
                val count = itemQuantity(item)
                val countWidth = LegacyTextRenderer.width("§7x§a§l${item.amount.addSeparators()}")
                val value = item.value?.let { "§6${it.coinFormat()}" } ?: "§8Unknown"
                val quantityLeft = config.details.quantityPosition == ProfitTrackerQuantityPosition.LEFT
                add(
                    ProfitLine(
                        left = name,
                        middle = count.takeUnless { quantityLeft },
                        leftColumnWidth = if (quantityLeft) LegacyTextRenderer.width(name) else itemNameColumnWidth,
                        right = value,
                        icon = item.stack.takeIf { renderItemIcons },
                        height = OverlayItemRowStyle.HEIGHT,
                        textYOffset = OverlayItemRowStyle.TEXT_Y_OFFSET,
                        leading = count.takeIf { quantityLeft },
                        reservedColumnWidth = countWidth,
                        control = ProfitTrackerControl.ManageItem(item.itemId, item.stack, item.name)
                            .takeIf { inventoryOpen },
                    ),
                )
            }
        }
        if (remainingItems > 0) {
            add(ProfitLine("§7$remainingItems more...", centered = true))
        } else if (hiddenItemsAbove > 0) {
            add(ProfitLine("§7$hiddenItemsAbove above...", centered = true))
        }
        val profitPerHour = profitPerHour(profit, stats.activeMillis)
        summaryLines.forEach { summaryLine ->
            when (summaryLine) {
                ProfitTrackerSummaryLine.COINS -> if (stats.coins > 0.0) {
                    add(ProfitLine("§7${target.coinLabel}", "§6${stats.coins.coinFormat()}"))
                }
                ProfitTrackerSummaryLine.QUEST_COSTS -> stats.costs.forEach { (currency, amount) ->
                    val value = if (currency == COIN_CURRENCY) amount.toDouble().coinFormat() else amount.addSeparators()
                    add(ProfitLine("§7Quest Costs", "§c-$value"))
                }
                ProfitTrackerSummaryLine.TOTAL_PROFIT -> {
                    add(ProfitLine("§7$profitLabel", profitColor(profit) + profit.signedCoinFormat()))
                }
                ProfitTrackerSummaryLine.PROFIT_PER_HOUR -> {
                    val label = if (profitLabel == "Total Profit") "Profit/h" else "$profitLabel/h"
                    add(ProfitLine("§7$label", profitColor(profitPerHour) + profitPerHour.signedCoinFormat()))
                }
                ProfitTrackerSummaryLine.ACTIONS -> {
                    add(ProfitLine("§7${target.actionLabel}", "§e${stats.actions.addSeparators()}"))
                }
                ProfitTrackerSummaryLine.AVERAGE_KILL_TIME -> add(
                    ProfitLine(
                        "§7Average Kill",
                        formatSlayerKillTimeForHud(requireNotNull(killTimeDisplay).averageMillis),
                    ),
                )
                ProfitTrackerSummaryLine.PERSONAL_BEST -> add(
                    ProfitLine(
                        "§7Personal Best",
                        formatSlayerKillTimeForHud(requireNotNull(killTimeDisplay).personalBestMillis),
                    ),
                )
                ProfitTrackerSummaryLine.UPTIME -> {
                    val paused = if (ProfitTracker.isTimerPaused(target)) " §c(paused)" else ""
                    add(ProfitLine("§7Uptime", "§b${formatProfitUptime(stats.activeMillis)}$paused"))
                }
            }
        }
        if (inventoryOpen) {
            add(ProfitLine("§7Display Mode §a§l[${period.displayName}]", control = ProfitTrackerControl.Period))
            add(ProfitLine("§7Price Source §e§l[${config.settings.priceSource}]", control = ProfitTrackerControl.PriceSource))
        }
    }

    private fun itemQuantity(item: ProfitDisplayItem): String {
        val highlighted = config.details.highlightChanges &&
            ProfitTracker.itemQuantityHighlights.isHighlighted(target.storageKey to item.itemId)
        val style = if (highlighted) "§a§l" else ""
        return "§7x$style${item.amount.addSeparators()}"
    }

    private fun controlTooltip(action: ProfitTrackerControl): List<String> = when (action) {
        ProfitTrackerControl.Period -> OverlayControlTooltips.cycle(
            "Display Mode",
            ProfitTrackingPeriod.entries.map(ProfitTrackingPeriod::displayName),
            period.ordinal,
        )
        ProfitTrackerControl.PriceSource -> OverlayControlTooltips.cycle(
            "Price Source",
            ProfitTrackerPriceSource.entries.map(ProfitTrackerPriceSource::toString),
            config.settings.priceSource.ordinal,
        )
        ProfitTrackerControl.Reset,
        ProfitTrackerControl.ConfirmReset,
        -> listOf("§7Reset ${period.displayName} ${target.displayName} data.")
        ProfitTrackerControl.CancelReset -> emptyList()
        ProfitTrackerControl.More -> listOf("§7Manage tracked items.")
        is ProfitTrackerControl.ManageItem -> emptyList()
        else -> emptyList()
    }
}

private data class ProfitLine(
    val left: String,
    val right: String? = null,
    val icon: ItemStack? = null,
    val height: Int = OverlayTextStyle.ROW_HEIGHT,
    val textYOffset: Int = 0,
    val control: ProfitTrackerControl? = null,
    val secondaryControl: ProfitTrackerControl? = null,
    val centered: Boolean = false,
    val leading: String? = null,
    val middle: String? = null,
    val reservedColumnWidth: Int? = null,
    val leftColumnWidth: Int = LegacyTextRenderer.width(left),
) {
    val leadingWidth: Int = leading?.let { reservedColumnWidth ?: LegacyTextRenderer.width(it) } ?: 0
    private val middleWidth: Int = middle?.let { reservedColumnWidth ?: LegacyTextRenderer.width(it) } ?: 0
    val contentOffset: Int = leadingWidth + if (leading == null) 0 else OverlayItemRowStyle.QUANTITY_COLUMN_GAP
    val width: Int = contentOffset + (if (icon == null) 0 else OverlayItemRowStyle.ICON_TEXT_OFFSET) +
        leftColumnWidth + (middle?.let { middleWidth + OverlayItemRowStyle.QUANTITY_COLUMN_GAP } ?: 0) +
        (right?.let { LegacyTextRenderer.width(it) + OverlayItemRowStyle.VALUE_COLUMN_GAP } ?: 0)

    fun primaryControlWidth(totalWidth: Int, padding: Int): Int = when {
        control is ProfitTrackerControl.ManageItem -> totalWidth - padding * 2
        secondaryControl == null -> width
        else -> LegacyTextRenderer.width(left)
    }
}

internal fun formatProfitUptime(activeMillis: Long): String {
    val duration = DurationParts.fromMilliseconds(activeMillis)
    return buildList {
        if (duration.totalHours > 0L) add("${duration.totalHours}h")
        if (duration.minutes > 0L || duration.totalHours > 0L) add("${duration.minutes}m")
        add("${duration.seconds}s")
    }.joinToString(" ")
}

internal fun profitPerHour(profit: Double, activeMillis: Long): Double =
    if (activeMillis > 0L) profit * MILLIS_PER_HOUR / activeMillis else 0.0

private data class ItemScrollKey(
    val target: ProfitTrackerTarget,
    val period: ProfitTrackingPeriod,
)

private data class ProfitDisplayItem(
    val itemId: String,
    val name: String,
    val stack: ItemStack,
    val amount: Long,
    val value: Double?,
)

private data class LocalControlArea(
    val action: ProfitTrackerControl,
    val bounds: Rect,
    val tooltipLines: List<String>,
)

private data class ProfitTrackerHoveredControl(
    val target: ProfitTrackerTarget,
    val area: OverlayControlArea<ProfitTrackerControl>,
)

private fun ProfitTrackerControl?.usesItemPanel(): Boolean =
    this == ProfitTrackerControl.More || this is ProfitTrackerControl.ManageItem

private fun selectItemPanelTarget(target: ProfitTrackerTarget) {
    if (itemPanelTarget == target) return
    itemPanel.clear()
    hudControls.clearResetConfirmation()
    itemPanelTarget = target
}

private fun LocalControlArea.contains(mouseX: Int?, mouseY: Int?): Boolean =
    mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)

private fun profitColor(value: Double): String = if (value >= 0.0) "§a" else "§c"

private const val COIN_CURRENCY = "Coins"
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val MAXIMUM_ITEMS = 15
private const val MAXIMUM_ITEM_NAME_LENGTH = 20
private const val MINIMUM_WIDTH = 145
private const val SIDE_PANEL_ESTIMATED_WIDTH = 310
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
