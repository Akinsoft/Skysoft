package com.skysoft.features.bazaar

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

object BazaarTracker {
    fun register() = registerBazaarTracker()

    fun investmentPosition(productId: String): BazaarInvestmentPosition? =
        bazaarInvestmentPosition(storage.itemLots, productId)

    fun transactionsFor(productId: String, itemName: String, sinceMillis: Long) =
        bazaarTransactionsFor(storage, productId, itemName, sinceMillis)

    @JvmStatic
    fun handleMouseClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): InputHandlingResult =
        handleBazaarTrackerMouseClick(screen, click)

    @JvmStatic
    fun handleMouseButtonPress(button: Int): InputHandlingResult = handleBazaarTrackerMouseButtonPress(button)

    @JvmStatic
    fun renderSlotIndicatorBackground(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) =
        renderBazaarTrackerSlotIndicatorBackground(screen, context, slot)

    @JvmStatic
    fun renderSlotIndicatorOverlay(screen: AbstractContainerScreen<*>, context: GuiGraphicsExtractor, slot: Slot) =
        renderBazaarTrackerSlotIndicatorOverlay(screen, context, slot)

    @JvmStatic
    fun layoutOrderMenu(screen: ContainerScreen): Int = layoutBazaarOrderMenu(screen)

    @JvmStatic
    fun restoreOrderMenu(screen: AbstractContainerScreen<*>) = restoreBazaarOrderMenu(screen)

    @JvmStatic
    fun shouldBlockOrderInteraction(screen: AbstractContainerScreen<*>, slotId: Int): Boolean =
        shouldBlockBazaarOrderInteraction(screen, slotId)

}

internal val config get() = SkysoftConfigGui.config().inventory.bazaar
internal val storage get() = ProfileStorageApi.storage.bazaarTracker

internal var pendingSetup: PendingOrder? = null
internal var pendingOrderOptionId: String? = null
internal var pendingCancel: PendingCancel? = null
internal var lastOrdersInventoryKey: String? = null
internal var pendingOrdersInventoryKey: String? = null
internal var pendingOrdersInventoryStableTicks = 0
internal var sessionKnownProfit = 0.0
internal var sessionBuySetupValue = 0.0
internal var sessionSellSetupValue = 0.0
internal var displayMode = TrackerDisplayMode.SESSION
internal var hoveredControlArea: OverlayControlArea<TrackerControl>? = null
internal var statusAlertTick = 0
internal var lastOrdersGuiClickMillis = 0L
internal var lastOrdersGuiClickSignature = ""
internal val lastAlertStatuses = mutableMapOf<String, OrderStatus>()
internal val lastOutbidAlertMillis = mutableMapOf<String, Long>()
internal val missingFromOrdersGuiScans = mutableMapOf<String, MissingOrderObservation>()
internal val fillHighlightExpiresAt = mutableMapOf<String, Long>()
internal val marketProofMillis = mutableMapOf<String, Long>()
internal val recentResolvedOrders = ArrayDeque<RecentResolvedOrder>()

internal data class PendingOrder(
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long,
    val amountApproximate: Boolean,
    val amountResolution: Double,
    val pricePerUnit: Double,
    val pricePerUnitResolution: Double,
    val totalCoins: Double?,
    val totalCoinsResolution: Double,
    val filledAmount: Long?,
    val filledAmountApproximate: Boolean,
    val filledAmountResolution: Double,
    val taxPercent: Double?,
    val guiSlot: Int?,
    val stackSignature: Int?,
)

internal data class PendingCancel(
    val orderId: String?,
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long?,
    val refundedCoins: Double?,
)

internal data class RecentResolvedOrder(
    val type: BazaarOrderType,
    val itemName: String,
    val productId: String?,
    val amount: Long,
    val totalCoins: Double,
    val timestampMillis: Long,
)

internal data class NumberParse(
    val value: Double,
    val approximate: Boolean,
    val resolution: Double,
)

internal data class MissingOrderObservation(
    val scans: Int,
    val firstObservedAtMillis: Long,
)

internal data class SlotIndicator(
    val fillColor: Int,
    val outlineColor: Int,
    val partial: Boolean,
)

internal enum class OrderStatus(val label: String, val color: Int) {
    COMPETITIVE("COMPETITIVE", 0xFF2EAD4A.toInt()),
    OUTBID("OUTBID", 0xFFE04444.toInt()),
    UNDERCUT("UNDERCUT", 0xFFE06622.toInt()),
    FILLED("FILLED", 0xFF21A7D8.toInt()),
    ;

    val isWarning: Boolean get() = this == OUTBID || this == UNDERCUT
}

internal enum class TrackerDisplayMode(val displayName: String) {
    SESSION("Session"),
    TOTAL("Total"),
}

internal val displayModeCycle = listOf(TrackerDisplayMode.TOTAL, TrackerDisplayMode.SESSION)

internal enum class TrackerControl {
    TOGGLE_MODE,
    RESET,
}

internal data class LineSegment(val text: String, val action: TrackerControl? = null)

internal data class DisplayLine(val tag: String?, val tagColor: Int, val segments: List<LineSegment>) {
    val text: String get() = segments.joinToString(separator = "") { it.text }

    companion object {
        fun title(text: String) = DisplayLine(null, 0, listOf(LineSegment(text)))
        fun text(text: String) = DisplayLine(null, 0, listOf(LineSegment(text)))
        fun segments(vararg segments: LineSegment) = DisplayLine(null, 0, segments.toList())
    }
}

internal data class RelativeControlArea(
    val action: TrackerControl,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val tooltipLines: List<String>,
) {
    fun contains(mouseX: Int, mouseY: Int): Boolean = Rect(x, y, width, height).contains(mouseX, mouseY)

    fun toOverlayArea(baseX: Int, baseY: Int, scale: Float): OverlayControlArea<TrackerControl> = OverlayControlArea(
        action = action,
        x = baseX + (x * scale).roundToInt(),
        y = baseY + (y * scale).roundToInt(),
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
        tooltipLines = tooltipLines,
    )
}

internal fun cycleDisplayMode(backwards: Boolean) {
    val currentIndex = displayModeCycle.indexOf(displayMode)
    check(currentIndex in displayModeCycle.indices) { "Display mode is missing from its cycle" }
    val step = if (backwards) -1 else 1
    displayMode = displayModeCycle[Math.floorMod(currentIndex + step, displayModeCycle.size)]
}

internal fun trackerControlTooltip(action: TrackerControl): List<String> = when (action) {
    TrackerControl.TOGGLE_MODE -> displayModeTooltip()
    TrackerControl.RESET -> listOf(
        "§7Clicking this will reset your ${resetTooltipModeText()}§7.",
    )
}

private fun displayModeTooltip(): List<String> {
    val selectedIndex = displayModeCycle.indexOf(displayMode)
    check(selectedIndex in displayModeCycle.indices) { "Display mode is missing from its tooltip cycle" }
    return OverlayControlTooltips.cycle("Display Mode", displayModeCycle.map { it.displayName }, selectedIndex)
}

private fun resetTooltipModeText(): String = when (displayMode) {
    TrackerDisplayMode.SESSION -> "§eSession's §7Bazaar Tracker data"
    TrackerDisplayMode.TOTAL -> "§eTotal §7Bazaar Tracker data"
}

internal class BazaarTrackerRenderable(
    private val lines: List<DisplayLine>,
    private val background: Boolean,
) : GuiRenderable {
    private val font = Minecraft.getInstance().font
    private val lineHeight = font.lineHeight + 3
    private val padding = if (background) OverlayPanelStyle.PADDING else 0

    override val width: Int = (lines.maxOfOrNull { lineWidth(it) } ?: 0) + padding * 2
    override val height: Int = lines.size * lineHeight + padding * 2

    override fun render(context: GuiGraphicsExtractor) {
        render(context, null, null)
    }

    fun render(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): RelativeControlArea? {
        if (background) {
            OverlayPanelStyle.draw(context, 0, 0, width, height)
        }
        var hovered: RelativeControlArea? = null
        var y = padding
        for (line in lines) {
            hovered = renderLine(context, line, padding, y, mouseX, mouseY) ?: hovered
            y += lineHeight
        }
        return hovered
    }

    private fun renderLine(
        context: GuiGraphicsExtractor,
        line: DisplayLine,
        x: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): RelativeControlArea? {
        var drawX = x
        val tag = line.tag
        if (tag != null) {
            val tagText = tag.uppercase(Locale.US)
            val tagWidth = LegacyTextRenderer.width("§l$tagText") + TAG_BACKGROUND_EXTRA_WIDTH
            context.fill(x, y - 1, x + tagWidth, y + font.lineHeight + 1, line.tagColor)
            LegacyTextRenderer.draw(
                context,
                "§f§l$tagText",
                x + TAG_TEXT_X_OFFSET,
                y,
                defaultColor = WHITE_TEXT_COLOR,
            )
            drawX += tagWidth + TAG_TRAILING_GAP
        }
        var hovered: RelativeControlArea? = null
        for (segment in line.segments) {
            val width = LegacyTextRenderer.width(segment.text)
            segment.action?.let {
                val area = controlArea(it, drawX, y, width)
                if (mouseX != null && mouseY != null && area.contains(mouseX, mouseY)) hovered = area
            }
            LegacyTextRenderer.draw(context, segment.text, drawX, y, defaultColor = WHITE_TEXT_COLOR)
            drawX += width
        }
        return hovered
    }

    private fun controlArea(action: TrackerControl, x: Int, y: Int, width: Int) = RelativeControlArea(
        action = action,
        x = x - CONTROL_HIT_PADDING_X,
        y = y - CONTROL_HIT_PADDING_Y,
        width = width + CONTROL_HIT_PADDING_X * 2,
        height = font.lineHeight + CONTROL_HIT_PADDING_Y * 2,
        tooltipLines = trackerControlTooltip(action),
    )

    private fun lineWidth(line: DisplayLine): Int = tagOffset(line) + LegacyTextRenderer.width(line.text)

    private fun tagOffset(line: DisplayLine): Int {
        val tag = line.tag ?: return 0
        return LegacyTextRenderer.width("§l${tag.uppercase(Locale.US)}") + TAG_OFFSET_EXTRA_WIDTH
    }
}

internal val buySetupPattern =
    Regex("""^\[Bazaar] Buy Order Setup! ([\d,]+)x (.+) for ([\d,.]+) coins\.$""")
internal val sellSetupPattern =
    Regex("""^\[Bazaar] Sell Offer Setup! ([\d,]+)x (.+) for ([\d,.]+) coins\.$""")
internal val orderFlippedPattern =
    Regex("""^\[Bazaar] Order Flipped! ([\d,]+)x (.+) for ([\d,.]+) coins of total expected profit\.$""")
internal val buyCancelPattern =
    Regex(
        """^(?:\[Bazaar] )?Cancelled! Refunded ([\d,.]+) coins from cancelling Buy Order!$""",
        RegexOption.IGNORE_CASE,
    )
internal val sellCancelPattern =
    Regex(
        """^(?:\[Bazaar] )?Cancelled! Refunded ([\d,]+)x (.+) from cancelling sell offer!$""",
        RegexOption.IGNORE_CASE,
    )
internal val buyClaimPattern = Regex("""^\[Bazaar] Claimed ([\d,]+)x (.+) worth ([\d,.]+) coins bought for ([\d,.]+) each!$""")
internal val sellClaimPattern = Regex("""^\[Bazaar] Claimed ([\d,.]+) coins from selling ([\d,]+)x (.+) at ([\d,.]+) each!$""")
internal val filledPattern = Regex("""^\[Bazaar] Your (Buy Order|Sell Offer) for ([\d,]+)x (.+) was filled!$""")
internal val instantBuyPattern = Regex("""^\[Bazaar] Bought ([\d,]+)x (.+) for ([\d,.]+) coins!$""")
internal val instantSellPattern = Regex("""^\[Bazaar] Sold ([\d,]+)x (.+) for ([\d,.]+) coins!$""")

internal val pricePerUnitPattern = Regex("""^Price per unit: ([\d,.]+[kKmMbB]?) coins$""")
internal val confirmBuyAmountPattern = Regex("""^Order: ([\d,]+)x (.+)$""")
internal val confirmSellAmountPattern = Regex("""^Selling: ([\d,]+)x (.+)$""")
internal val totalPricePattern = Regex("""^Total price: ([\d,.]+) coins$""")
internal val youEarnPattern = Regex("""^You earn: ([\d,.]+) coins$""")
internal val taxPattern = Regex("""^Current tax: ([\d,.]+)%$""")

internal val orderHeaderPattern = Regex("""^(BUY|SELL) (.+)$""")
internal val orderAmountPattern = Regex("""^(?:Order|Offer) amount: ([\d,.]+[kKmMbB]?)x$""")
internal val worthPattern = Regex("""^Worth:? ([\d,.]+[kKmMbB]?) coins$""")
internal val filledPatternGui = Regex("""^Filled: ([\d,.]+[kKmMbB]?)/([\d,.]+[kKmMbB]?).*$""")
internal val cancelBuyTooltipPattern =
    Regex(
        """refunded ([\d,.]+) coins from ([\d,.]+[kKmMbB]?)x missing items""",
        RegexOption.IGNORE_CASE,
    )
internal val cancelSellTooltipPattern = Regex("""refunded ([\d,.]+[kKmMbB]?)x (.+?)(?: from|\.|$)""", RegexOption.IGNORE_CASE)

internal const val MAX_RECENT_RESOLVED_ORDERS = 20
internal const val RECENT_RESOLVED_SUPPRESS_MILLIS = 5_000L
internal const val STATUS_ALERT_INTERVAL_TICKS = 20
internal const val OUTBID_SOUND_COOLDOWN_MILLIS = 60_000L
internal const val GUI_MISSING_PRUNE_CLICK_GRACE_MILLIS = 1_500L
internal const val GUI_MISSING_PRUNE_NEW_ORDER_GRACE_MILLIS = 1_500L
internal const val DUPLICATE_CLICK_SUPPRESS_MILLIS = 100L
internal const val FILL_HIGHLIGHT_MILLIS = 3_000L
internal const val BAZAAR_ORDERS_GUI_VISIBLE_ORDER_LIMIT = 21
internal const val MIN_TRACKER_DISPLAY_ORDERS = 1
internal const val MAX_TRACKER_DISPLAY_ORDERS = 20
internal const val FILLED_STATUS_PRIORITY = 3
internal const val WARNING_STATUS_PRIORITY = 2
internal const val COMPETITIVE_STATUS_PRIORITY = 1
internal const val GUI_MISSING_PRUNE_CONFIRM_SCANS = 3
internal const val GUI_MISSING_PRUNE_MIN_CONFIRMATION_MILLIS = 1_500L
internal const val GUI_MISSING_PRUNE_INVENTORY_STABLE_TICKS = 3

data class BazaarInvestmentPosition(
    val amount: Long,
    val investedCoins: Double,
    val averageCost: Double,
)

internal fun bazaarInvestmentPosition(
    lots: List<com.skysoft.data.ProfileStorage.BazaarItemLotData>,
    productId: String,
): BazaarInvestmentPosition? {
    val matching = lots.asSequence()
        .filter { it.flipBatchId != null && it.productId.equals(productId, ignoreCase = true) }
        .toList()
    val amount = matching.sumOf { it.amount }
    if (amount <= 0L) return null
    val investedCoins = matching.sumOf { it.amount * it.unitCost }
    return BazaarInvestmentPosition(
        amount = amount,
        investedCoins = investedCoins,
        averageCost = investedCoins / amount,
    )
}

internal const val EXACT_AMOUNT_EPSILON = 0.5
internal const val BAZAAR_PERCENT_SCALE = 100.0
internal const val MIN_BAZAAR_TAX_MULTIPLIER = 0.01
internal const val BAZAAR_MATCH_TOLERANCE_RATE = 0.08
internal const val MIN_GUI_FILL_TOLERANCE = 1L
internal const val MIN_CANCEL_AMOUNT_TOLERANCE = 1L
internal const val MIN_CLAIM_AMOUNT_TOLERANCE = 2L
internal const val MIN_UNIT_PRICE_TOLERANCE = 2.0
internal const val TOTAL_RECALCULATION_EPSILON = 0.5
internal const val CONTROL_HIT_PADDING_X = 2
internal const val CONTROL_HIT_PADDING_Y = 3
internal const val TAG_BACKGROUND_EXTRA_WIDTH = 6
internal const val TAG_TEXT_X_OFFSET = 3
internal const val TAG_TRAILING_GAP = 4
internal const val TAG_OFFSET_EXTRA_WIDTH = TAG_BACKGROUND_EXTRA_WIDTH + TAG_TRAILING_GAP
internal const val SLOT_INDICATOR_INSET = 1
internal const val SLOT_INDICATOR_SIZE = 18
internal const val SLOT_INDICATOR_END_OFFSET = SLOT_INDICATOR_SIZE - SLOT_INDICATOR_INSET
internal const val PARTIAL_MARKER_X_OFFSET = 10
internal const val PARTIAL_MARKER_Y_OFFSET = 8
internal const val PARTIAL_MARKER_TEXT_X_OFFSET = 11
internal const val PARTIAL_MARKER_TEXT_Y_OFFSET = 8
internal const val SLOT_COMPETITIVE_FILL = 0x5530FF30
internal const val SLOT_COMPETITIVE_OUTLINE = 0xFF30FF30.toInt()
internal const val SLOT_UNDERCUT_FILL = 0x60FFD735
internal const val SLOT_UNDERCUT_OUTLINE = 0xFFFFD735.toInt()
internal const val SLOT_FILLED_FILL = 0x6045A3FF
internal const val SLOT_FILLED_OUTLINE = 0xFF45A3FF.toInt()
internal const val PARTIAL_MARKER_BACKGROUND = 0xB0000000.toInt()
internal val PARTIAL_MARKER_TEXT_COLOR = 0xFFFFFF55.toInt()
internal const val FILLED_SOUND_VOLUME = 1.6f
internal const val FILLED_SOUND_PITCH = 0.8f
internal const val PARTIAL_SOUND_VOLUME = 1.25f
internal const val PARTIAL_SOUND_PITCH = 0.35f
internal const val OUTBID_SOUND_VOLUME = 0.65f
internal const val OUTBID_SOUND_PITCH = 0.7f
internal val WHITE_TEXT_COLOR = 0xFFFFFFFF.toInt()

enum class BazaarOrderType(val label: String) {
    BUY("BUY"),
    SELL("SELL"),
}
