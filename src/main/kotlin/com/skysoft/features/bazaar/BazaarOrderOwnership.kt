package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.SkyBlockOpenInventoryCell
import com.skysoft.utils.PlayerUtilities.localPlayerName
import net.minecraft.world.item.ItemStack

private val bazaarOrderOwnerPattern = Regex("""^By: (?:\[[^]]+] )*([A-Za-z0-9_]{1,16})$""")
internal const val COOP_BAZAAR_ORDERS_TITLE = "Co-op Bazaar Orders"

internal data class BazaarOrderScan(
    val orders: List<PendingOrder>,
    val localOrderSlots: Set<Int>,
)

internal fun parseBazaarOrderOwner(stack: ItemStack): String? =
    stack.cleanTextLines().firstNotNullOfOrNull { line ->
        bazaarOrderOwnerPattern.matchEntire(line)?.groupValues?.get(1)
    }

internal fun shouldBlockBazaarOrderOwner(owner: String, playerName: String): Boolean =
    !owner.equals(playerName, ignoreCase = true)

internal fun isLocalBazaarOrder(stack: ItemStack, playerName: String): Boolean? =
    parseBazaarOrderOwner(stack)?.let { owner -> !shouldBlockBazaarOrderOwner(owner, playerName) }

internal fun localBazaarPlayerName(): String? = localPlayerName()?.takeIf(String::isNotBlank)

internal fun isLocalCoopBazaarOrder(stack: ItemStack): Boolean {
    val playerName = localBazaarPlayerName() ?: return false
    return isLocalBazaarOrder(stack, playerName) == true
}

internal fun parseBazaarOrderScan(title: String, cells: List<SkyBlockOpenInventoryCell>): BazaarOrderScan? {
    if (!ordersMenuLoaded(cells.asSequence().map { cell -> cell.item })) return null
    val playerName = if (title == COOP_BAZAAR_ORDERS_TITLE) {
        localBazaarPlayerName() ?: return null
    } else {
        null
    }
    val orders = mutableListOf<PendingOrder>()
    val localOrderSlots = mutableSetOf<Int>()
    for (cell in cells) {
        val parsed = parseOrdersStack(cell.item)?.copy(guiSlot = cell.index) ?: continue
        orders += parsed
        val isLocal = playerName?.let { isLocalBazaarOrder(cell.item, it) ?: return null } ?: true
        if (isLocal) localOrderSlots += cell.index
    }
    return BazaarOrderScan(orders, localOrderSlots)
}
