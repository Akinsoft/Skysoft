package com.skysoft.features.inventory.equipment

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorageView
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.data.skyblock.StatsEquipmentMenu
import com.skysoft.features.inventory.storage.StorageItemStacks
import com.skysoft.features.inventory.storage.encodeItem
import com.skysoft.features.inventory.storage.storage
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import net.minecraft.world.item.ItemStack

internal object InventoryEquipmentCache {
    private val consumers = ActiveConsumerRegistry()

    fun register() {
        ProfileStorageApi.registerConsumer("Inventory Equipment Cache") { consumers.hasActiveConsumers }
        SkyBlockOpenInventoryApi.onChange(
            "Inventory Equipment Cache inventory",
            isActive = { consumers.hasActiveConsumers },
            listener = ::readInventoryEquipmentSnapshot,
        )
        SkysoftClientEvents.onDisconnect("Inventory Equipment Cache reset", ::reset)
        SkyBlockProfileApi.onProfileChange(
            "Inventory Equipment Cache profile reset",
            { consumers.hasActiveConsumers },
        ) { reset() }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) = consumers.register(id, isActive)

    fun stacks(): List<ItemStack> = inventoryEquipmentStorage.map(StorageItemStacks::stackFor)

    private fun reset() {
        lastEquipmentInventoryKey = null
    }
}

private val inventoryEquipmentStorage: List<ProfileStorageView.SkyBlockStorageItemData>
    get() = ProfileStorageApi.storage.inventoryEquipment

private var lastEquipmentInventoryKey: String? = null

private fun readInventoryEquipmentSnapshot(snapshot: SkyBlockOpenInventorySnapshot?) {
    val menuType = snapshot?.let { inventoryEquipmentMenuType(it.title) }
    if (snapshot == null || menuType == null) {
        lastEquipmentInventoryKey = null
        return
    }

    if (snapshot.key == lastEquipmentInventoryKey) return
    lastEquipmentInventoryKey = snapshot.key

    val cells = snapshot.cells.map { cell ->
        EquipmentMenuCell(
            index = cell.index,
            item = cell.item,
            cleanName = cell.item.formattedHoverName().cleanSkyBlockText(),
            isFiller = cell.item.item in MinecraftItems.stainedGlassPanes(),
            isEquippedSelector = cell.item.item == MinecraftItems.limeDye(),
        )
    }
    val items = when (menuType) {
        InventoryEquipmentMenuType.FIXED_COLUMN -> selectEquipmentItems(cells, InventoryEquipmentMenu.FIXED_EQUIPMENT_SLOTS)
        InventoryEquipmentMenuType.EQUIPMENT_SETS -> selectEquipmentSetItems(cells)
    }
    if (items.isEmpty()) return

    updateInventoryEquipmentStorage(items.map(::encodeItem))
}

private data class EquipmentMenuCell(
    val index: Int,
    val item: ItemStack,
    val cleanName: String,
    val isFiller: Boolean,
    val isEquippedSelector: Boolean,
)

private fun selectEquipmentSetItems(cells: List<EquipmentMenuCell>): List<ItemStack> {
    val equippedSelector = cells.singleOrNull { cell ->
        cell.index in InventoryEquipmentMenu.SELECTOR_SLOTS &&
            cell.isEquippedSelector &&
            equippedSetSelectorPattern.matches(cell.cleanName)
    } ?: return emptyList()
    val column = equippedSelector.index - InventoryEquipmentMenu.SELECTOR_ROW_START
    val equipmentSlots = InventoryEquipmentMenu.EQUIPMENT_SET_ROW_STARTS.map { it + column }
    return selectEquipmentItems(cells, equipmentSlots)
}

private fun selectEquipmentItems(cells: List<EquipmentMenuCell>, equipmentSlots: List<Int>): List<ItemStack> {
    val cellsByIndex = cells.associateBy(EquipmentMenuCell::index)
    return equipmentSlots.map { index ->
        val cell = cellsByIndex[index] ?: return emptyList()
        val isEmptyPlaceholder = cell.cleanName.isEmptyEquipmentPlaceholder()
        if (cell.isFiller && !isEmptyPlaceholder) return emptyList()
        if (isEmptyPlaceholder) ItemStack.EMPTY else cell.item
    }
}

private fun inventoryEquipmentMenuType(name: String): InventoryEquipmentMenuType? = when {
    StatsEquipmentMenu.isTitle(name) -> InventoryEquipmentMenuType.FIXED_COLUMN
    legacyLoadoutMenuPattern.matches(name) -> InventoryEquipmentMenuType.FIXED_COLUMN
    equipmentSetsMenuPattern.matches(name) -> InventoryEquipmentMenuType.EQUIPMENT_SETS
    else -> null
}

private fun updateInventoryEquipmentStorage(items: List<ProfileStorage.SkyBlockStorageItemData>) {
    val storageItems = inventoryEquipmentStorage
    if (storageItems.map { it.encodedStack } == items.map { it.encodedStack }) return
    ProfileStorageApi.updateProfile { profile ->
        profile.inventoryEquipment.clear()
        profile.inventoryEquipment.addAll(items)
    }
}

private fun String.isEmptyEquipmentPlaceholder(): Boolean {
    val normalized = lowercase(Locale.ROOT)
    return normalized.startsWith("empty") || normalized.startsWith("slot ")
}

private object InventoryEquipmentMenu {
    const val COLUMNS = 9
    const val EQUIPMENT_COLUMN = 1
    const val SELECTOR_ROW_START = 36

    val FIXED_EQUIPMENT_SLOTS =
        (1..ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT).map { it * COLUMNS + EQUIPMENT_COLUMN }
    val EQUIPMENT_SET_ROW_STARTS =
        (0 until ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT).map { it * COLUMNS }
    val SELECTOR_SLOTS = SELECTOR_ROW_START until SELECTOR_ROW_START + COLUMNS
}

private enum class InventoryEquipmentMenuType {
    FIXED_COLUMN,
    EQUIPMENT_SETS,
}

private val legacyLoadoutMenuPattern = Regex("""^\([0-9]+/[0-9]+\) Loadouts$""")
private val equipmentSetsMenuPattern = Regex("""^\([0-9]+/[0-9]+\) Equipment Sets$""")
private val equippedSetSelectorPattern = Regex("""^Slot [0-9]+: Equipped$""")
