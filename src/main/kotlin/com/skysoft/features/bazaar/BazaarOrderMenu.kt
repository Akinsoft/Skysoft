package com.skysoft.features.bazaar

import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.ScreenSlotLayout
import com.skysoft.utils.gui.nonPlayerSlots
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.Slot

private val bazaarOrderMenuSlots = ScreenSlotLayout()

internal data class BazaarOrderMenuCell(val row: Int, val column: Int)

internal data class BazaarOrderMenuOrder(
    val slot: Slot,
    val owner: String,
    val isLocal: Boolean,
    val visibleIndex: Int?,
)

internal data class BazaarOrderMenuSnapshot(
    val playerName: String?,
    val visibleRows: Int,
    val rejectionReason: String?,
    val orders: List<BazaarOrderMenuOrder>,
) {
    val isActive: Boolean get() = rejectionReason == null
}

private data class BazaarOrderMenuReadResult(
    val orders: List<BazaarOrderMenuOrder>,
    val rejectionReason: String?,
)

internal fun compactBazaarOrderMenuRows(
    containerRows: Int,
    occupiedOrderRows: Set<Int>,
    ownedOrderRows: Set<Int>,
): Int = maxOf(
    containerRows.coerceAtMost(BazaarOrderMenuLayout.MIN_VISIBLE_ROWS),
    containerRows - (occupiedOrderRows - ownedOrderRows).size,
)

internal fun compactBazaarOrderMenuCell(visibleIndex: Int): BazaarOrderMenuCell = BazaarOrderMenuCell(
    row = BazaarOrderMenuLayout.FIRST_ORDER_ROW + visibleIndex / BazaarOrderMenuLayout.ORDER_COLUMNS,
    column = BazaarOrderMenuLayout.FIRST_ORDER_COLUMN + visibleIndex % BazaarOrderMenuLayout.ORDER_COLUMNS,
)

internal fun bazaarOrderMenuSlotPosition(row: Int, column: Int): Pair<Int, Int> =
    BazaarOrderMenuLayout.SLOT_X_ORIGIN + column * BazaarOrderMenuLayout.SLOT_STEP to
        BazaarOrderMenuLayout.SLOT_Y_ORIGIN + row * BazaarOrderMenuLayout.SLOT_STEP

internal fun bazaarOrderSlotRange(containerRows: Int): IntRange =
    BazaarOrderMenuLayout.MENU_COLUMNS until (containerRows - 1) * BazaarOrderMenuLayout.MENU_COLUMNS

internal fun isBazaarOrderAreaEmpty(containerSlots: List<Int>, occupiedSlots: Set<Int>): Boolean {
    val containerRows = containerSlots.size / BazaarOrderMenuLayout.MENU_COLUMNS
    if (
        containerRows !in BazaarOrderMenuLayout.MIN_ROWS..BazaarOrderMenuLayout.MAX_ROWS ||
        containerSlots.sorted() != (0 until containerRows * BazaarOrderMenuLayout.MENU_COLUMNS).toList()
    ) {
        return false
    }
    return occupiedSlots.none { slot ->
        val column = slot % BazaarOrderMenuLayout.MENU_COLUMNS
        slot in bazaarOrderSlotRange(containerRows) &&
            column in BazaarOrderMenuLayout.FIRST_ORDER_COLUMN until
            BazaarOrderMenuLayout.MENU_COLUMNS - BazaarOrderMenuLayout.FIRST_ORDER_COLUMN
    }
}

internal fun shouldCompactBazaarOrderMenu(totalOrders: Int, ownedOrders: Int): Boolean = totalOrders > ownedOrders

internal fun bazaarOrderMenuSnapshot(screen: ContainerScreen): BazaarOrderMenuSnapshot {
    val playerName = Minecraft.getInstance().player?.gameProfile?.name?.takeIf(String::isNotBlank)
    fun rejected(reason: String, orders: List<BazaarOrderMenuOrder> = emptyList()) = BazaarOrderMenuSnapshot(
        playerName = playerName,
        visibleRows = screen.menu.rowCount,
        rejectionReason = reason,
        orders = orders,
    )

    val slots = screen.nonPlayerSlots().sortedBy { slot -> slot.containerSlot }
    val containerRows = screen.menu.rowCount
    val orderSlotRange = bazaarOrderSlotRange(containerRows)
    val rejectionReason = when {
        !config.settings.onlyMyOrders -> "setting disabled"
        screen.title.cleanSkyBlockText() != COOP_BAZAAR_ORDERS_TITLE -> "not the co-op orders menu"
        containerRows !in BazaarOrderMenuLayout.MIN_ROWS..BazaarOrderMenuLayout.MAX_ROWS ->
            "unsupported container row count"
        slots.map { slot -> slot.containerSlot } != (0 until containerRows * BazaarOrderMenuLayout.MENU_COLUMNS).toList() ->
            "unsupported container slots"
        !ordersMenuLoaded(slots) -> "menu contents are not loaded"
        playerName == null -> "local player name is unavailable"
        slots.any { slot ->
            slot.containerSlot !in orderSlotRange && parseOrdersStack(slot.item) != null
        } -> "order found outside the expected order area"
        else -> null
    }
    if (rejectionReason != null) return rejected(rejectionReason)

    val readResult = readBazaarOrderMenuOrders(slots, requireNotNull(playerName), orderSlotRange)
    if (readResult.rejectionReason != null) {
        return rejected(readResult.rejectionReason, readResult.orders)
    }
    val visibleOrders = readResult.orders.count(BazaarOrderMenuOrder::isLocal)
    if (!shouldCompactBazaarOrderMenu(readResult.orders.size, visibleOrders)) {
        return rejected("menu has no co-op orders")
    }
    val occupiedOrderRows = readResult.orders.mapTo(mutableSetOf()) { order ->
        order.slot.containerSlot / BazaarOrderMenuLayout.MENU_COLUMNS
    }
    val ownedOrderRows = readResult.orders.filter(BazaarOrderMenuOrder::isLocal).mapTo(mutableSetOf()) { order ->
        order.slot.containerSlot / BazaarOrderMenuLayout.MENU_COLUMNS
    }
    return BazaarOrderMenuSnapshot(
        playerName = playerName,
        visibleRows = compactBazaarOrderMenuRows(containerRows, occupiedOrderRows, ownedOrderRows),
        rejectionReason = null,
        orders = readResult.orders,
    )
}

private fun readBazaarOrderMenuOrders(
    slots: List<Slot>,
    playerName: String,
    orderSlotRange: IntRange,
): BazaarOrderMenuReadResult {
    val orders = mutableListOf<BazaarOrderMenuOrder>()
    var visibleIndex = 0
    for (slot in slots.filter { it.containerSlot in orderSlotRange }) {
        val column = slot.containerSlot % BazaarOrderMenuLayout.MENU_COLUMNS
        val parsed = parseOrdersStack(slot.item)
        if (column == 0 || column == BazaarOrderMenuLayout.MENU_COLUMNS - 1) {
            if (parsed != null) return BazaarOrderMenuReadResult(orders, "order found in a border slot")
            continue
        }
        if (parsed == null) continue
        val owner = parseBazaarOrderOwner(slot.item)
            ?: return BazaarOrderMenuReadResult(orders, "order slot ${slot.containerSlot} has no valid By line")
        val isLocal = !shouldBlockBazaarOrderOwner(owner, playerName)
        orders += BazaarOrderMenuOrder(
            slot = slot,
            owner = owner,
            isLocal = isLocal,
            visibleIndex = if (isLocal) visibleIndex++ else null,
        )
    }
    return BazaarOrderMenuReadResult(orders, null)
}

internal fun layoutBazaarOrderMenu(screen: ContainerScreen): Int {
    val snapshot = bazaarOrderMenuSnapshot(screen)
    if (!snapshot.isActive) {
        restoreBazaarOrderMenu(screen)
        return screen.menu.rowCount
    }

    val slots = screen.nonPlayerSlots().associateBy { slot -> slot.containerSlot }
    val orderSlotRange = bazaarOrderSlotRange(screen.menu.rowCount)
    for (containerSlot in orderSlotRange) {
        bazaarOrderMenuSlots.move(
            screen,
            requireNotNull(slots[containerSlot]),
            BazaarOrderMenuLayout.OFFSCREEN,
            BazaarOrderMenuLayout.OFFSCREEN,
        )
    }

    val orderRows = snapshot.visibleRows - BazaarOrderMenuLayout.FIXED_ROWS
    repeat(orderRows) { orderRow ->
        val sourceRow = BazaarOrderMenuLayout.FIRST_ORDER_ROW + orderRow
        moveBazaarOrderMenuSlot(screen, requireNotNull(slots[sourceRow * BazaarOrderMenuLayout.MENU_COLUMNS]), sourceRow, 0)
        moveBazaarOrderMenuSlot(
            screen,
            requireNotNull(slots[sourceRow * BazaarOrderMenuLayout.MENU_COLUMNS + BazaarOrderMenuLayout.MENU_COLUMNS - 1]),
            sourceRow,
            BazaarOrderMenuLayout.MENU_COLUMNS - 1,
        )
    }
    snapshot.orders.filter(BazaarOrderMenuOrder::isLocal).forEach { order ->
        val cell = compactBazaarOrderMenuCell(requireNotNull(order.visibleIndex))
        moveBazaarOrderMenuSlot(screen, order.slot, cell.row, cell.column)
    }

    val controlRow = snapshot.visibleRows - 1
    val sourceControlRow = screen.menu.rowCount - 1
    repeat(BazaarOrderMenuLayout.MENU_COLUMNS) { column ->
        moveBazaarOrderMenuSlot(
            screen,
            requireNotNull(slots[sourceControlRow * BazaarOrderMenuLayout.MENU_COLUMNS + column]),
            controlRow,
            column,
        )
    }

    val removedRows = screen.menu.rowCount - snapshot.visibleRows
    val playerInventory = Minecraft.getInstance().player?.inventory
    screen.menu.slots
        .filter { slot -> playerInventory != null && slot.container === playerInventory }
        .forEach { slot -> bazaarOrderMenuSlots.moveFromOriginal(screen, slot, deltaY = -removedRows * BazaarOrderMenuLayout.SLOT_STEP) }
    applyBazaarOrderMenuRows(screen, snapshot.visibleRows)
    return snapshot.visibleRows
}

internal fun restoreBazaarOrderMenu(screen: AbstractContainerScreen<*>) {
    val hasSnapshot = bazaarOrderMenuSlots.hasSnapshot(screen)
    bazaarOrderMenuSlots.restore(screen)
    if (hasSnapshot && screen is ContainerScreen) {
        applyBazaarOrderMenuRows(screen, screen.menu.rowCount)
    }
}

internal fun shouldBlockBazaarOrderInteraction(screen: AbstractContainerScreen<*>, slotId: Int): Boolean {
    if (slotId < 0 || screen !is ContainerScreen) return false
    val snapshot = bazaarOrderMenuSnapshot(screen)
    if (!snapshot.isActive) return false
    return snapshot.orders.firstOrNull { order -> order.slot.index == slotId }?.isLocal == false
}

private fun moveBazaarOrderMenuSlot(screen: ContainerScreen, slot: Slot, row: Int, column: Int) {
    val (x, y) = bazaarOrderMenuSlotPosition(row, column)
    bazaarOrderMenuSlots.move(
        screen,
        slot,
        x,
        y,
    )
}

private fun applyBazaarOrderMenuRows(screen: ContainerScreen, rows: Int) {
    val height = BazaarOrderMenuLayout.BASE_HEIGHT + rows * BazaarOrderMenuLayout.SLOT_STEP
    val accessor = screen as AbstractContainerScreenAccessor
    accessor.skysoftSetImageHeight(height)
    accessor.skysoftSetTopPos((screen.height - height) / 2)
    accessor.skysoftSetInventoryLabelY(height - BazaarOrderMenuLayout.INVENTORY_LABEL_OFFSET)
}

private object BazaarOrderMenuLayout {
    const val MENU_COLUMNS = 9
    const val ORDER_COLUMNS = 7
    const val MIN_ROWS = 2
    const val MAX_ROWS = 6
    const val MIN_VISIBLE_ROWS = 4
    const val FIRST_ORDER_ROW = 1
    const val FIRST_ORDER_COLUMN = 1
    const val FIXED_ROWS = 2
    const val SLOT_X_ORIGIN = 8
    const val SLOT_Y_ORIGIN = 18
    const val SLOT_STEP = 18
    const val BASE_HEIGHT = 114
    const val INVENTORY_LABEL_OFFSET = 94
    const val OFFSCREEN = -100_000
}
