package com.skysoft.data

import com.skysoft.data.skyblock.BazaarOrderType
import com.skysoft.data.skyblock.SkyBlockSkill
import com.skysoft.data.skyblock.SkyBlockSkillView
import com.skysoft.data.skyblock.SkyBlockSlayerType
import java.util.UUID

interface ProfileStorageView {
    val players: Map<String, PlayerSpecific>
    val profiles: Map<String, ProfileSpecific>

    interface PlayerSpecific {
        val profiles: Map<String, ProfileSpecific>
        val cookieBuffExpiresAtMillis: Long
    }

    interface ProfileSpecific {
        val profileName: String
        val pets: List<StoredPetData>
        val expSharePets: List<UUID?>
        val currentPetUuid: UUID?
        val beastmasterPetXpMultiplier: Double?
        val accessories: Map<String, AccessoryData>
        val skyBlockStoragePages: Map<Int, SkyBlockStoragePageData>
        val skyBlockRiftStoragePages: Map<Int, SkyBlockStoragePageData>
        val skyBlockToolkits: Map<String, SkyBlockStoragePageData>
        val skyBlockToolkitIcon: String
        val inventoryEquipment: List<SkyBlockStorageItemData>
        val skillData: Map<SkyBlockSkill, SkyBlockSkillView>
        val attributeShards: Map<String, AttributeShardData>
        val slotBindings: List<SlotBindingData>
        val slotLocks: List<Int>
        val protectedItemUuids: List<UUID>
        val inventoryItemCounts: Map<String, Int>
        val sackContents: Map<String, SackItemData>
        val profitTracker: ProfitTrackerData
        val slayerTimeToKill: SlayerTimeToKillData
        val bazaarTracker: BazaarTrackerData
        val honeyhiveTracker: HoneyhiveTrackerData
        val dianaBurrowCache: DianaBurrowCacheData
        val dianaBurrowChain: DianaBurrowChainData
    }

    interface ProfitTrackerData {
        val totals: Map<String, ProfitTrackerStats>
        val todayEpochDay: Long
        val today: Map<String, ProfitTrackerStats>
        val mythologicalRitualMayorKey: String
        val mythologicalRitualMayor: ProfitTrackerStats
        val displayPeriods: Map<String, String>
        val itemCustomizations: Map<String, ProfitTrackerItemCustomizations>
        val farmingKernelItem: String
        val farmingKernelPriceSource: String
        val lastPreset: String
    }

    interface ProfitTrackerItemCustomizations {
        val excludedItems: List<String>
        val customItems: List<String>
        val priceSources: Map<String, String>
    }

    interface ProfitTrackerStats {
        val itemCounts: Map<String, Long>
        val costs: Map<String, Long>
        val coins: Double
        val kernels: Long
        val activeMillis: Long
        val actions: Long
        val pestKills: Map<String, Long>
    }

    interface SlayerTimeToKillData {
        val totals: Map<SkyBlockSlayerType, Map<Int, SlayerKillTimeStats>>
        val todayEpochDay: Long
        val today: Map<SkyBlockSlayerType, Map<Int, SlayerKillTimeStats>>
        val lastTiers: Map<SkyBlockSlayerType, Int>
    }

    interface SlayerKillTimeStats {
        val kills: Long
        val totalMillis: Long
        val bestMillis: Long
        val averageMillis: Double?
    }

    interface SackItemData {
        val amount: Long
        val exact: Boolean
        val displayName: String
    }

    interface HoneyhiveTrackerData {
        val initialized: Boolean
        val hives: List<HoneyhiveData>
    }

    interface HoneyhiveData {
        val x: Int
        val y: Int
        val z: Int
        val readyAtMillis: Long
        val statusObserved: Boolean
        fun hasKnownStatus(): Boolean
        fun isUsable(): Boolean
        fun locationKey(): String
    }

    interface DianaBurrowCacheData {
        val savedAtMillis: Long
        val targets: List<DianaBurrowTargetData>
    }

    interface DianaBurrowTargetData {
        val targetId: Long
        val x: Double
        val y: Double
        val z: Double
        val type: String
        val createdAtMillis: Long
        val updatedAtMillis: Long
        val guessCandidates: List<DianaBurrowGuessCandidateData>
        fun isUsable(): Boolean
    }

    interface DianaBurrowGuessCandidateData {
        val x: Double
        val y: Double
        val z: Double
        fun isUsable(): Boolean
    }

    interface DianaBurrowChainData {
        val savedAtMillis: Long
        val completed: Int
        val total: Int
        val nextTargetId: Long
        val sameBlockStartTargetId: Long
        val activeTargets: List<DianaBurrowChainTargetData>
        fun isUsable(): Boolean
    }

    interface DianaBurrowChainTargetData {
        val savedAtMillis: Long
        val completed: Int
        val total: Int
        val nextTargetId: Long
        val sameBlockStartTargetId: Long
        fun isUsable(): Boolean
    }

    interface BazaarTrackerData {
        val taxPercent: Double
        val activeOrders: List<BazaarOrderData>
        val itemLots: List<BazaarItemLotData>
        val transactions: List<BazaarTransactionData>
        val totalKnownProfit: Double
        val activeFlipBatchId: Long
        val nextFlipBatchId: Long
        val flipAccountingVersion: Int?
    }

    interface BazaarOrderData {
        val id: String
        val type: BazaarOrderType
        val itemName: String
        val productId: String?
        val amountOrdered: Long
        val pricePerUnit: Double
        val totalCoins: Double
        val filledAmount: Long
        val claimedAmount: Long
        val claimedCoins: Double
        val createdAtMillis: Long
        val updatedAtMillis: Long
        val lastGuiSlot: Int
        val amountResolution: Double
        val pricePerUnitResolution: Double
        val totalCoinsResolution: Double
        val setupConfirmed: Boolean
        val flipBatchId: Long?
        fun isUsable(): Boolean
        fun maximumAmount(): Long
        fun remainingAmount(): Long
        fun activeValue(): Double
    }

    interface BazaarItemLotData {
        val itemName: String
        val productId: String?
        val amount: Long
        val unitCost: Double
        val flipBatchId: Long?
        val source: ProfileStorage.BazaarLotSource
    }

    interface BazaarTransactionData {
        val type: ProfileStorage.BazaarTransactionType
        val itemName: String
        val productId: String?
        val amount: Long
        val totalCoins: Double
        val atMillis: Long
        fun isUsable(): Boolean
        fun pricePerUnit(): Double
    }

    interface SlotBindingData {
        val firstSlot: Int
        val secondSlot: Int
        fun isValid(): Boolean
    }

    interface AttributeShardData {
        val amountSyphoned: Int
        val amountInBox: Int
        val enabled: Boolean
    }

    interface AccessoryData {
        val displayName: String
        val lastSeenSlot: Int
    }

    interface SkyBlockStoragePageData {
        val title: String
        val rows: Int
        val overviewIcon: String
        val items: List<SkyBlockStorageItemData>
    }

    interface SkyBlockStorageItemData {
        val encodedStack: String
    }
}
