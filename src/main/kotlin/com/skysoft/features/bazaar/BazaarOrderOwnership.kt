package com.skysoft.features.bazaar

import com.skysoft.data.skyblock.SkyBlockOpenInventoryCell
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

private val bazaarOrderOwnerPattern = Regex("""^By: (?:\[[^]]+] )*([A-Za-z0-9_]{1,16})$""")
internal const val COOP_BAZAAR_ORDERS_TITLE = "Co-op Bazaar Orders"

internal data class BazaarOrderScan(
    val orders: List<PendingOrder>,
    val localOrderSlots: Set<Int>,
)

internal fun parseBazaarOrderOwner(lines: List<String>): String? =
    lines.firstNotNullOfOrNull { line -> bazaarOrderOwnerPattern.matchEntire(line)?.groupValues?.get(1) }

internal fun parseBazaarOrderOwner(stack: ItemStack): String? =
    parseBazaarOrderOwner(stack.textLines().map(String::clean))

internal fun shouldBlockBazaarOrderOwner(owner: String, playerName: String): Boolean =
    !owner.equals(playerName, ignoreCase = true)

private fun isLocalBazaarOrder(stack: ItemStack, playerName: String): Boolean? =
    parseBazaarOrderOwner(stack)?.let { owner -> !shouldBlockBazaarOrderOwner(owner, playerName) }

internal fun isLocalCoopBazaarOrder(stack: ItemStack): Boolean {
    val playerName = Minecraft.getInstance().player?.gameProfile?.name?.takeIf(String::isNotBlank) ?: return false
    return isLocalBazaarOrder(stack, playerName) == true
}

internal fun parseBazaarOrderScan(title: String, cells: List<SkyBlockOpenInventoryCell>): BazaarOrderScan? {
    if (!ordersMenuLoaded(cells.asSequence().map { cell -> cell.item })) return null
    val playerName = if (title == COOP_BAZAAR_ORDERS_TITLE) {
        Minecraft.getInstance().player?.gameProfile?.name?.takeIf(String::isNotBlank) ?: return null
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
