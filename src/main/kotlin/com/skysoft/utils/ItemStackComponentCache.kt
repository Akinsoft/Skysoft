package com.skysoft.utils

import net.minecraft.world.item.ItemStack

internal class ItemStackComponentCache<T> {
    private val entriesByHash = mutableMapOf<Int, MutableList<Entry<T>>>()
    private var entryCount = 0

    fun getOrPut(stack: ItemStack, create: () -> T): T {
        val hash = ItemStack.hashItemAndComponents(stack)
        entriesByHash[hash]?.firstOrNull { ItemStack.isSameItemSameComponents(it.stack, stack) }?.let {
            return it.value
        }
        if (entryCount >= MAX_ENTRIES) {
            entriesByHash.clear()
            entryCount = 0
        }
        return create().also { value ->
            entriesByHash.getOrPut(hash) { mutableListOf() } += Entry(stack.copyWithCount(1), value)
            entryCount++
        }
    }

    private data class Entry<T>(val stack: ItemStack, val value: T)

    private companion object {
        const val MAX_ENTRIES = 512
    }
}
