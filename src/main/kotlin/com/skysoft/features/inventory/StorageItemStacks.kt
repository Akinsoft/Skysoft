package com.skysoft.features.inventory

import com.skysoft.data.ProfileStorageView
import com.skysoft.data.skyblock.SkyBlockItemStackCodec
import net.minecraft.world.item.ItemStack

internal object StorageItemStacks {
    private val decodedStacks = linkedMapOf<String, ItemStack>()
    private val emptyOverviewStacks = mutableMapOf<Int, ItemStack>()

    fun stackFor(item: ProfileStorageView.SkyBlockStorageItemData?): ItemStack {
        val encoded = item?.encodedStack?.takeIf { it.isNotBlank() } ?: return ItemStack.EMPTY
        decodedStacks[encoded]?.let { return it }
        val decodedStack = SkyBlockItemStackCodec.decode(encoded) ?: return ItemStack.EMPTY
        decodedStacks[encoded] = decodedStack
        return decodedStack
    }

    fun overviewPlaceholder(pageIndex: Int): ItemStack? = emptyOverviewStacks[pageIndex]

    fun rememberOverviewPlaceholder(pageIndex: Int, stack: ItemStack) {
        emptyOverviewStacks[pageIndex] = stack
    }

    fun removeOverviewPlaceholder(pageIndex: Int) {
        emptyOverviewStacks.remove(pageIndex)
    }

    fun clear() {
        decodedStacks.clear()
        emptyOverviewStacks.clear()
    }
}
