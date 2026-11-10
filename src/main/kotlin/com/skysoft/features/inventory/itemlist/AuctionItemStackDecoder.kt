package com.skysoft.features.inventory.itemlist

import com.mojang.serialization.DynamicOps
import com.skysoft.features.inventory.storage.StorageRuntime
import com.skysoft.features.inventory.migrateLegacySkyBlockItem
import java.io.ByteArrayInputStream
import java.util.Base64
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

internal fun decodeAuctionItemStack(encoded: String, ops: DynamicOps<Tag>): ItemStack {
    val tag = migrateAuctionItemTag(encoded)
    return ItemStack.CODEC.parse(ops, tag)
        .resultOrPartial { error -> throw IllegalArgumentException("Failed to decode auction item: $error") }
        .orElseThrow { IllegalArgumentException("Failed to decode auction item") }
}

private fun migrateAuctionItemTag(encoded: String): CompoundTag {
    require(encoded.length <= MAXIMUM_ENCODED_ITEM_LENGTH) { "Auction item data is too large" }
    val bytes = Base64.getDecoder().decode(encoded)
    val root = NbtIo.readCompressed(
        ByteArrayInputStream(bytes),
        NbtAccounter.create(StorageRuntime.MAX_ITEM_NBT_BYTES),
    )
    val item = root.getList("i").orElseThrow { IllegalArgumentException("Auction item list is missing") }
        .getCompound(0).orElseThrow { IllegalArgumentException("Auction item is missing") }
    return migrateLegacySkyBlockItem(item)
}

private const val MAXIMUM_ENCODED_ITEM_LENGTH = 2_000_000
