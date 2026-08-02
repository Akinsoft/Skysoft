package com.skysoft.utils

import net.minecraft.world.item.ItemStack

internal class ItemStackComponentCache<T> {
    private val entriesByHash = mutableMapOf<Int, MutableList<Entry<T>>>()

    fun getOrPut(stack: ItemStack, create: () -> T): T {
        val hash = ItemStack.hashItemAndComponents(stack)
        entriesByHash[hash]?.firstOrNull { ItemStack.isSameItemSameComponents(it.stack, stack) }?.let {
            return it.value
        }
        if (entriesByHash.size >= MAX_HASHES) entriesByHash.clear()
        return create().also { value ->
            entriesByHash.getOrPut(hash) { mutableListOf() } += Entry(stack.copyWithCount(1), value)
        }
    }

    private data class Entry<T>(val stack: ItemStack, val value: T)

    private companion object {
        const val MAX_HASHES = 512
    }
}
