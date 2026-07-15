package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

object AccessoryBagData {
    private val storage get() = ProfileStorageApi.storage
    private var currentInventoryId: Int? = null
    private var pendingSnapshot: Map<String, AccessorySnapshot>? = null
    private var stableSnapshotReads = 0
    private var processedSnapshot: Map<String, AccessorySnapshot>? = null
    private var baselineEstablished = false
    private var forceNextSnapshotBaseline = false
    private var changeLoggingArmedAt = ElapsedTimeMark.farPast()

    fun onSlotClick(slot: Slot?, clickedButton: Int) {
        if (clickedButton !in ACCESSORY_BAG_CHANGE_BUTTONS) return
        val itemName = slot?.item?.takeUnless { it.isEmpty }
            ?.formattedHoverName()
            ?.cleanSkyBlockText()
        if (itemName == "Next Page" || itemName == "Previous Page") {
            forceNextSnapshotBaseline = true
            changeLoggingArmedAt = ElapsedTimeMark.farPast()
        } else {
            changeLoggingArmedAt = ElapsedTimeMark.now()
        }
    }

    fun readOpenInventory(inventoryName: String?, inventoryItems: Map<Int, ItemStack>, inventoryId: Int? = null) {
        if (!accessoryBagNamePattern.matches(inventoryName.orEmpty())) {
            resetOpenInventoryState()
            return
        }
        resetIfNewInventory(inventoryId)

        // Keep lore-derived accessory state outside the snapshot gate. Add future lore parsers here,
        // not in AccessorySnapshot, unless they should emit explicit accessory update events.
        if (updateBeastmasterMultiplier(inventoryItems.values) == ChangeResult.CHANGED) ProfileStorageApi.markDirty()

        val snapshot = accessorySnapshot(inventoryItems)
        if (!hasStableSnapshot(snapshot)) return
        if (snapshot == processedSnapshot) return
        processStableSnapshot(snapshot)
    }

    private fun resetIfNewInventory(inventoryId: Int?) {
        if (currentInventoryId == inventoryId) return
        resetOpenInventoryState()
        currentInventoryId = inventoryId
    }

    private fun hasStableSnapshot(snapshot: Map<String, AccessorySnapshot>): Boolean {
        if (snapshot == pendingSnapshot) {
            stableSnapshotReads++
        } else {
            pendingSnapshot = snapshot
            stableSnapshotReads = 1
        }
        return stableSnapshotReads >= STABLE_SNAPSHOT_READS
    }

    private fun processStableSnapshot(snapshot: Map<String, AccessorySnapshot>) {
        val previousSnapshot = processedSnapshot
        val emitChanges = shouldEmitChanges(previousSnapshot, snapshot)
        forceNextSnapshotBaseline = false
        changeLoggingArmedAt = ElapsedTimeMark.farPast()

        val accessoriesChanged = storeVisibleAccessories(snapshot) == ChangeResult.CHANGED
        val removedAccessoriesChanged = removeMissingAccessories(snapshot, previousSnapshot, emitChanges) == ChangeResult.CHANGED

        if (accessoriesChanged || removedAccessoriesChanged) ProfileStorageApi.markDirty()
        processedSnapshot = snapshot
        baselineEstablished = true
    }

    private fun shouldEmitChanges(
        previousSnapshot: Map<String, AccessorySnapshot>?,
        snapshot: Map<String, AccessorySnapshot>,
    ): Boolean =
        baselineEstablished &&
            !forceNextSnapshotBaseline &&
            changeLoggingArmedAt.passedSince() <= ACCESSORY_CHANGE_LOG_MAX_AGE &&
            previousSnapshot.isLikelySameAccessoryBagView(snapshot)

    private fun storeVisibleAccessories(
        snapshot: Map<String, AccessorySnapshot>,
    ): ChangeResult {
        var changed = false
        for ((internalName, accessory) in snapshot) {
            val previous = storage.accessories[internalName]
            if (previous != null && previous.displayName == accessory.displayName && previous.lastSeenSlot == accessory.slot) {
                continue
            }
            storage.accessories[internalName] = accessory.toStorageData()
            changed = true
        }
        return ChangeResult.from(changed)
    }

    private fun removeMissingAccessories(
        snapshot: Map<String, AccessorySnapshot>,
        previousSnapshot: Map<String, AccessorySnapshot>?,
        emitChanges: Boolean,
    ): ChangeResult {
        if (!emitChanges) return ChangeResult.UNCHANGED
        var changed = false
        val removedAccessories = previousSnapshot.orEmpty().filterKeys { it !in snapshot }
        for (internalName in removedAccessories.keys) {
            changed = storage.accessories.remove(internalName) != null || changed
        }
        return ChangeResult.from(changed)
    }

    private fun updateBeastmasterMultiplier(inventoryItems: Collection<ItemStack>): ChangeResult {
        var changed = false
        for (item in inventoryItems) {
            val beastmasterMultiplier = readBeastmasterMultiplier(item) ?: continue
            if (beastmasterMultiplier > (storage.beastmasterPetXpMultiplier ?: 1.0)) {
                storage.beastmasterPetXpMultiplier = beastmasterMultiplier
                changed = true
            }
        }
        return ChangeResult.from(changed)
    }

    fun readBeastmasterMultiplier(item: ItemStack): Double? {
        val internalName = item.accessoryInternalNameOrNull() ?: return null
        if (!internalName.startsWith(BEASTMASTER_CREST_PREFIX)) return null
        val amount = item.loreLines().firstNotNullOfOrNull { line ->
            beastmasterPetXpPattern.matchEntire(line.cleanSkyBlockText())
                ?.group("amount")
                ?.formatDoubleOrNull()
        } ?: return null
        return 1.0 + amount / BEASTMASTER_PERCENT_DENOMINATOR
    }

    private fun ItemStack.accessoryInternalNameOrNull(): String? =
        (skyBlockId() ?: extraAttributes()?.getStringOrNull("id"))
            ?.uppercase(Locale.US)
            ?.replace(':', '-')
            ?.takeUnless { it.isBlank() }

    private const val BEASTMASTER_PERCENT_DENOMINATOR = 100.0

    private fun accessorySnapshot(inventoryItems: Map<Int, ItemStack>): Map<String, AccessorySnapshot> =
        inventoryItems.mapNotNull { (slot, item) ->
            val internalName = item.accessoryInternalNameOrNull() ?: return@mapNotNull null
            internalName to AccessorySnapshot(
                displayName = item.formattedHoverName().cleanSkyBlockText(),
                slot = slot,
            )
        }.toMap()

    private fun Map<String, AccessorySnapshot>?.isLikelySameAccessoryBagView(
        currentSnapshot: Map<String, AccessorySnapshot>,
    ): Boolean {
        val previousSnapshot = this ?: return false
        val removed = previousSnapshot.keys - currentSnapshot.keys
        val added = currentSnapshot.keys - previousSnapshot.keys
        if (removed.size + added.size <= 1) return true
        return removed.size <= 1 &&
            added.size <= 1 &&
            previousSnapshot.keys.intersect(currentSnapshot.keys).isNotEmpty()
    }

    private fun resetOpenInventoryState() {
        currentInventoryId = null
        pendingSnapshot = null
        stableSnapshotReads = 0
        processedSnapshot = null
        baselineEstablished = false
        forceNextSnapshotBaseline = false
        changeLoggingArmedAt = ElapsedTimeMark.farPast()
    }

    private fun AccessorySnapshot.toStorageData(): ProfileStorage.AccessoryData =
        ProfileStorage.AccessoryData(
            displayName = displayName,
            lastSeenSlot = slot,
        )

    private data class AccessorySnapshot(
        val displayName: String,
        val slot: Int,
    )

    private const val STABLE_SNAPSHOT_READS = 2
    private val ACCESSORY_BAG_CHANGE_BUTTONS = setOf(0, 1)
    private val ACCESSORY_CHANGE_LOG_MAX_AGE = 5.seconds
    private const val BEASTMASTER_CREST_PREFIX = "BEASTMASTER_CREST_"
    private val accessoryBagNamePattern = Regex("""Accessory Bag(?: \(\d+/\d+\))?""")
    private val beastmasterPetXpPattern = Regex("""Pet Exp Boost: \+(?<amount>[\d.]+)%""")
}
