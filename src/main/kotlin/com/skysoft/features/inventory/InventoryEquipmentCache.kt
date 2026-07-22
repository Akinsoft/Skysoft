package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorage
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.utils.ActiveConsumerRegistry
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.MinecraftItems
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.nonPlayerInventoryKey
import com.skysoft.utils.gui.nonPlayerSlots
import java.util.Locale
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
        screen.nonPlayerSlots().map { slot ->
            EquipmentMenuCell(
                index = slot.containerSlot,
                item = slot.item.copy(),
                cleanName = slot.item.formattedHoverName().cleanSkyBlockText(),
                isFiller = slot.item.item in MinecraftItems.stainedGlassPanes(),
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
    cells: List<EquipmentMenuCell<T>>,
    emptyItem: T,
): List<T> {
    val result = ArrayList<T>(ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT)
    for (cell in cells.sortedBy(EquipmentMenuCell<T>::index)) {
        if (cell.index % InventoryEquipmentMenu.COLUMNS != InventoryEquipmentMenu.EQUIPMENT_COLUMN) continue
        val isEmptyPlaceholder = cell.cleanName.isEmptyEquipmentPlaceholder()
        if (cell.isFiller && !isEmptyPlaceholder) continue
        result += if (isEmptyPlaceholder) emptyItem else cell.item
        if (result.size == ProfileStorage.INVENTORY_EQUIPMENT_SLOT_COUNT) break
    }
    return result
}

internal data class EquipmentMenuCell<T>(
    val index: Int,
    val item: T,
    val cleanName: String,
    val isFiller: Boolean,
)

internal fun isInventoryEquipmentMenuName(name: String): Boolean =
    name == InventoryEquipmentMenu.STATS_TITLE || loadoutMenuPattern.matches(name)

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
    const val STATS_TITLE = "Your Equipment and Stats"
    const val COLUMNS = 9
    const val EQUIPMENT_COLUMN = 1
}

private val loadoutMenuPattern = Regex("""^\([0-9]+/[0-9]+\) Loadouts$""")
