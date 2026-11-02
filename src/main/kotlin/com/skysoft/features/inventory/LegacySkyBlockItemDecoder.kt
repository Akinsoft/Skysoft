package com.skysoft.features.inventory

import com.mojang.serialization.Dynamic
import com.skysoft.data.skyblock.SkyBlockItemStackCodec
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.item.ItemStack

internal fun decodeLegacySkyBlockItem(item: CompoundTag): ItemStack {
    val migrated = migrateLegacySkyBlockItem(item)
    return ItemStack.CODEC.parse(SkyBlockItemStackCodec.registryOps(), migrated)
        .resultOrPartial { error -> throw IllegalArgumentException("Failed to decode legacy SkyBlock item: $error") }
        .orElseThrow { IllegalArgumentException("Failed to decode legacy SkyBlock item") }
}

internal fun migrateLegacySkyBlockItem(item: CompoundTag): CompoundTag {
    val currentComponents = item.getCompound("components").orElse(null)
    val migrated = DataFixers.getDataFixer().update(
        References.ITEM_STACK,
        Dynamic(NbtOps.INSTANCE, item),
        LEGACY_ITEM_DATA_VERSION,
        SharedConstants.getCurrentVersion().dataVersion().version(),
    ).value as? CompoundTag ?: throw IllegalArgumentException("Legacy SkyBlock item migration returned invalid data")
    if (currentComponents != null) {
        val components = migrated.getCompoundOrEmpty("components").copy().apply { merge(currentComponents) }
        migrated.put("components", components)
    }
    val components = migrated.getCompoundOrEmpty("components")
    components.getCompound("minecraft:custom_data").ifPresent { customData ->
        customData.getCompound("ExtraAttributes").ifPresent { extraAttributes ->
            val flattened = customData.copy().apply {
                remove("ExtraAttributes")
                merge(extraAttributes)
            }
            components.put("minecraft:custom_data", flattened)
        }
    }
    return migrated
}

private const val LEGACY_ITEM_DATA_VERSION = -1
