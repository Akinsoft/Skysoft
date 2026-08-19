package com.skysoft.features.inventory.sacks

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.data.skyblock.isSackContentsMenu
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.features.inventory.itemlist.ItemListViewerScreen
import com.skysoft.features.profit.profitTrackerSourcePrice
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlCycle
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import kotlin.math.floor
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object SackDisplay {
    fun register() = registerSackDisplay()
}

private val config get() = SkysoftConfigGui.config().inventory.sackDisplay
private var openSack: OpenSack? = null
private var displayMode = SackDisplayMode.ITEM_QUANTITIES
private var scrollOffset = 0
private var hoveredControl: OverlayControlArea<SackDisplayControl>? = null
private var isDisplayHovered = false

private fun registerSackDisplay() {
    SkyBlockDataRepository.Demand.register("Sack Display") { config.enabled }
    SkyBlockOpenInventoryApi.onUpdate(
        "Sack Display inventory",
        isActive = { config.enabled && HypixelLocationState.inSkyBlock },
        listener = ::updateOpenSack,
    )
    SkysoftClientEvents.onDisconnect("Sack Display reset", ::clearSackDisplay)
    registerSackDisplayInput()
    GuiOverlayRegistry.register(
        GuiOverlay(
            id = "sack_display",
            layer = GuiOverlayLayer.BELOW_SCREEN,
            contexts = GuiOverlayContextType.INVENTORIES,
            screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
            render = { context, _ -> renderSackDisplay(context) },
        ),
    )
    HudEditorRegistry.register(object : HudEditorElement {
        override val id: String = "sack_display"
        override val label: String = "Sack Display"
        override val position get() = config.position
        override val hasEditorBackground: Boolean get() = !config.details.showBackground

        override fun width(): Int = editorRenderable()?.width ?: 0
        override fun height(): Int = editorRenderable()?.height ?: 0
        override fun isVisible(): Boolean = isSackDisplayVisible()
        override fun absoluteX(width: Int): Int = position.getAbsX0AllowingOverflow(0)
        override fun absoluteY(height: Int): Int = position.getAbsY0AllowingOverflow(0)
        override fun renderEditor(context: GuiGraphicsExtractor) = editorRenderable()?.render(context) ?: Unit
        override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
            val targetX = position.getAbsX0AllowingOverflow(0) + deltaX
            val targetY = position.getAbsY0AllowingOverflow(0) + deltaY
            position.moveToAbsoluteAllowingOverflow(targetX, targetY, 0, 0)
            return InputHandlingResult.CONSUMED
        }
        override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
            position.scale += if (scrollY > 0.0) HUD_SCALE_STEP else -HUD_SCALE_STEP
            return InputHandlingResult.CONSUMED
        }
        override fun openConfig() = SkysoftConfigGui.open("Sack Display")
    })
}

private fun registerSackDisplayInput() {
    ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
        if (screen !is AbstractContainerScreen<*>) return@register
        ScreenMouseEvents.allowMouseClick(screen).register { _, click ->
            SkysoftErrorBoundary.value("Sack Display mouse click", true) {
                shouldAllowSackDisplayClick(screen, click)
            }
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, mouseY, _, verticalAmount ->
            SkysoftErrorBoundary.value("Sack Display mouse scroll", true) {
                InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
                    !wasSackDisplayScrollHandled(verticalAmount)
            }
        }
    }
}

private fun shouldAllowSackDisplayClick(
    screen: AbstractContainerScreen<*>,
    click: MouseButtonEvent,
): Boolean {
    if (!isSackDisplayVisible()) return true
    if (InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) return true
    val control = hoveredControl?.action ?: return true
    val handled = when (control) {
        SackDisplayControl.Mode -> OverlayControlCycle.wasClickHandled(click.button()) { backwards ->
            displayMode = OverlayControlCycle.next(SackDisplayMode.entries, displayMode, backwards)
        }
        SackDisplayControl.PriceSource -> OverlayControlCycle.wasClickHandled(click.button()) { backwards ->
            config.settings.priceSource = OverlayControlCycle.next(
                ProfitTrackerPriceSource.entries,
                config.settings.priceSource,
                backwards,
            )
            SkysoftConfigGui.config().saveNow()
        }
        is SackDisplayControl.Item -> wasSackItemClickHandled(screen, control.item, click.button())
    }
    if (handled) SoundUtilities.playClickSound()
    return !handled
}

private fun wasSackItemClickHandled(
    screen: AbstractContainerScreen<*>,
    item: SackDisplayItem,
    button: Int,
): Boolean = when (button) {
    GLFW.GLFW_MOUSE_BUTTON_LEFT -> {
        val connection = Minecraft.getInstance().connection ?: return false
        val key = SkyBlockDataRepository.itemKey(item.itemId)
        val itemName = SkyBlockDataRepository.entry(key)?.displayName ?: item.name.cleanSkyBlockText()
        connection.sendCommand("bz $itemName")
        MinecraftClient.setScreen(null)
        true
    }
    GLFW.GLFW_MOUSE_BUTTON_RIGHT -> if (SkysoftConfigGui.config().inventory.itemList.enabled) {
        MinecraftClient.setScreen(ItemListViewerScreen(screen, SkyBlockDataRepository.itemKey(item.itemId)))
        true
    } else {
        false
    }
    else -> false
}

private fun wasSackDisplayScrollHandled(verticalAmount: Double): Boolean {
    if (!isSackDisplayVisible() || !isDisplayHovered || verticalAmount == 0.0) return false
    val items = openSack?.items ?: return false
    val maximumOffset = maximumScrollOffset(items.size)
    if (maximumOffset == 0) return false
    scrollOffset = (scrollOffset + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)
    return true
}

private fun updateOpenSack(snapshot: SkyBlockOpenInventorySnapshot?) {
    if (snapshot == null && MinecraftClient.screen() is SkysoftHudEditor.EditorScreen) return
    if (snapshot == null || !isSackContentsMenu(snapshot.title)) {
        clearSackDisplay()
        return
    }
    val items = snapshot.items.toSortedMap().values.flatMap(::sackDisplayItems)
    val previous = openSack
    if (previous?.containerId != snapshot.containerId || previous.title != snapshot.title) scrollOffset = 0
    openSack = OpenSack(snapshot.title, snapshot.containerId, items)
    scrollOffset = scrollOffset.coerceIn(0, maximumScrollOffset(items.size))
}

private fun clearSackDisplay() {
    openSack = null
    scrollOffset = 0
    hoveredControl = null
    isDisplayHovered = false
}

private fun renderSackDisplay(context: GuiGraphicsExtractor) {
    if (!isSackDisplayVisible()) {
        hoveredControl = null
        isDisplayHovered = false
        return
    }
    val sack = openSack ?: return
    val renderable = buildRenderable(sack)
    val minecraft = Minecraft.getInstance()
    val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*> ?: return
    val window = minecraft.window
    val mouseX = minecraft.mouseHandler.getScaledXPos(window).toInt()
    val mouseY = minecraft.mouseHandler.getScaledYPos(window).toInt()
    val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
    val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
    val interactive = !InventoryOverlayInput.isPointCovered(screen, screenMouseX.toDouble(), screenMouseY.toDouble())
    val scale = config.position.effectiveScale
    val x = config.position.getAbsX0AllowingOverflow(0)
    val y = config.position.getAbsY0AllowingOverflow(0)
    val localMouseX = floor((normalMouseX - x) / scale).toInt()
    val localMouseY = floor((normalMouseY - y) / scale).toInt()

    context.nextStratum()
    context.pose().pushMatrix()
    context.pose().translate(x.toFloat(), y.toFloat())
    context.pose().scale(scale, scale)
    val localControl = renderable.renderInteractive(
        context,
        localMouseX.takeIf { interactive },
        localMouseY.takeIf { interactive },
    )
    context.pose().popMatrix()

    isDisplayHovered = interactive && localMouseX in 0 until renderable.width && localMouseY in 0 until renderable.height
    hoveredControl = localControl?.let { control ->
        OverlayControlArea(
            action = control.action,
            bounds = Rect(
                x = x + (control.bounds.x * scale).roundToInt(),
                y = y + (control.bounds.y * scale).roundToInt(),
                width = (control.bounds.width * scale).roundToInt().coerceAtLeast(1),
                height = (control.bounds.height * scale).roundToInt().coerceAtLeast(1),
            ),
            tooltipLines = control.tooltipLines,
        )
    }
    if (interactive) hoveredControl?.let { control ->
        context.nextStratum()
        val item = (control.action as? SackDisplayControl.Item)?.item
        if (item != null) {
            SkysoftNativeTooltip.setItemActionForNextFrame(
                context,
                item.stack ?: ItemStack.EMPTY,
                null,
                item.name,
                screenMouseX,
                screenMouseY,
                actionLines = buildList {
                    add("§eLeft-click §7to open Bazaar")
                    if (SkysoftConfigGui.config().inventory.itemList.enabled) {
                        add("§eRight-click §7to open Item List Info")
                    }
                },
            )
        } else {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                control.tooltipLines,
                screenMouseX,
                screenMouseY,
                scrollable = false,
            )
        }
    }
}

private fun isSackDisplayVisible(): Boolean {
    if (!config.enabled || !HypixelLocationState.inSkyBlock) return false
    val minecraft = Minecraft.getInstance()
    if (MinecraftClient.isGuiHidden(minecraft)) return false
    val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*> ?: return false
    val sack = openSack ?: return false
    return screen.menu.containerId == sack.containerId && isSackContentsMenu(screen.title.cleanSkyBlockText())
}

private fun buildRenderable(sack: OpenSack): SackDisplayRenderable {
    val maximumItems = config.settings.maximumItems.coerceIn(1, MAXIMUM_DISPLAY_ITEMS)
    val maximumOffset = (sack.items.size - maximumItems).coerceAtLeast(0)
    scrollOffset = scrollOffset.coerceIn(0, maximumOffset)
    val displayedItems = sack.items.drop(scrollOffset).take(maximumItems)
    return SackDisplayRenderable(
        title = sack.title,
        items = displayedItems,
        hiddenAbove = scrollOffset,
        hiddenBelow = (sack.items.size - scrollOffset - displayedItems.size).coerceAtLeast(0),
        mode = displayMode,
        priceSource = config.settings.priceSource,
        totalValue = if (displayMode == SackDisplayMode.TOTAL_VALUE) {
            sack.items.totalValue(config.settings.priceSource)
        } else {
            null
        },
        showIcons = config.details.showItemIcons,
        background = config.details.showBackground,
    )
}

private fun editorRenderable(): SackDisplayRenderable? = openSack?.let(::buildRenderable)

private fun maximumScrollOffset(itemCount: Int): Int =
    (itemCount - config.settings.maximumItems.coerceIn(1, MAXIMUM_DISPLAY_ITEMS)).coerceAtLeast(0)

private class SackDisplayRenderable(
    private val title: String,
    items: List<SackDisplayItem>,
    private val hiddenAbove: Int,
    private val hiddenBelow: Int,
    private val mode: SackDisplayMode,
    private val priceSource: ProfitTrackerPriceSource,
    private val totalValue: Double?,
    private val showIcons: Boolean,
    private val background: Boolean,
) : GuiRenderable {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val rows = items.map { item ->
        SackDisplayRow(
            item = item,
            name = item.name.truncateLegacyText(MAXIMUM_ITEM_NAME_LENGTH),
            value = item.displayValue(mode, priceSource),
            stack = item.stack,
            reserveIcon = showIcons,
        )
    }
    private val emptyText = "§7No stored items."
    private val indicatorText = buildList {
        if (hiddenAbove > 0) add("$hiddenAbove above")
        if (hiddenBelow > 0) add("$hiddenBelow more")
    }.joinToString(" §8• §7", prefix = "§7", postfix = if (hiddenAbove > 0 || hiddenBelow > 0) "..." else "")
    private val modeLine = "§7Mode: §a§l[${mode.displayName}]"
    private val priceSourceLine = "§7Price Source: §e§l[$priceSource]"
    private val totalText = totalValue?.let { "§6${it.coinFormat()} coins" } ?: "§8Unknown"
    private val totalWidth = LegacyTextRenderer.width("§7Total") + COLUMN_GAP + LegacyTextRenderer.width(totalText)
    private val contentWidth = maxOf(
        MINIMUM_WIDTH,
        LegacyTextRenderer.width("§e§l$title"),
        rows.maxOfOrNull(SackDisplayRow::width) ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        LegacyTextRenderer.width(modeLine),
        LegacyTextRenderer.width(priceSourceLine).takeIf { mode == SackDisplayMode.TOTAL_VALUE } ?: 0,
        totalWidth.takeIf { mode == SackDisplayMode.TOTAL_VALUE } ?: 0,
    )

    override val width: Int = contentWidth + padding * 2
    override val height: Int = padding * 2 + TITLE_HEIGHT +
        (if (rows.isEmpty()) TEXT_ROW_HEIGHT else rows.size * ITEM_ROW_HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else TEXT_ROW_HEIGHT) +
        (if (mode == SackDisplayMode.TOTAL_VALUE) TEXT_ROW_HEIGHT + CONTROL_ROW_HEIGHT else 0) +
        CONTROL_ROW_HEIGHT

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalSackControl? {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = padding
        LegacyTextRenderer.draw(context, "§e§l$title", padding, y)
        y += TITLE_HEIGHT
        var hoveredItem: LocalSackControl? = null
        if (rows.isEmpty()) {
            LegacyTextRenderer.draw(context, emptyText, padding, y)
            y += TEXT_ROW_HEIGHT
        } else {
            rows.forEach { row ->
                hoveredItem = row.renderInteractive(context, padding, width - padding, y, mouseX, mouseY)
                    ?: hoveredItem
                y += ITEM_ROW_HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, (width - LegacyTextRenderer.width(indicatorText)) / 2, y)
            y += TEXT_ROW_HEIGHT
        }
        var hoveredPriceSource: LocalSackControl? = null
        if (mode == SackDisplayMode.TOTAL_VALUE) {
            LegacyTextRenderer.draw(context, "§7Total", padding, y)
            LegacyTextRenderer.draw(context, totalText, width - padding - LegacyTextRenderer.width(totalText), y)
            y += TEXT_ROW_HEIGHT
            hoveredPriceSource = renderControl(
                context,
                y,
                priceSourceLine,
                SackDisplayControl.PriceSource,
                OverlayControlTooltips.cycle(
                    "Price Source",
                    ProfitTrackerPriceSource.entries.map(ProfitTrackerPriceSource::toString),
                    priceSource.ordinal,
                ),
                mouseX,
                mouseY,
            )
            y += CONTROL_ROW_HEIGHT
        }
        val hoveredMode = renderControl(
            context,
            y,
            modeLine,
            SackDisplayControl.Mode,
            OverlayControlTooltips.cycle(
                "Mode",
                SackDisplayMode.entries.map(SackDisplayMode::displayName),
                mode.ordinal,
            ),
            mouseX,
            mouseY,
        )
        return hoveredItem ?: hoveredPriceSource ?: hoveredMode
    }

    private fun renderControl(
        context: GuiGraphicsExtractor,
        y: Int,
        line: String,
        action: SackDisplayControl,
        tooltipLines: List<String>,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalSackControl? {
        val bounds = Rect(padding, y, LegacyTextRenderer.width(line), CONTROL_ROW_HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, CONTROL_HOVER_COLOR)
        }
        LegacyTextRenderer.draw(context, line, bounds.x, y + CONTROL_TEXT_Y_OFFSET)
        return LocalSackControl(action, bounds, tooltipLines).takeIf { hovered }
    }
}

private data class SackDisplayRow(
    val item: SackDisplayItem,
    val name: String,
    val value: String,
    val stack: ItemStack?,
    val reserveIcon: Boolean,
) {
    private val contentOffset = if (reserveIcon) ITEM_TEXT_OFFSET else 0
    val width: Int = contentOffset + LegacyTextRenderer.width(name) + COLUMN_GAP + LegacyTextRenderer.width(value)

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalSackControl? {
        val bounds = Rect(left, y, right - left, ITEM_ROW_HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) {
            context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, CONTROL_HOVER_COLOR)
        }
        if (reserveIcon) stack?.let { ItemIconRenderable(it, ICON_SCALE).renderAt(context, left, y) }
        LegacyTextRenderer.draw(context, name, left + contentOffset, y + ITEM_TEXT_Y_OFFSET)
        LegacyTextRenderer.draw(context, value, right - LegacyTextRenderer.width(value), y + ITEM_TEXT_Y_OFFSET)
        return LocalSackControl(SackDisplayControl.Item(item), bounds, emptyList()).takeIf { hovered }
    }
}

private fun SackDisplayItem.displayValue(
    mode: SackDisplayMode,
    priceSource: ProfitTrackerPriceSource,
): String = when (mode) {
    SackDisplayMode.ITEM_QUANTITIES -> if (amount == 0L) {
        "§8x0"
    } else {
        "§7x§e${amount.addSeparators()}" + (capacity?.let { " §8/ §7$it" } ?: "")
    }
    SackDisplayMode.TOTAL_VALUE -> totalValue(priceSource)?.let {
        "§6${it.coinFormat()} coins"
    } ?: "§8Unknown"
}

private fun SackDisplayItem.totalValue(priceSource: ProfitTrackerPriceSource): Double? = if (amount == 0L) {
    0.0
} else {
    profitTrackerSourcePrice(
        SkyBlockPriceData.getBazaarPrice(itemId),
        SkyBlockPriceData.getNpcSellPrices(itemId).coins,
        priceSource,
    )
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.times(amount)
        ?.takeIf(Double::isFinite)
}

private fun List<SackDisplayItem>.totalValue(priceSource: ProfitTrackerPriceSource): Double? {
    var total = 0.0
    for (item in this) {
        total += item.totalValue(priceSource) ?: return null
        if (!total.isFinite()) return null
    }
    return total
}

private data class OpenSack(
    val title: String,
    val containerId: Int,
    val items: List<SackDisplayItem>,
)

private data class LocalSackControl(
    val action: SackDisplayControl,
    val bounds: Rect,
    val tooltipLines: List<String>,
)

private enum class SackDisplayMode(val displayName: String) {
    ITEM_QUANTITIES("Quantities"),
    TOTAL_VALUE("Total Value"),
}

private sealed interface SackDisplayControl {
    data object Mode : SackDisplayControl
    data object PriceSource : SackDisplayControl
    data class Item(val item: SackDisplayItem) : SackDisplayControl
}

private const val MAXIMUM_DISPLAY_ITEMS = 30
private const val MAXIMUM_ITEM_NAME_LENGTH = 30
private const val MINIMUM_WIDTH = 190
private const val TITLE_HEIGHT = 13
private const val TEXT_ROW_HEIGHT = 11
private const val ITEM_ROW_HEIGHT = 14
private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
private const val ITEM_TEXT_Y_OFFSET = 2
private const val ICON_SCALE = 0.75
private const val ITEM_TEXT_OFFSET = 14
private const val COLUMN_GAP = 8
private const val CONTROL_HOVER_COLOR = 0x20FFFFFF
private const val HUD_SCALE_STEP = 0.1f
