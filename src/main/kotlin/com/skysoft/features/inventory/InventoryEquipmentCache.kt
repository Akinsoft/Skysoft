package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.data.skyblock.StatsEquipmentMenu
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.nonPlayerInventoryKey
import com.skysoft.utils.gui.nonPlayerSlots
import java.util.Locale
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

internal object InventoryEquipmentCache {
    private val consumers = ActiveConsumerRegistry()

    fun register() {
        ProfileStorageApi.registerConsumer("Inventory Equipment Cache") { consumers.hasActiveConsumers }
        SkysoftClientEvents.onEndTick(
            "Inventory Equipment Cache tick",
            isActive = { consumers.hasActiveConsumers || lastEquipmentInventoryKey != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Inventory Equipment Cache reset", ::reset)
        SkyBlockProfileApi.onProfileChange(
            "Inventory Equipment Cache profile reset",
            { consumers.hasActiveConsumers },
        ) { reset() }
        registerCloseFlush()
    }

    private fun registerCloseFlush() {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (consumers.hasActiveConsumers && screen is AbstractContainerScreen<*>) {
                ScreenEvents.remove(screen).register {
                    SkysoftErrorBoundary.run("Inventory Equipment Cache close flush") {
                        readInventoryEquipmentScreen(screen)
                    }
                }
            }
        }
    }

    fun registerConsumer(id: String, isActive: () -> Boolean) = consumers.register(id, isActive)

    fun stacks(): List<ItemStack> = inventoryEquipmentStorage.map(::stackFor)

    private fun tick() {
        if (!consumers.hasActiveConsumers || !HypixelLocationState.inSkyBlock) {
            reset()
            return
        }
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: run {
            reset()
            return
        }
        readInventoryEquipmentScreen(screen)
    }

    private fun reset() {
        lastEquipmentInventoryKey = null
    }
}

private val inventoryEquipmentStorage: MutableList<ProfileStorage.SkyBlockStorageItemData>
    get() = ProfileStorageApi.storage.inventoryEquipment.also(::repairInventoryEquipmentItems)

internal var lastEquipmentInventoryKey: String? = null

internal fun readInventoryEquipmentScreen(screen: AbstractContainerScreen<*>): ChangeResult {
    val inventoryName = screen.title.cleanSkyBlockText()
    if (!isInventoryEquipmentMenuName(inventoryName)) {
        lastEquipmentInventoryKey = null
        return ChangeResult.UNCHANGED
    }

    val key = screen.nonPlayerInventoryKey(inventoryName)
    if (key == lastEquipmentInventoryKey) return ChangeResult.UNCHANGED
    lastEquipmentInventoryKey = key

    val items = selectEquipmentMenuItems(
        inventoryName,
        screen.nonPlayerSlots().map { slot ->
            EquipmentMenuCell(
                index = slot.containerSlot,
                item = slot.item.copy(),
                cleanName = slot.item.formattedHoverName().cleanSkyBlockText(),
                isFiller = slot.item.item in MinecraftItems.stainedGlassPanes(),
                isEquippedSelector = slot.item.item == MinecraftItems.limeDye(),
            )
        },
        emptyItem = ItemStack.EMPTY,
    )
    if (items.size < ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) return ChangeResult.UNCHANGED

    val result = updateInventoryEquipmentStorage(items.map(::encodeItem))
    if (result == ChangeResult.CHANGED) ProfileStorageApi.markDirty()
    return result
}

internal fun repairInventoryEquipmentItems(items: MutableList<ProfileStorage.SkyBlockStorageItemData>) {
    while (items.size > ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) items.removeAt(items.lastIndex)
    while (items.size < ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) items.add(ProfileStorage.SkyBlockStorageItemData())
}

internal fun <T> selectEquipmentMenuItems(
    inventoryName: String,
    cells: List<EquipmentMenuCell<T>>,
    emptyItem: T,
): List<T> = when (inventoryEquipmentMenuType(inventoryName)) {
    InventoryEquipmentMenuType.FIXED_COLUMN -> selectEquipmentItems(
        cells,
        InventoryEquipmentMenu.FIXED_EQUIPMENT_SLOTS,
        emptyItem,
    )

    InventoryEquipmentMenuType.EQUIPMENT_SETS -> selectEquipmentSetItems(cells, emptyItem)
    null -> emptyList()
}

internal data class EquipmentMenuCell<T>(
    val index: Int,
    val item: T,
    val cleanName: String,
    val isFiller: Boolean,
    val isEquippedSelector: Boolean = false,
)

internal fun isInventoryEquipmentMenuName(name: String): Boolean =
    inventoryEquipmentMenuType(name) != null

private fun <T> selectEquipmentSetItems(
    cells: List<EquipmentMenuCell<T>>,
    emptyItem: T,
): List<T> {
    val equippedSelector = cells.singleOrNull { cell ->
        cell.index in InventoryEquipmentMenu.SELECTOR_SLOTS &&
            cell.isEquippedSelector &&
            equippedSetSelectorPattern.matches(cell.cleanName)
    } ?: return emptyList()
    val column = equippedSelector.index - InventoryEquipmentMenu.SELECTOR_ROW_START
    val equipmentSlots = InventoryEquipmentMenu.EQUIPMENT_SET_ROW_STARTS.map { it + column }
    return selectEquipmentItems(cells, equipmentSlots, emptyItem)
}

private fun <T> selectEquipmentItems(
    cells: List<EquipmentMenuCell<T>>,
    equipmentSlots: List<Int>,
    emptyItem: T,
): List<T> {
    val cellsByIndex = cells.associateBy(EquipmentMenuCell<T>::index)
    return equipmentSlots.map { index ->
        val cell = cellsByIndex[index] ?: return emptyList()
        val isEmptyPlaceholder = cell.cleanName.isEmptyEquipmentPlaceholder()
        if (cell.isFiller && !isEmptyPlaceholder) return emptyList()
        if (isEmptyPlaceholder) emptyItem else cell.item
    }
}

private fun inventoryEquipmentMenuType(name: String): InventoryEquipmentMenuType? = when {
    StatsEquipmentMenu.isTitle(name) -> InventoryEquipmentMenuType.FIXED_COLUMN
    legacyLoadoutMenuPattern.matches(name) -> InventoryEquipmentMenuType.FIXED_COLUMN
    equipmentSetsMenuPattern.matches(name) -> InventoryEquipmentMenuType.EQUIPMENT_SETS
    else -> null
}

private fun updateInventoryEquipmentStorage(items: List<ProfileStorage.SkyBlockStorageItemData>): ChangeResult {
    val storageItems = inventoryEquipmentStorage
    if (storageItems.map { it.encodedStack } == items.map { it.encodedStack }) return ChangeResult.UNCHANGED
    storageItems.clear()
    storageItems.addAll(items)
    repairInventoryEquipmentItems(storageItems)
    return ChangeResult.CHANGED
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
