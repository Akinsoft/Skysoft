package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object SkyBlockInventoryChanges {
    private var listeners: List<Listener> = emptyList()
    private val cachedCounts = mutableMapOf<InventoryOwner, Map<String, Int>>()
    private var previousCounts: Map<String, Int>? = null
    private var previousSignature: List<Int>? = null
    private var previousContext: InventoryContext? = null
    private var settlingBaseline: Map<String, Int>? = null
    private var reportSettlingChanges = false
    private var settlingTicks = 0
    private var wasInSkyBlock = false

    fun register() {
        SkyBlockProfileApi.registerConsumer("SkyBlock Inventory Changes") { HypixelLocationState.inSkyBlock }
        ProfileStorageApi.registerConsumer("SkyBlock Inventory Cache") { HypixelLocationState.inSkyBlock }
        SkysoftClientEvents.onEndTick(
            "SkyBlock Inventory Changes",
            isActive = { HypixelLocationState.inSkyBlock || wasInSkyBlock },
            action = ::update,
        )
        SkysoftClientEvents.onDisconnect("SkyBlock Inventory Changes reset", ::reset)
    }

    fun onChange(
        boundary: String,
        isActive: () -> Boolean,
        listener: (SkyBlockInventoryChange) -> Unit,
    ) {
        listeners += Listener(boundary, isActive, listener)
    }

    private fun update(minecraft: Minecraft) {
        if (!HypixelLocationState.inSkyBlock) {
            reset()
            wasInSkyBlock = false
            return
        }
        wasInSkyBlock = true

        val player = minecraft.player ?: return reset()
        val owner = SkyBlockProfileApi.currentProfileKey?.let { profileId ->
            InventoryOwner(player.uuid.toString(), profileId)
        }
        val context = InventoryContext(
            HypixelLocationState.locationVersion,
            player.uuid.toString(),
            SkyBlockProfileApi.currentProfileKey,
            minecraft.level,
        )
        val inventory = player.inventory.getNonEquipmentItems() +
            listOfNotNull(player.containerMenu.carried.takeUnless { it.isEmpty })
        val signature = inventory.map { stack ->
            ItemStack.hashItemAndComponents(stack) * HASH_MULTIPLIER + stack.count
        }
        val contextChanged = context != previousContext
        if (!contextChanged && settlingTicks > 0) settlingTicks--
        if (!contextChanged && signature == previousSignature) {
            if (settlingTicks == 0 && settlingBaseline != null) finishSettling(owner, previousCounts.orEmpty())
            return
        }

        val currentCounts = buildMap {
            inventory.forEach { stack ->
                val itemId = stack.skyBlockId() ?: return@forEach
                put(itemId, getOrDefault(itemId, 0) + stack.count)
            }
        }
        if (contextChanged) {
            previousContext = context
            previousSignature = signature
            previousCounts = currentCounts
            val sessionBaseline = owner?.let(cachedCounts::get)
            settlingBaseline = sessionBaseline ?: owner?.let { ProfileStorageApi.storage.inventoryItemCounts.toMap() }
            reportSettlingChanges = sessionBaseline != null
            settlingTicks = INVENTORY_SETTLING_TICKS
            return
        }

        previousSignature = signature
        if (settlingTicks > 0) {
            previousCounts = currentCounts
            return
        }
        if (settlingBaseline != null) {
            finishSettling(owner, currentCounts)
            return
        }

        val changes = inventoryCountChanges(previousCounts.orEmpty(), currentCounts)
        previousCounts = currentCounts
        owner?.let { cacheCounts(it, currentCounts) }
        dispatch(changes)
    }

    private fun finishSettling(owner: InventoryOwner?, currentCounts: Map<String, Int>) {
        val changes = if (reportSettlingChanges) {
            settlingBaseline?.let { baseline -> inventoryCountChanges(baseline, currentCounts) }.orEmpty()
        } else {
            emptyMap()
        }
        settlingBaseline = null
        reportSettlingChanges = false
        previousCounts = currentCounts
        owner?.let { cacheCounts(it, currentCounts) }
        dispatch(changes)
    }

    private fun cacheCounts(owner: InventoryOwner, counts: Map<String, Int>) {
        cachedCounts[owner] = counts
        val storedCounts = ProfileStorageApi.storage.inventoryItemCounts
        if (storedCounts == counts) return
        storedCounts.clear()
        storedCounts.putAll(counts)
        ProfileStorageApi.markDirty()
    }

    private fun dispatch(changes: Map<String, Int>) {
        if (changes.isEmpty()) return
        val observation = SkyBlockInventoryChange(changes)
        listeners.forEach { registered ->
            if (registered.isActive()) {
                SkysoftErrorBoundary.run(registered.boundary) { registered.listener(observation) }
            }
        }
    }

    private fun reset() {
        previousCounts = null
        previousSignature = null
        previousContext = null
        settlingBaseline = null
        reportSettlingChanges = false
        settlingTicks = 0
    }

    private data class Listener(
        val boundary: String,
        val isActive: () -> Boolean,
        val listener: (SkyBlockInventoryChange) -> Unit,
    )

    private data class InventoryOwner(
        val playerId: String,
        val profileId: String,
    )

    private data class InventoryContext(
        val locationVersion: Long,
        val playerId: String,
        val profileId: String?,
        val level: Any?,
    )
}

data class SkyBlockInventoryChange(
    val changes: Map<String, Int>,
)

private const val HASH_MULTIPLIER = 31
private const val INVENTORY_SETTLING_TICKS = 60

internal fun inventoryCountChanges(
    previous: Map<String, Int>,
    current: Map<String, Int>,
): Map<String, Int> = buildMap {
    (previous.keys + current.keys).forEach { itemId ->
        val change = current.getOrDefault(itemId, 0) - previous.getOrDefault(itemId, 0)
        if (change != 0) put(itemId, change)
    }
}
