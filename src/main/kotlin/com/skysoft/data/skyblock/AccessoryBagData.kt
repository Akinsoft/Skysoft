package com.skysoft.data.skyblock

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object AccessoryBagData {
    private val storage get() = ProfileStorageApi.storage
    private var session: AccessoryBagSession? = null

    internal fun register(isActive: () -> Boolean) {
        SkyBlockOpenInventoryApi.onChange("Accessory Bag inventory", isActive, ::readOpenInventory)
        SkyBlockProfileApi.onProfileChange("Accessory Bag profile reset", { session != null }) {
            session = null
        }
        SkysoftClientEvents.onEndTick("Accessory Bag snapshot settlement", { isActive() || session != null }) {
            if (isActive()) processPendingSnapshot() else session = null
        }
        SkysoftClientEvents.onDisconnect("Accessory Bag disconnect reset") { session = null }
    }

    fun onSlotClick(slot: Slot?, clickedButton: Int) {
        val current = session ?: return
        if (clickedButton !in ACCESSORY_BAG_CHANGE_BUTTONS) return
        val itemName = slot?.item?.takeUnless { it.isEmpty }
            ?.formattedHoverName()
            ?.cleanSkyBlockText()
        if (itemName == "Next Page" || itemName == "Previous Page") {
            current.forceNextSnapshotBaseline = true
            current.changeLoggingArmedAt = ElapsedTimeMark.farPast()
        } else {
            current.changeLoggingArmedAt = ElapsedTimeMark.now()
        }
    }

    private fun readOpenInventory(inventory: SkyBlockOpenInventorySnapshot?) {
        if (inventory == null || !accessoryBagNamePattern.matches(inventory.title)) {
            session = null
            return
        }
        val current = session?.takeIf { it.inventoryId == inventory.containerId }
            ?: AccessoryBagSession(inventory.containerId).also { session = it }
        updateBeastmasterMultiplier(inventory.items.values)
        current.stage(accessorySnapshot(inventory.items))
    }

    private fun processPendingSnapshot() {
        val current = session ?: return
        val snapshot = current.stableSnapshot() ?: return
        val previousSnapshot = current.processedSnapshot
        val removeMissing = !current.forceNextSnapshotBaseline &&
            current.changeLoggingArmedAt.passedSince() in 0.seconds..ACCESSORY_CHANGE_LOG_MAX_AGE &&
            previousSnapshot.isLikelySameAccessoryBagView(snapshot)
        current.forceNextSnapshotBaseline = false
        current.changeLoggingArmedAt = ElapsedTimeMark.farPast()

        storeVisibleAccessories(snapshot)
        if (removeMissing) removeMissingAccessories(snapshot, previousSnapshot)
        current.processedSnapshot = snapshot
    }

    private fun storeVisibleAccessories(
        snapshot: Map<String, AccessorySnapshot>,
    ) {
        for ((internalName, accessory) in snapshot) {
            val previous = storage.accessories[internalName]
            if (previous != null && previous.displayName == accessory.displayName && previous.lastSeenSlot == accessory.slot) {
                continue
            }
            ProfileStorageApi.updateProfile { it.accessories[internalName] = accessory.toStorageData() }
        }
    }

    private fun removeMissingAccessories(
        snapshot: Map<String, AccessorySnapshot>,
        previousSnapshot: Map<String, AccessorySnapshot>?,
    ) {
        val removedAccessories = previousSnapshot.orEmpty().filterKeys { it !in snapshot }
        for (internalName in removedAccessories.keys) {
            if (internalName in storage.accessories) {
                ProfileStorageApi.updateProfile { it.accessories.remove(internalName) }
            }
        }
    }

    private fun updateBeastmasterMultiplier(inventoryItems: Collection<ItemStack>) {
        for (item in inventoryItems) {
            val beastmasterMultiplier = readBeastmasterMultiplier(item) ?: continue
            if (beastmasterMultiplier > (storage.beastmasterPetXpMultiplier ?: 1.0)) {
                ProfileStorageApi.updateProfile { it.beastmasterPetXpMultiplier = beastmasterMultiplier }
            }
        }
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

    private class AccessoryBagSession(val inventoryId: Int) {
        private var pendingSnapshot: Map<String, AccessorySnapshot>? = null
        private var stableTicks = 0
        var processedSnapshot: Map<String, AccessorySnapshot>? = null
        var forceNextSnapshotBaseline = false
        var changeLoggingArmedAt = ElapsedTimeMark.farPast()

        fun stage(snapshot: Map<String, AccessorySnapshot>) {
            if (snapshot == pendingSnapshot) return
            pendingSnapshot = snapshot
            stableTicks = 0
        }

        fun stableSnapshot(): Map<String, AccessorySnapshot>? {
            val snapshot = pendingSnapshot ?: return null
            if (snapshot == processedSnapshot) return null
            stableTicks++
            return snapshot.takeIf { stableTicks >= STABLE_SNAPSHOT_TICKS }
        }
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

    private const val STABLE_SNAPSHOT_TICKS = 2
    private val ACCESSORY_BAG_CHANGE_BUTTONS = setOf(0, 1)
    private val ACCESSORY_CHANGE_LOG_MAX_AGE = 5.seconds
    private const val BEASTMASTER_CREST_PREFIX = "BEASTMASTER_CREST_"
    private val accessoryBagNamePattern = Regex("""Accessory Bag(?: \(\d+/\d+\))?""")
    private val beastmasterPetXpPattern = Regex("""Pet Exp Boost: \+(?<amount>[\d.]+)%""")
}
