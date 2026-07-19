package com.skysoft.features.inventory

import com.mojang.datafixers.DataFixer
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.item.ItemStack

internal fun decodeLegacySkyBlockItem(
    item: CompoundTag,
    dataFixer: DataFixer = DataFixers.getDataFixer(),
    currentDataVersion: Int = SharedConstants.getCurrentVersion().dataVersion().version(),
): ItemStack {
    val migrated = migrateLegacySkyBlockItem(item, dataFixer, currentDataVersion)
    return ItemStack.CODEC.parse(registryOps(), migrated)
        .resultOrPartial { error -> throw IllegalArgumentException("Failed to decode legacy SkyBlock item: $error") }
        .orElseThrow { IllegalArgumentException("Failed to decode legacy SkyBlock item") }
}

internal fun migrateLegacySkyBlockItem(
    item: CompoundTag,
    dataFixer: DataFixer = DataFixers.getDataFixer(),
    currentDataVersion: Int = SharedConstants.getCurrentVersion().dataVersion().version(),
): CompoundTag {
    val currentComponents = item.getCompound("components").orElse(null)
    val migrated = dataFixer.update(
        References.ITEM_STACK,
        Dynamic(NbtOps.INSTANCE, item),
        LEGACY_ITEM_DATA_VERSION,
        currentDataVersion,
    ).value as? CompoundTag ?: throw IllegalArgumentException("Legacy SkyBlock item migration returned invalid data")
    if (currentComponents != null) {
        val components = migrated.getCompoundOrEmpty("components").copy().apply { merge(currentComponents) }
        migrated.put("components", components)
    }
    migrated.getCompoundOrEmpty("components")
        .getCompound("minecraft:custom_data")
        .orElse(null)
        ?.getCompound("ExtraAttributes")
        ?.orElse(null)
        ?.let { extraAttributes ->
            val customData = migrated.getCompoundOrEmpty("components")
                .getCompoundOrEmpty("minecraft:custom_data")
                .copy()
                .apply {
                    remove("ExtraAttributes")
                    merge(extraAttributes)
                }
            migrated.getCompoundOrEmpty("components").put("minecraft:custom_data", customData)
        }
    return migrated
}

private const val LEGACY_ITEM_DATA_VERSION = -1
