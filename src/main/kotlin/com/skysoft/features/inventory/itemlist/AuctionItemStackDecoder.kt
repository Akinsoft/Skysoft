package com.skysoft.features.inventory.itemlist

import com.mojang.datafixers.DataFixer
import com.mojang.serialization.DynamicOps
import com.skysoft.features.inventory.StorageRuntime
import com.skysoft.features.inventory.migrateLegacySkyBlockItem
import com.skysoft.features.inventory.registryOps
import java.io.ByteArrayInputStream
import java.util.Base64
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.util.datafix.DataFixers
import net.minecraft.world.item.ItemStack

internal fun decodeAuctionItemStack(encoded: String, ops: DynamicOps<Tag> = registryOps()): ItemStack {
    val tag = migrateAuctionItemTag(encoded)
    return ItemStack.CODEC.parse(ops, tag)
        .resultOrPartial { error -> throw IllegalArgumentException("Failed to decode auction item: $error") }
        .orElseThrow { IllegalArgumentException("Failed to decode auction item") }
}

internal fun migrateAuctionItemTag(
    encoded: String,
    dataFixer: DataFixer = DataFixers.getDataFixer(),
    currentDataVersion: Int = SharedConstants.getCurrentVersion().dataVersion().version(),
): CompoundTag {
    require(encoded.length <= MAXIMUM_ENCODED_ITEM_LENGTH) { "Auction item data is too large" }
    val bytes = Base64.getDecoder().decode(encoded)
    val root = NbtIo.readCompressed(
        ByteArrayInputStream(bytes),
        NbtAccounter.create(StorageRuntime.MAX_ITEM_NBT_BYTES),
    )
    val item = root.getList("i").orElseThrow { IllegalArgumentException("Auction item list is missing") }
        .getCompound(0).orElseThrow { IllegalArgumentException("Auction item is missing") }
    return migrateLegacySkyBlockItem(item, dataFixer, currentDataVersion)
}

private const val MAXIMUM_ENCODED_ITEM_LENGTH = 2_000_000
