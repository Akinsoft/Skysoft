package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.io.ByteArrayInputStream
import java.util.WeakHashMap
import kotlin.math.ceil
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.world.item.ItemStack

object StoragePreviews {
    private val embeddedPreviews = WeakHashMap<ItemStack, CachedEmbeddedPreview>()
    private val config get() = SkysoftConfigGui.config().inventory.storagePreviews

    fun register() {
        StorageCache.registerConsumer("Storage Previews") {
            config.enabled && (config.settings.backpacks || config.settings.enderChests)
        }
        SkyBlockDataRepository.Demand.register("Storage Previews") {
            config.enabled && (config.settings.personalCompactors || config.settings.personalDeletors)
        }
    }

    internal fun tooltipFor(stack: ItemStack): StoragePreviewTooltip? {
        if (!config.enabled || !HypixelLocationState.inSkyBlock || stack.isEmpty) return null
        storagePageTooltip(stack)?.let { return it }

        val componentHash = ItemStack.hashItemAndComponents(stack)
        val settingsSignature = settingsSignature()
        val catalogVersion = SkyBlockDataRepository.snapshotVersion
        embeddedPreviews[stack]
            ?.takeIf {
                it.componentHash == componentHash &&
                    it.settingsSignature == settingsSignature &&
                    it.catalogVersion == catalogVersion
            }
            ?.let { return it.tooltip }
        val tooltip = embeddedTooltip(stack) ?: return null
        embeddedPreviews[stack] = CachedEmbeddedPreview(
            componentHash,
            settingsSignature,
            catalogVersion,
            tooltip,
        )
        return tooltip
    }

    private fun storagePageTooltip(stack: ItemStack): StoragePreviewTooltip? {
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: return null
        if (screen.title.cleanSkyBlockText() != STORAGE_TITLE) return null
        val hoveredSlot = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot() ?: return null
        if (hoveredSlot.item !== stack) return null
        val pageIndex = StorageOverviewSlots.pageIndexForSlot(hoveredSlot.containerSlot) ?: return null
        val isEnderChest = pageIndex < ProfileStorage.SKYBLOCK_STORAGE_ENDER_CHEST_PAGES
        if (isEnderChest && !config.settings.enderChests) return null
        if (!isEnderChest && !config.settings.backpacks) return null
        val page = storage.skyBlockStoragePages[pageIndex] ?: return null
        if (page.rows <= 0) return null
        return StoragePreviewTooltip(
            items = page.items.map(::stackFor),
            columns = ProfileStorage.SLOTS_PER_STORAGE_ROW,
            rows = page.rows,
        )
    }

    private fun embeddedTooltip(stack: ItemStack): StoragePreviewTooltip? {
        val customData = stack.extraAttributes() ?: return null
        val itemId = stack.skyBlockId()
        val personalStorage = personalStorageTooltip(itemId, customData)
        if (personalStorage != null) return personalStorage
        if (itemId == NEW_YEAR_CAKE_BAG_ID && config.settings.cakeBags) {
            return encodedContainerTooltip(customData, NEW_YEAR_CAKE_BAG_DATA_KEY)
        }
        if (!config.settings.backpacks) return null
        val backpackDataKey = customData.keySet().firstOrNull { it.endsWith(BACKPACK_DATA_SUFFIX) } ?: return null
        return encodedContainerTooltip(customData, backpackDataKey)
    }

    private fun personalStorageTooltip(itemId: String?, customData: CompoundTag): StoragePreviewTooltip? {
        val match = itemId?.let(PERSONAL_STORAGE_ID::matchEntire) ?: return null
        val type = match.groups["type"]?.value ?: return null
        val size = match.groups["size"]?.value ?: return null
        val isCompactor = type == COMPACTOR_TYPE
        if (isCompactor && !config.settings.personalCompactors) return null
        if (!isCompactor && !config.settings.personalDeletors) return null
        val dimensions = PERSONAL_STORAGE_DIMENSIONS[size] ?: return null
        val itemKeyPrefix = if (isCompactor) PERSONAL_COMPACTOR_KEY_PREFIX else PERSONAL_DELETOR_KEY_PREFIX
        val items = MutableList(dimensions.columns * dimensions.rows) { ItemStack.EMPTY }
        customData.keySet().forEach { key ->
            if (!key.startsWith(itemKeyPrefix)) return@forEach
            val slot = key.removePrefix(itemKeyPrefix).toIntOrNull() ?: return@forEach
            if (slot !in items.indices) return@forEach
            val configuredItemId = customData.getString(key).orElse("")
            if (configuredItemId.isBlank()) return@forEach
            items[slot] = SkyBlockDataRepository.stack(SkyBlockDataRepository.itemKey(configuredItemId)) ?: return null
        }
        return StoragePreviewTooltip(items, dimensions.columns, dimensions.rows)
    }

    private fun encodedContainerTooltip(customData: CompoundTag, key: String): StoragePreviewTooltip? {
        val bytes = customData.getByteArray(key).orElse(null) ?: return null
        require(bytes.size <= StorageRuntime.MAX_ITEM_NBT_BYTES) { "Storage preview item data is too large" }
        val root = NbtIo.readCompressed(
            ByteArrayInputStream(bytes),
            NbtAccounter.create(StorageRuntime.MAX_ITEM_NBT_BYTES),
        )
        val encodedItems = root.getList(ENCODED_ITEMS_KEY)
            .orElseThrow { IllegalArgumentException("Storage preview item list is missing") }
        require(encodedItems.size <= MAXIMUM_CONTAINER_SLOTS) { "Storage preview contains too many slots" }
        val rows = ceil(encodedItems.size / ProfileStorage.SLOTS_PER_STORAGE_ROW.toDouble()).toInt().coerceAtLeast(1)
        val items = MutableList(rows * ProfileStorage.SLOTS_PER_STORAGE_ROW) { ItemStack.EMPTY }
        repeat(encodedItems.size) { index ->
            val item = encodedItems.getCompound(index)
                .orElseThrow { IllegalArgumentException("Storage preview contains an invalid item") }
            if (!item.isEmpty) items[index] = decodeLegacySkyBlockItem(item)
        }
        return StoragePreviewTooltip(items, ProfileStorage.SLOTS_PER_STORAGE_ROW, rows)
    }

    private fun settingsSignature(): Int {
        val settings = config.settings
        var signature = 0
        if (settings.cakeBags) signature = signature or CAKE_BAGS_SETTING
        if (settings.personalDeletors) signature = signature or PERSONAL_DELETORS_SETTING
        if (settings.personalCompactors) signature = signature or PERSONAL_COMPACTORS_SETTING
        if (settings.backpacks) signature = signature or BACKPACKS_SETTING
        return signature
    }

    private data class CachedEmbeddedPreview(
        val componentHash: Int,
        val settingsSignature: Int,
        val catalogVersion: Long,
        val tooltip: StoragePreviewTooltip,
    )

    private data class PreviewDimensions(val rows: Int, val columns: Int)

    private const val STORAGE_TITLE = "Storage"
    private const val NEW_YEAR_CAKE_BAG_ID = "NEW_YEAR_CAKE_BAG"
    private const val NEW_YEAR_CAKE_BAG_DATA_KEY = "new_year_cake_bag_data"
    private const val BACKPACK_DATA_SUFFIX = "backpack_data"
    private const val ENCODED_ITEMS_KEY = "i"
    private const val MAXIMUM_CONTAINER_SLOTS = 54
    private const val COMPACTOR_TYPE = "COMPACTOR"
    private const val PERSONAL_COMPACTOR_KEY_PREFIX = "personal_compact_"
    private const val PERSONAL_DELETOR_KEY_PREFIX = "personal_deletor_"
    private const val CAKE_BAGS_SETTING = 1
    private const val PERSONAL_DELETORS_SETTING = 1 shl 1
    private const val PERSONAL_COMPACTORS_SETTING = 1 shl 2
    private const val BACKPACKS_SETTING = 1 shl 3
    private val PERSONAL_STORAGE_ID = Regex("PERSONAL_(?<type>COMPACTOR|DELETOR)_(?<size>[0-9]+)")
    private val PERSONAL_STORAGE_DIMENSIONS = mapOf(
        "4000" to PreviewDimensions(1, 1),
        "5000" to PreviewDimensions(1, 3),
        "6000" to PreviewDimensions(1, 7),
        "7000" to PreviewDimensions(2, 6),
    )
}
