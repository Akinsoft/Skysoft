package com.skysoft.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.data.skyblock.SkyBlockSkillInfo
import java.util.UUID
import kotlin.math.ceil

data class ProfileStorage(
    @Expose override val players: MutableMap<String, PlayerSpecific> = mutableMapOf(),

    // Kept for migration from the first Skysoft pet-storage layout.
    @Expose override val profiles: MutableMap<String, ProfileSpecific> = mutableMapOf(),
) : ProfileStorageView {
    @Expose private val pets: MutableList<StoredPetData> = mutableListOf()
    @Expose private val expSharePets: MutableList<UUID?> = mutableListOf()
    @Expose private var currentPetUuid: UUID? = null
    @Expose private var beastmasterPetXpMultiplier: Double? = null
    @Expose private val skillData: MutableMap<SkyBlockSkill, SkyBlockSkillInfo> = mutableMapOf()
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
    }

    internal fun activeProfile(onChange: () -> Unit): ProfileSpecific {
        val profileId = SkyBlockProfileApi.currentProfileId ?: return transientProfile
        val profileKey = profileId.profileKey
        val playerStorage = playerStorage(profileId.playerKey, onChange)
        if (profiles.isNotEmpty() || hasLegacyFlatStorage()) {
            migrateLegacyStorage(playerStorage, profileKey)
            onChange()
        }
        return playerStorage.profiles.getOrPut(profileKey) {
            onChange()
            ProfileSpecific(profileName = profileKey)
        }
    }

    internal fun activePlayer(onChange: () -> Unit): PlayerSpecific {
        val playerKey = SkyBlockProfileApi.currentPlayerKeyOrNull() ?: return transientPlayer
        return playerStorage(playerKey, onChange)
    }

    private fun playerStorage(playerKey: String, onChange: () -> Unit): PlayerSpecific =
        players.getOrPut(playerKey) {
            onChange()
            PlayerSpecific()
        }

    private fun migrateLegacyStorage(playerStorage: PlayerSpecific, profileKey: String) {
        if (profiles.isNotEmpty()) {
            profiles.forEach { (profile, data) ->
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
        @Expose override val profiles: MutableMap<String, ProfileSpecific> = mutableMapOf(),
        @Expose override var cookieBuffExpiresAtMillis: Long = 0L,
    ) : ProfileStorageView.PlayerSpecific {
        fun repairLoadedValues() {
            if (cookieBuffExpiresAtMillis < 0L) cookieBuffExpiresAtMillis = 0L
        }
    }

    data class ProfileSpecific(
        @Expose override var profileName: String = "",
        @Expose override val pets: MutableList<StoredPetData> = mutableListOf(),
        @Expose override val expSharePets: MutableList<UUID?> = mutableListOf(),
        @Expose override var currentPetUuid: UUID? = null,
        @Expose override var beastmasterPetXpMultiplier: Double? = null,
        @Expose override val accessories: MutableMap<String, AccessoryData> = mutableMapOf(),
        @Expose override val skyBlockStoragePages: MutableMap<Int, SkyBlockStoragePageData> = mutableMapOf(),
        @Expose override val skyBlockRiftStoragePages: MutableMap<Int, SkyBlockStoragePageData> = mutableMapOf(),
        @Expose override val skyBlockToolkits: MutableMap<String, SkyBlockStoragePageData> = mutableMapOf(),
        @Expose override var skyBlockToolkitIcon: String = "",
        @Expose override val inventoryEquipment: MutableList<SkyBlockStorageItemData> =
            MutableList(INVENTORY_EQUIPMENT_SLOT_COUNT) { SkyBlockStorageItemData() },
        @Expose override val skillData: MutableMap<SkyBlockSkill, SkyBlockSkillInfo> = mutableMapOf(),
        @Expose override val attributeShards: MutableMap<String, AttributeShardData> = mutableMapOf(),
        @Expose override val slotBindings: MutableList<SlotBindingData> = mutableListOf(),
        @Expose override val slotLocks: MutableList<Int> = mutableListOf(),
        @Expose override val protectedItemUuids: MutableList<UUID> = mutableListOf(),
        @Expose override val inventoryItemCounts: MutableMap<String, Int> = mutableMapOf(),
        @Expose override val sackContents: MutableMap<String, SackItemData> = mutableMapOf(),
        @Expose override val profitTracker: ProfitTrackerData = ProfitTrackerData(),
        @Expose override val slayerTimeToKill: SlayerTimeToKillData = SlayerTimeToKillData(),
        @Expose override val bazaarTracker: BazaarTrackerData = BazaarTrackerData(
            flipAccountingVersion = BazaarTrackerData.FLIP_ACCOUNTING_VERSION,
        ),
        @Expose override val honeyhiveTracker: HoneyhiveTrackerData = HoneyhiveTrackerData(),
        @Expose override val dianaBurrowCache: DianaBurrowCacheData = DianaBurrowCacheData(),
        @Expose override val dianaBurrowChain: DianaBurrowChainData = DianaBurrowChainData(),
    ) : ProfileStorageView.ProfileSpecific {
        fun repairLoadedValues() {
            currentPetUuid = repairPetReferences(pets, expSharePets, currentPetUuid)
            skyBlockStoragePages.repairStoragePages { it in 0 until SKYBLOCK_STORAGE_PAGE_COUNT }
            skyBlockRiftStoragePages.repairStoragePages { it in 0 until SKYBLOCK_RIFT_STORAGE_PAGE_COUNT }
            skyBlockToolkits.repairStoragePages { it in SKYBLOCK_TOOLKIT_KEYS }
            repairInventoryEquipment()
            repairSlotBindings()
            repairSlotLocks()
            val repairedProtectedItemUuids = protectedItemUuids.distinct()
            protectedItemUuids.clear()
            protectedItemUuids.addAll(repairedProtectedItemUuids)
            inventoryItemCounts.entries.removeIf { (itemId, amount) -> itemId.isBlank() || amount <= 0 }
            sackContents.keys.removeIf(String::isBlank)
            sackContents.values.forEach(SackItemData::repairLoadedValues)
            profitTracker.repairLoadedValues()
            slayerTimeToKill.repairLoadedValues()
            bazaarTracker.repairLoadedValues()
            honeyhiveTracker.repairLoadedValues()
            dianaBurrowCache.repairLoadedValues()
            dianaBurrowChain.repairLoadedValues()
        }

        private fun <K> MutableMap<K, SkyBlockStoragePageData>.repairStoragePages(isValidKey: (K) -> Boolean) {
            entries.removeIf { (key, page) ->
                if (!isValidKey(key)) {
                    true
                } else {
                    page.repairLoadedValues()
                    page.title.isBlank()
                }
            }
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

    data class ProfitTrackerData(
        @Expose override val totals: MutableMap<String, ProfitTrackerStats> = mutableMapOf(),
        @Expose override var todayEpochDay: Long = 0L,
        @Expose override val today: MutableMap<String, ProfitTrackerStats> = mutableMapOf(),
        @Expose override var mythologicalRitualMayorKey: String = "",
        @Expose override val mythologicalRitualMayor: ProfitTrackerStats = ProfitTrackerStats(),
        @Expose override val displayPeriods: MutableMap<String, String> = mutableMapOf(),
        @Expose override val itemCustomizations: MutableMap<String, ProfitTrackerItemCustomizations> = mutableMapOf(),
        @Expose override var farmingKernelItem: String = "",
        @Expose override var farmingKernelPriceSource: String = "",
        @Expose
        @SerializedName(value = "lastPreset", alternate = ["lastSlayerType"])
        override var lastPreset: String = "",
    ) : ProfileStorageView.ProfitTrackerData {
        fun repairLoadedValues() {
            totals.keys.removeIf(String::isBlank)
            today.keys.removeIf(String::isBlank)
            displayPeriods.entries.removeIf { (preset, period) ->
                preset.isBlank() || period !in PROFIT_TRACKER_PERIODS
            }
            itemCustomizations.keys.removeIf(String::isBlank)
            itemCustomizations.values.forEach(ProfitTrackerItemCustomizations::repairLoadedValues)
            totals.values.forEach(ProfitTrackerStats::repairLoadedValues)
            today.values.forEach(ProfitTrackerStats::repairLoadedValues)
            mythologicalRitualMayor.repairLoadedValues()
            if (mythologicalRitualMayorKey.isBlank()) mythologicalRitualMayor.clear()
            if (todayEpochDay < 0L) todayEpochDay = 0L
        }
    }

    data class ProfitTrackerItemCustomizations(
        @Expose override val excludedItems: MutableList<String> = mutableListOf(),
        @Expose override val customItems: MutableList<String> = mutableListOf(),
        @Expose override val priceSources: MutableMap<String, String> = mutableMapOf(),
    ) : ProfileStorageView.ProfitTrackerItemCustomizations {
        fun repairLoadedValues() {
            excludedItems.repairItemIds()
            customItems.repairItemIds()
            priceSources.entries.removeIf { (itemId, source) ->
                itemId.isBlank() || source !in PROFIT_TRACKER_PRICE_SOURCES
            }
        }

        private fun MutableList<String>.repairItemIds() {
            val repaired = filter(String::isNotBlank).distinct()
            clear()
            addAll(repaired)
        }
    }

    data class ProfitTrackerStats(
        @Expose override val itemCounts: MutableMap<String, Long> = mutableMapOf(),
        @Expose override val costs: MutableMap<String, Long> = mutableMapOf(),
        @Expose
        @SerializedName(value = "coins", alternate = ["mobKillCoins"])
        override var coins: Double = 0.0,
        @Expose override var kernels: Long = 0L,
        @Expose override var activeMillis: Long = 0L,
        @Expose
        @SerializedName(value = "actions", alternate = ["bosses"])
        override var actions: Long = 0L,
        @Expose override val pestKills: MutableMap<String, Long> = mutableMapOf(),
    ) : ProfileStorageView.ProfitTrackerStats {
        fun repairLoadedValues() {
            itemCounts.entries.removeIf { (itemId, amount) -> itemId.isBlank() || amount <= 0L }
            costs.entries.removeIf { (currency, amount) -> currency.isBlank() || amount <= 0L }
            pestKills.entries.removeIf { (pest, amount) -> pest.isBlank() || amount <= 0L }
            if (!coins.isFinite() || coins < 0.0) coins = 0.0
            if (kernels < 0L) kernels = 0L
            if (activeMillis < 0L) activeMillis = 0L
            if (actions < 0L) actions = 0L
        }

        fun clear() {
            itemCounts.clear()
            costs.clear()
            pestKills.clear()
            coins = 0.0
            kernels = 0L
            activeMillis = 0L
            actions = 0L
        }
    }

    data class SlayerTimeToKillData(
        @Expose override val totals: MutableMap<SkyBlockSlayerType, MutableMap<Int, SlayerKillTimeStats>> = mutableMapOf(),
        @Expose override var todayEpochDay: Long = 0L,
        @Expose override val today: MutableMap<SkyBlockSlayerType, MutableMap<Int, SlayerKillTimeStats>> = mutableMapOf(),
        @Expose override val lastTiers: MutableMap<SkyBlockSlayerType, Int> = mutableMapOf(),
    ) : ProfileStorageView.SlayerTimeToKillData {
        fun repairLoadedValues() {
            repairStats(totals)
            repairStats(today)
            lastTiers.entries.removeIf { (_, tier) -> tier !in SLAYER_TIER_RANGE }
            if (todayEpochDay < 0L) todayEpochDay = 0L
        }

        private fun repairStats(stats: MutableMap<SkyBlockSlayerType, MutableMap<Int, SlayerKillTimeStats>>) {
            stats.values.forEach { tiers ->
                tiers.keys.removeIf { tier -> tier !in SLAYER_TIER_RANGE }
                tiers.values.forEach(SlayerKillTimeStats::repairLoadedValues)
                tiers.entries.removeIf { (_, value) -> value.kills == 0L }
            }
            stats.entries.removeIf { (_, tiers) -> tiers.isEmpty() }
        }

        companion object {
            private val SLAYER_TIER_RANGE = 1..5
        }
    }

    data class SlayerKillTimeStats(
        @Expose override var kills: Long = 0L,
        @Expose override var totalMillis: Long = 0L,
        @Expose override var bestMillis: Long = 0L,
    ) : ProfileStorageView.SlayerKillTimeStats {
        override val averageMillis: Double?
            get() = totalMillis.takeIf { kills > 0L }?.toDouble()?.div(kills)

        fun record(durationMillis: Long): Long {
            require(durationMillis > 0L)
            val previousBestMillis = bestMillis
            kills++
            totalMillis += durationMillis
            if (bestMillis == 0L || durationMillis < bestMillis) bestMillis = durationMillis
            return previousBestMillis
        }

        fun repairLoadedValues() {
            if (kills <= 0L || totalMillis <= 0L) {
                kills = 0L
                totalMillis = 0L
                bestMillis = 0L
            } else if (bestMillis !in 1..totalMillis) {
                bestMillis = 0L
            }
        }
    }

    data class SackItemData(
        @Expose override var amount: Long = 0L,
        @Expose override var exact: Boolean = false,
        @Expose override var displayName: String = "",
    ) : ProfileStorageView.SackItemData {
        fun repairLoadedValues() {
            if (amount < 0L) {
                amount = 0L
                exact = false
            }
        }
    }

    data class HoneyhiveTrackerData(
        @Expose override var initialized: Boolean = false,
        @Expose override val hives: MutableList<HoneyhiveData> = mutableListOf(),
    ) : ProfileStorageView.HoneyhiveTrackerData {
        fun repairLoadedValues() {
            hives.removeIf { !it.isUsable() }
            val repaired = hives.associateBy { it.locationKey() }.values
            hives.clear()
            hives.addAll(repaired)
        }
    }

    data class HoneyhiveData(
        @Expose override var x: Int = 0,
        @Expose override var y: Int = 0,
        @Expose override var z: Int = 0,
        @Expose override var readyAtMillis: Long = 0L,
        @Expose override var statusObserved: Boolean = false,
    ) : ProfileStorageView.HoneyhiveData {
        override fun hasKnownStatus(): Boolean = statusObserved || readyAtMillis > 0L
        override fun isUsable(): Boolean = readyAtMillis >= 0L
        override fun locationKey(): String = "$x:$y:$z"
    }

    data class DianaBurrowCacheData(
        @Expose override var savedAtMillis: Long = 0L,
        @Expose override val targets: MutableList<DianaBurrowTargetData> = mutableListOf(),
    ) : ProfileStorageView.DianaBurrowCacheData {
        fun repairLoadedValues() {
            if (savedAtMillis < 0L) savedAtMillis = 0L
            targets.forEach { target -> target.repairLoadedValues() }
            targets.removeIf { target -> !target.isUsable() }
        }

        fun clear() {
            savedAtMillis = 0L
            targets.clear()
        }
    }

    data class DianaBurrowTargetData(
        @Expose override var targetId: Long = 0L,
        @Expose override var x: Double = 0.0,
        @Expose override var y: Double = 0.0,
        @Expose override var z: Double = 0.0,
        @Expose override var type: String = "",
        @Expose override var createdAtMillis: Long = 0L,
        @Expose override var updatedAtMillis: Long = 0L,
        @Expose override val guessCandidates: MutableList<DianaBurrowGuessCandidateData> = mutableListOf(),
    ) : ProfileStorageView.DianaBurrowTargetData {
        fun repairLoadedValues() {
            guessCandidates.removeIf { candidate -> !candidate.isUsable() }
        }

        override fun isUsable(): Boolean =
            targetId > 0L &&
                type.isNotBlank() &&
                x.isFinite() &&
                y.isFinite() &&
                z.isFinite()
    }

    data class DianaBurrowGuessCandidateData(
        @Expose override var x: Double = 0.0,
        @Expose override var y: Double = 0.0,
        @Expose override var z: Double = 0.0,
    ) : ProfileStorageView.DianaBurrowGuessCandidateData {
        override fun isUsable(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
    }

    data class DianaBurrowChainData(
        @Expose override var savedAtMillis: Long = 0L,
        @Expose override var completed: Int = 0,
        @Expose override var total: Int = 0,
        @Expose override var nextTargetId: Long = 0L,
        @Expose override var sameBlockStartTargetId: Long = 0L,
        @Expose override val activeTargets: MutableList<DianaBurrowChainTargetData> = mutableListOf(),
    ) : ProfileStorageView.DianaBurrowChainData {
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

        override fun isUsable(): Boolean =
            activeTargets.isNotEmpty() || hasLegacyTarget()

        private fun hasLegacyTarget(): Boolean =
            savedAtMillis >= 0L &&
                total > 0 &&
                completed in 0..total &&
                nextTargetId > 0L &&
                sameBlockStartTargetId >= 0L
    }

    data class DianaBurrowChainTargetData(
        @Expose override var savedAtMillis: Long = 0L,
        @Expose override var completed: Int = 0,
        @Expose override var total: Int = 0,
        @Expose override var nextTargetId: Long = 0L,
        @Expose override var sameBlockStartTargetId: Long = 0L,
    ) : ProfileStorageView.DianaBurrowChainTargetData {
        override fun isUsable(): Boolean =
            savedAtMillis >= 0L &&
                total > 0 &&
                completed in 0..total &&
                nextTargetId > 0L &&
                sameBlockStartTargetId >= 0L
    }

    data class BazaarTrackerData(
        @Expose override var taxPercent: Double = 1.0,
        @Expose override val activeOrders: MutableList<BazaarOrderData> = mutableListOf(),
        @Expose override val itemLots: MutableList<BazaarItemLotData> = mutableListOf(),
        @Expose override val transactions: MutableList<BazaarTransactionData> = mutableListOf(),
        @Expose override var totalKnownProfit: Double = 0.0,
        @Expose override var activeFlipBatchId: Long = 0L,
        @Expose override var nextFlipBatchId: Long = 1L,
        @Expose override var flipAccountingVersion: Int? = null,
    ) : ProfileStorageView.BazaarTrackerData {
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
            normalizeTransactions()
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

        internal fun recordTransaction(transaction: BazaarTransactionData) {
            require(transaction.isUsable()) { "Bazaar transaction must contain an item, amount, total, and timestamp" }
            transactions += transaction
            normalizeTransactions()
        }

        private fun normalizeTransactions() {
            transactions.sortByDescending(BazaarTransactionData::atMillis)
            if (transactions.size > MAX_TRANSACTIONS) {
                transactions.subList(MAX_TRANSACTIONS, transactions.size).clear()
            }
        }

        companion object {
            const val FLIP_ACCOUNTING_VERSION = 1
            const val MAX_TRANSACTIONS = 500
        }
    }

    data class BazaarOrderData(
        @Expose override var id: String = UUID.randomUUID().toString(),
        @Expose override var type: BazaarOrderType = BazaarOrderType.BUY,
        @Expose override var itemName: String = "",
        @Expose override var productId: String? = null,
        @Expose override var amountOrdered: Long = 0L,
        @Expose override var pricePerUnit: Double = 0.0,
        @Expose override var totalCoins: Double = 0.0,
        @Expose override var filledAmount: Long = 0L,
        @Expose override var claimedAmount: Long = 0L,
        @Expose override var claimedCoins: Double = 0.0,
        @Expose override var createdAtMillis: Long = System.currentTimeMillis(),
        @Expose override var updatedAtMillis: Long = System.currentTimeMillis(),
        @Expose override var lastGuiSlot: Int = -1,
        @Expose override var amountResolution: Double = 0.0,
        @Expose override var pricePerUnitResolution: Double = 0.0,
        @Expose override var totalCoinsResolution: Double = 0.0,
        @Expose override var setupConfirmed: Boolean = false,
        @Expose override var flipBatchId: Long? = null,
    ) : ProfileStorageView.BazaarOrderData {
        override fun isUsable(): Boolean = itemName.isNotBlank() && amountOrdered > 0 && pricePerUnit > 0.0
        fun repairLoadedValues() {
            if (!amountResolution.isFinite() || amountResolution < 0.0) amountResolution = 0.0
            if (!pricePerUnitResolution.isFinite() || pricePerUnitResolution < 0.0) pricePerUnitResolution = 0.0
            if (!totalCoinsResolution.isFinite() || totalCoinsResolution < 0.0) totalCoinsResolution = 0.0
        }
        override fun maximumAmount(): Long = if (amountResolution > 0.0) {
            (ceil(amountOrdered + amountResolution).toLong() - 1L).coerceAtLeast(amountOrdered)
        } else {
            amountOrdered
        }
        override fun remainingAmount(): Long = (maximumAmount() - claimedAmount).coerceAtLeast(0L)
        override fun activeValue(): Double = remainingAmount() * pricePerUnit
    }

    data class BazaarItemLotData(
        @Expose override var itemName: String = "",
        @Expose override var productId: String? = null,
        @Expose override var amount: Long = 0L,
        @Expose override var unitCost: Double = 0.0,
        @Expose override var flipBatchId: Long? = null,
        @Expose override var source: BazaarLotSource = BazaarLotSource.BUY_ORDER,
    ) : ProfileStorageView.BazaarItemLotData

    enum class BazaarLotSource {
        BUY_ORDER,
        CRAFTED,
    }

    data class BazaarTransactionData(
        @Expose override var type: BazaarTransactionType = BazaarTransactionType.INSTANT_BUY,
        @Expose override var itemName: String = "",
        @Expose override var productId: String? = null,
        @Expose override var amount: Long = 0L,
        @Expose override var totalCoins: Double = 0.0,
        @Expose override var atMillis: Long = 0L,
    ) : ProfileStorageView.BazaarTransactionData {
        override fun isUsable(): Boolean = itemName.isNotBlank() && amount > 0L && totalCoins > 0.0 && atMillis > 0L
        override fun pricePerUnit(): Double = totalCoins / amount
    }

    enum class BazaarTransactionType(val displayName: String) {
        INSTANT_BUY("Instant Buy"),
        INSTANT_SELL("Instant Sell"),
        BUY_ORDER("Buy Order"),
        SELL_ORDER("Sell Order"),
    }

    data class SlotBindingData(
        @Expose override var firstSlot: Int = -1,
        @Expose override var secondSlot: Int = -1,
    ) : ProfileStorageView.SlotBindingData {
        override fun isValid(): Boolean = SlotBindingGraph.isValidPair(firstSlot, secondSlot)
    }

    data class AttributeShardData(
        @Expose override var amountSyphoned: Int = 0,
        @Expose override var amountInBox: Int = 0,
        @Expose override var enabled: Boolean = true,
    ) : ProfileStorageView.AttributeShardData

    data class AccessoryData(
        @Expose override var displayName: String = "",
        @Expose override var lastSeenSlot: Int = -1,
    ) : ProfileStorageView.AccessoryData

    data class SkyBlockStoragePageData(
        @Expose override var title: String = "",
        @Expose override var rows: Int = 0,
        @Expose override var overviewIcon: String = "",
        @Expose override val items: MutableList<SkyBlockStorageItemData> = mutableListOf(),
    ) : ProfileStorageView.SkyBlockStoragePageData {
        fun repairLoadedValues() {
            rows = rows.coerceIn(0, SKYBLOCK_CONTAINER_MAX_ROWS)
            val targetSize = rows * SLOTS_PER_STORAGE_ROW
            while (items.size > targetSize) items.removeAt(items.lastIndex)
            while (items.size < targetSize) items.add(SkyBlockStorageItemData())
        }
    }

    data class SkyBlockStorageItemData(
        @Expose override var encodedStack: String = "",
    ) : ProfileStorageView.SkyBlockStorageItemData

    companion object {
        const val SKYBLOCK_STORAGE_ENDER_CHEST_PAGES = 9
        const val SKYBLOCK_STORAGE_BACKPACK_PAGES = 18
        const val SKYBLOCK_STORAGE_PAGE_COUNT = SKYBLOCK_STORAGE_ENDER_CHEST_PAGES + SKYBLOCK_STORAGE_BACKPACK_PAGES
        const val SKYBLOCK_STORAGE_PAGE_MAX_ROWS = 5
        const val SKYBLOCK_RIFT_STORAGE_PAGE_COUNT = 2
        const val SKYBLOCK_CONTAINER_MAX_ROWS = 6
        const val SLOTS_PER_STORAGE_ROW = 9
        const val INVENTORY_EQUIPMENT_SLOT_COUNT = 4
        private val PLAYER_INVENTORY_SLOT_RANGE = 0..40
        val SKYBLOCK_TOOLKIT_KEYS = setOf("farming", "hunting")
    }
}

private val PROFIT_TRACKER_PERIODS = setOf("SESSION", "TODAY", "MAYOR", "TOTAL")
private val PROFIT_TRACKER_PRICE_SOURCES = ProfitTrackerPriceSource.entries.mapTo(mutableSetOf()) { it.name }

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
