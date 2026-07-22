package com.skysoft.data.skyblock

import java.util.LinkedHashMap
import java.util.function.Supplier
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.PlayerSkin

internal object SkyBlockEntityStacks {
    private val cache = object : LinkedHashMap<String, ItemStack>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemStack>?): Boolean = size > CACHE_SIZE
    }
    private val skinLookups = mutableMapOf<String, Supplier<PlayerSkin>>()

    fun stack(id: String): ItemStack? = SkyBlockDataRepository.entity(id)?.let(::stack)

    fun stack(entity: SkyBlockEntityInfo): ItemStack? {
        synchronized(cache) { cache[entity.id]?.let { return it } }
        val stack = when {
            entity.texture != null -> SkyBlockStackFactory.texturedHead(entity.texture, Component.literal(entity.name))
            entity.itemId != null -> Identifier.tryParse(entity.itemId)
                ?.let { BuiltInRegistries.ITEM.getValue(it) }
                ?.let(::ItemStack)
            else -> null
        } ?: return null
        synchronized(cache) { cache[entity.id] = stack }
        return stack
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
        synchronized(skinLookups) { skinLookups.clear() }
    }

    fun skinTexture(id: String): Identifier? {
        val stack = stack(id) ?: return null
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        val lookup = synchronized(skinLookups) {
            skinLookups.getOrPut(id) {
                Minecraft.getInstance().skinManager.createLookup(profile.partialProfile(), false)
            }
        }
        return lookup.get().body().texturePath()
    }

    private const val CACHE_SIZE = 128
    private const val LOAD_FACTOR = 0.75f
}
