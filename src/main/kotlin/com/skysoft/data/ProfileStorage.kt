package com.skysoft.data

import com.google.gson.annotations.Expose
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.features.bazaar.BazaarOrderType
import com.skysoft.features.pets.SkillExpGainApi
import com.skysoft.features.pets.SkyBlockSkill
import java.util.UUID
import kotlin.math.ceil

data class ProfileStorage(
    @Expose val players: MutableMap<String, PlayerSpecific> = mutableMapOf(),

    // Kept for migration from the first Skysoft pet-storage layout.
    @Expose val profiles: MutableMap<String, ProfileSpecific> = mutableMapOf(),
) {
    @Expose private val pets: MutableList<StoredPetData> = mutableListOf()
    @Expose private val expSharePets: MutableList<UUID?> = mutableListOf()
    @Expose private var currentPetUuid: UUID? = null
    @Expose private var beastmasterPetXpMultiplier: Double? = null
    @Expose private val skillData: MutableMap<SkyBlockSkill, SkillExpGainApi.SkillInfo> = mutableMapOf()
    @Expose private val attributeShards: MutableMap<String, AttributeShardData> = mutableMapOf()

    @Transient
    private val transientProfile = ProfileSpecific()
    @Transient
    private val transientPlayer = PlayerSpecific()

    fun repairLoadedValues() {
        repairLegacyFlatStorage()
        profiles.values.forEach { it.repairLoadedValues() }
        players.values.forEach { player ->
            player.repairLoadedValues()
            player.profiles.values.forEach { it.repairLoadedValues() }
        }
        transientProfile.repairLoadedValues()
        transientPlayer.repairLoadedValues()
    }

    fun importFrom(legacy: ProfileStorage) {
        legacy.repairLoadedValues()
        legacy.players.forEach { (playerKey, legacyPlayer) ->
            val player = players.getOrPut(playerKey) { PlayerSpecific() }
            legacyPlayer.profiles.forEach { (profileKey, legacyProfile) ->
                player.profiles.putIfAbsent(profileKey, legacyProfile)
            }
        }
        legacy.profiles.forEach { (profileKey, legacyProfile) ->
            profiles.putIfAbsent(profileKey, legacyProfile)
        }
        if (!hasLegacyFlatStorage() && legacy.hasLegacyFlatStorage()) {
            pets.addAll(legacy.pets)
            expSharePets.addAll(legacy.expSharePets)
            currentPetUuid = legacy.currentPetUuid
            beastmasterPetXpMultiplier = legacy.beastmasterPetXpMultiplier
            skillData.putAll(legacy.skillData)
            attributeShards.putAll(legacy.attributeShards)
        }
        repairLoadedValues()
    }

    fun activeProfile(): ProfileSpecific {
        val profileKey = SkyBlockProfileApi.currentProfileKey ?: return transientProfile
        val playerStorage = activePlayer()
        migrateLegacyStorage(playerStorage, profileKey)
        return playerStorage.profiles.getOrPut(profileKey) { ProfileSpecific(profileName = profileKey) }
    }

    fun activePlayer(): PlayerSpecific {
        val playerKey = SkyBlockProfileApi.currentPlayerKeyOrNull() ?: return transientPlayer
        return players.getOrPut(playerKey) { PlayerSpecific() }
    }

    private fun migrateLegacyStorage(playerStorage: PlayerSpecific, profileKey: String) {
        repairLegacyFlatStorage()
        if (profiles.isNotEmpty()) {
            profiles.forEach { (profile, data) ->
                data.repairLoadedValues()
                playerStorage.profiles.putIfAbsent(profile, data)
            }
            profiles.clear()
        }

        if (!hasLegacyFlatStorage()) return
        playerStorage.profiles.putIfAbsent(
            profileKey,
            ProfileSpecific(
                profileName = profileKey,
                pets = pets.toMutableList(),
                expSharePets = expSharePets.toMutableList(),
                currentPetUuid = currentPetUuid,
                beastmasterPetXpMultiplier = beastmasterPetXpMultiplier,
                skillData = skillData.toMutableMap(),
                attributeShards = attributeShards.toMutableMap(),
            ),
        )
        pets.clear()
        expSharePets.clear()
        currentPetUuid = null
        beastmasterPetXpMultiplier = null
        skillData.clear()
        attributeShards.clear()
    }

    private fun hasLegacyFlatStorage(): Boolean =
        pets.isNotEmpty() ||
            expSharePets.isNotEmpty() ||
            currentPetUuid != null ||
            beastmasterPetXpMultiplier != null ||
            skillData.isNotEmpty() ||
            attributeShards.isNotEmpty()

    private fun repairLegacyFlatStorage() {
        currentPetUuid = repairPetReferences(pets, expSharePets, currentPetUuid)
    }

    data class PlayerSpecific(
        @Expose val profiles: MutableMap<String, ProfileSpecific> = mutableMapOf(),
        @Expose var cookieBuffExpiresAtMillis: Long = 0L,
    ) {
        fun repairLoadedValues() {
            if (cookieBuffExpiresAtMillis < 0L) cookieBuffExpiresAtMillis = 0L
        }
    }

    data class ProfileSpecific(
        @Expose var profileName: String = "",
        @Expose val pets: MutableList<StoredPetData> = mutableListOf(),
        @Expose val expSharePets: MutableList<UUID?> = mutableListOf(),
        @Expose var currentPetUuid: UUID? = null,
        @Expose var beastmasterPetXpMultiplier: Double? = null,
        @Expose val accessories: MutableMap<String, AccessoryData> = mutableMapOf(),
        @Expose val skyBlockStoragePages: MutableMap<Int, SkyBlockStoragePageData> = mutableMapOf(),
        @Expose val skyBlockToolkits: MutableMap<String, SkyBlockStoragePageData> = mutableMapOf(),
        @Expose var skyBlockToolkitIcon: String = "",
        @Expose val inventoryEquipment: MutableList<SkyBlockStorageItemData> = mutableListOf(),
        @Expose val skillData: MutableMap<SkyBlockSkill, SkillExpGainApi.SkillInfo> = mutableMapOf(),
        @Expose val attributeShards: MutableMap<String, AttributeShardData> = mutableMapOf(),
        @Expose val slotBindings: MutableList<SlotBindingData> = mutableListOf(),
        @Expose val slotLocks: MutableList<Int> = mutableListOf(),
        @Expose val protectedItemUuids: MutableList<UUID> = mutableListOf(),
        @Expose val bazaarTracker: BazaarTrackerData = BazaarTrackerData(),
        @Expose val dianaBurrowCache: DianaBurrowCacheData = DianaBurrowCacheData(),
        @Expose val dianaBurrowChain: DianaBurrowChainData = DianaBurrowChainData(),
    ) {
        fun repairLoadedValues() {
            currentPetUuid = repairPetReferences(pets, expSharePets, currentPetUuid)
            if (attributeShards.isNotEmpty() && attributeShards.values.none { it.enabled }) {
                attributeShards.values.forEach { it.enabled = true }
            }
            skyBlockStoragePages.entries.removeIf { (page, data) ->
                page !in 0 until SKYBLOCK_STORAGE_PAGE_COUNT || !data.isUsable()
            }
            skyBlockToolkits.entries.removeIf { (toolkit, data) ->
                toolkit !in SKYBLOCK_TOOLKIT_KEYS || !data.isUsable()
            }
            repairInventoryEquipment()
            repairSlotBindings()
            repairSlotLocks()
            val repairedProtectedItemUuids = protectedItemUuids.distinct()
            protectedItemUuids.clear()
            protectedItemUuids.addAll(repairedProtectedItemUuids)
            bazaarTracker.repairLoadedValues()
            dianaBurrowCache.repairLoadedValues()
            dianaBurrowChain.repairLoadedValues()
        }

        private fun repairInventoryEquipment() {
            while (inventoryEquipment.size > INVENTORY_EQUIPMENT_SLOT_COUNT) inventoryEquipment.removeAt(inventoryEquipment.lastIndex)
            while (inventoryEquipment.size < INVENTORY_EQUIPMENT_SLOT_COUNT) inventoryEquipment.add(SkyBlockStorageItemData())
        }

        private fun repairSlotBindings() {
            SlotBindingGraph.repair(slotBindings)
        }

        private fun repairSlotLocks() {
            val repaired = slotLocks.filter { it in PLAYER_INVENTORY_SLOT_RANGE }.distinct().sorted()
            slotLocks.clear()
            slotLocks.addAll(repaired)
        }
    }

    data class DianaBurrowCacheData(
        @Expose var savedAtMillis: Long = 0L,
        @Expose val targets: MutableList<DianaBurrowTargetData> = mutableListOf(),
    ) {
        fun repairLoadedValues() {
            if (savedAtMillis < 0L) savedAtMillis = 0L
            targets.removeIf { target -> !target.isUsable() }
        }

        fun clear() {
            savedAtMillis = 0L
            targets.clear()
        }
    }

    data class DianaBurrowTargetData(
        @Expose var targetId: Long = 0L,
        @Expose var x: Double = 0.0,
        @Expose var y: Double = 0.0,
        @Expose var z: Double = 0.0,
        @Expose var type: String = "",
        @Expose var createdAtMillis: Long = 0L,
        @Expose var updatedAtMillis: Long = 0L,
    ) {
        fun isUsable(): Boolean =
            targetId > 0L &&
                type.isNotBlank() &&
                x.isFinite() &&
                y.isFinite() &&
                z.isFinite()
    }

    data class DianaBurrowChainData(
        @Expose var savedAtMillis: Long = 0L,
        @Expose var completed: Int = 0,
        @Expose var total: Int = 0,
        @Expose var nextTargetId: Long = 0L,
        @Expose var sameBlockStartTargetId: Long = 0L,
        @Expose val activeTargets: MutableList<DianaBurrowChainTargetData> = mutableListOf(),
    ) {
        fun repairLoadedValues() {
            activeTargets.removeIf { target -> !target.isUsable() }
            if (activeTargets.isEmpty() && hasLegacyTarget()) {
                activeTargets += DianaBurrowChainTargetData(
                    savedAtMillis = savedAtMillis,
                    completed = completed,
                    total = total,
                    nextTargetId = nextTargetId,
                    sameBlockStartTargetId = sameBlockStartTargetId,
                )
            }
            if (!isUsable()) clear()
        }

        fun clear() {
            savedAtMillis = 0L
            completed = 0
            total = 0
            nextTargetId = 0L
            sameBlockStartTargetId = 0L
            activeTargets.clear()
        }

        fun isUsable(): Boolean =
            activeTargets.isNotEmpty() || hasLegacyTarget()

        private fun hasLegacyTarget(): Boolean =
            savedAtMillis >= 0L &&
                total > 0 &&
                completed in 0..total &&
                nextTargetId > 0L &&
                sameBlockStartTargetId >= 0L
    }

    data class DianaBurrowChainTargetData(
        @Expose var savedAtMillis: Long = 0L,
        @Expose var completed: Int = 0,
        @Expose var total: Int = 0,
        @Expose var nextTargetId: Long = 0L,
        @Expose var sameBlockStartTargetId: Long = 0L,
    ) {
        fun isUsable(): Boolean =
            savedAtMillis >= 0L &&
                total > 0 &&
                completed in 0..total &&
                nextTargetId > 0L &&
                sameBlockStartTargetId >= 0L
    }

    data class BazaarTrackerData(
        @Expose var taxPercent: Double = 1.0,
        @Expose val activeOrders: MutableList<BazaarOrderData> = mutableListOf(),
        @Expose val itemLots: MutableList<BazaarItemLotData> = mutableListOf(),
        @Expose val transactions: MutableList<BazaarTransactionData> = mutableListOf(),
        @Expose var totalKnownProfit: Double = 0.0,
        @Expose var activeFlipBatchId: Long = 0L,
        @Expose var nextFlipBatchId: Long = 1L,
        @Expose var flipAccountingVersion: Int? = null,
    ) {
        fun repairLoadedValues() {
            activeOrders.removeIf { !it.isUsable() }
            activeOrders.forEach { it.repairLoadedValues() }
            if (flipAccountingVersion != FLIP_ACCOUNTING_VERSION) {
                itemLots.clear()
                totalKnownProfit = 0.0
                flipAccountingVersion = FLIP_ACCOUNTING_VERSION
            }
            val removedLegacyLots = itemLots.removeIf {
                it.amount <= 0 || it.unitCost <= 0.0 || it.itemName.isBlank() || (it.flipBatchId ?: 0L) <= 0L
            }
            if (removedLegacyLots) totalKnownProfit = 0.0
            transactions.removeIf { !it.isUsable() }
            if (transactions.size > MAX_TRANSACTIONS) {
                transactions.sortByDescending(BazaarTransactionData::atMillis)
                transactions.subList(MAX_TRANSACTIONS, transactions.size).clear()
            }
            activeOrders.forEach { order ->
                if ((order.flipBatchId ?: 0L) <= 0L) order.flipBatchId = null
            }
            val highestBatchId = sequence {
                yield(activeFlipBatchId)
                yieldAll(activeOrders.mapNotNull { it.flipBatchId })
                yieldAll(itemLots.mapNotNull { it.flipBatchId })
            }.max()
            nextFlipBatchId = nextFlipBatchId.coerceAtLeast(highestBatchId + 1L).coerceAtLeast(1L)
            if (activeFlipBatchId < 0L) activeFlipBatchId = 0L
        }

        companion object {
            const val FLIP_ACCOUNTING_VERSION = 1
            const val MAX_TRANSACTIONS = 500
        }
    }

    data class BazaarOrderData(
        @Expose var id: String = UUID.randomUUID().toString(),
        @Expose var type: BazaarOrderType = BazaarOrderType.BUY,
        @Expose var itemName: String = "",
        @Expose var productId: String? = null,
        @Expose var amountOrdered: Long = 0L,
        @Expose var pricePerUnit: Double = 0.0,
        @Expose var totalCoins: Double = 0.0,
        @Expose var filledAmount: Long = 0L,
        @Expose var claimedAmount: Long = 0L,
        @Expose var claimedCoins: Double = 0.0,
        @Expose var createdAtMillis: Long = System.currentTimeMillis(),
        @Expose var updatedAtMillis: Long = System.currentTimeMillis(),
        @Expose var lastGuiSlot: Int = -1,
        @Expose var amountResolution: Double = 0.0,
        @Expose var pricePerUnitResolution: Double = 0.0,
        @Expose var totalCoinsResolution: Double = 0.0,
        @Expose var setupConfirmed: Boolean = false,
        @Expose var flipBatchId: Long? = null,
    ) {
        fun isUsable(): Boolean = itemName.isNotBlank() && amountOrdered > 0 && pricePerUnit > 0.0
        fun repairLoadedValues() {
            if (!amountResolution.isFinite() || amountResolution < 0.0) amountResolution = 0.0
            if (!pricePerUnitResolution.isFinite() || pricePerUnitResolution < 0.0) pricePerUnitResolution = 0.0
            if (!totalCoinsResolution.isFinite() || totalCoinsResolution < 0.0) totalCoinsResolution = 0.0
        }
        fun maximumAmount(): Long = if (amountResolution > 0.0) {
            (ceil(amountOrdered + amountResolution).toLong() - 1L).coerceAtLeast(amountOrdered)
        } else {
            amountOrdered
        }
        fun remainingAmount(): Long = (maximumAmount() - claimedAmount).coerceAtLeast(0L)
        fun activeValue(): Double = remainingAmount() * pricePerUnit
    }

    data class BazaarItemLotData(
        @Expose var itemName: String = "",
        @Expose var productId: String? = null,
        @Expose var amount: Long = 0L,
        @Expose var unitCost: Double = 0.0,
        @Expose var flipBatchId: Long? = null,
        @Expose var source: BazaarLotSource = BazaarLotSource.BUY_ORDER,
    )

    enum class BazaarLotSource {
        BUY_ORDER,
        CRAFTED,
    }

    data class BazaarTransactionData(
        @Expose var type: BazaarTransactionType = BazaarTransactionType.INSTANT_BUY,
        @Expose var itemName: String = "",
        @Expose var productId: String? = null,
        @Expose var amount: Long = 0L,
        @Expose var totalCoins: Double = 0.0,
        @Expose var atMillis: Long = 0L,
    ) {
        fun isUsable(): Boolean = itemName.isNotBlank() && amount > 0L && totalCoins > 0.0 && atMillis > 0L
        fun pricePerUnit(): Double = totalCoins / amount
    }

    enum class BazaarTransactionType(val displayName: String) {
        INSTANT_BUY("Instant Buy"),
        INSTANT_SELL("Instant Sell"),
        BUY_ORDER("Buy Order"),
        SELL_ORDER("Sell Order"),
    }

    data class SlotBindingData(
        @Expose var firstSlot: Int = -1,
        @Expose var secondSlot: Int = -1,
    ) {
        fun isValid(): Boolean = SlotBindingGraph.isValidPair(firstSlot, secondSlot)
    }

    data class AttributeShardData(
        @Expose var amountSyphoned: Int = 0,
        @Expose var amountInBox: Int = 0,
        @Expose var enabled: Boolean = true,
    )

    data class AccessoryData(
        @Expose var displayName: String = "",
        @Expose var lastSeenSlot: Int = -1,
    )

    data class SkyBlockStoragePageData(
        @Expose var title: String = "",
        @Expose var rows: Int = 0,
        @Expose var overviewIcon: String = "",
        @Expose val items: MutableList<SkyBlockStorageItemData> = mutableListOf(),
    ) {
        fun repairLoadedValues() {
            rows = rows.coerceIn(0, SKYBLOCK_CONTAINER_MAX_ROWS)
            val targetSize = rows * SLOTS_PER_STORAGE_ROW
            while (items.size > targetSize) items.removeAt(items.lastIndex)
            while (items.size < targetSize) items.add(SkyBlockStorageItemData())
        }

        fun isUsable(): Boolean {
            repairLoadedValues()
            return title.isNotBlank()
        }
    }

    data class SkyBlockStorageItemData(
        @Expose var encodedStack: String = "",
    )

    companion object {
        const val SKYBLOCK_STORAGE_ENDER_CHEST_PAGES = 9
        const val SKYBLOCK_STORAGE_BACKPACK_PAGES = 18
        const val SKYBLOCK_STORAGE_PAGE_COUNT = SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + SKYBLOCK_STORAGE_BACKPACK_PAGES
        const val SKYBLOCK_STORAGE_PAGE_MAX_ROWS = 5
        const val SKYBLOCK_CONTAINER_MAX_ROWS = 6
        const val SLOTS_PER_STORAGE_ROW = 9
        const val SKYBLOCK_STORAGE_MAX_ROWS = SKYBLOCK_CONTAINER_MAX_ROWS
        const val INVENTORY_EQUIPMENT_SLOT_COUNT = 4
        private val PLAYER_INVENTORY_SLOT_RANGE = 0..40
        val SKYBLOCK_TOOLKIT_KEYS = setOf("farming", "hunting")
    }
}

private fun repairPetReferences(
    pets: MutableList<StoredPetData>,
    expSharePets: MutableList<UUID?>,
    currentPetUuid: UUID?,
): UUID? {
    pets.removeIf { !it.hasPetInternalName }
    val validPetUuids = pets.mapNotNullTo(mutableSetOf()) { it.uuid }
    expSharePets.replaceAll { uuid -> uuid?.takeIf { it in validPetUuids } }
    return currentPetUuid?.takeIf { it in validPetUuids }
}
