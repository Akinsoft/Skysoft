package com.skysoft.data.skyblock

import com.skysoft.utils.boundedAccessOrderMap
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

internal object SkyBlockEntityStacks {
    private val cache = boundedAccessOrderMap<EntityStackKey, CachedEntityStack>(CACHE_SIZE)

    fun stack(id: String): ItemStack? = SkyBlockDataRepository.entity(id)?.let(::stack)

    fun stack(entity: SkyBlockEntityInfo): ItemStack? = cachedStack(entity)?.stack

    fun skinTexture(entity: SkyBlockEntityInfo): Identifier? = cachedStack(entity)?.skinTexture()

    private fun cachedStack(entity: SkyBlockEntityInfo): CachedEntityStack? {
        val key = EntityStackKey(entity.name, entity.texture, entity.itemId)
        synchronized(cache) { cache[key]?.let { return it } }
        val stack = when {
            entity.texture != null -> SkyBlockStackFactory.texturedHead(entity.texture, Component.literal(entity.name))
            entity.itemId != null -> Identifier.tryParse(entity.itemId)
                ?.let { BuiltInRegistries.ITEM.getValue(it) }
                ?.let(::ItemStack)
            else -> null
        } ?: return null
        val cached = CachedEntityStack(stack)
        synchronized(cache) { cache[key] = cached }
        return cached
    }

    private class CachedEntityStack(val stack: ItemStack) {
        private val skinLookup by lazy {
            stack.get(DataComponents.PROFILE)?.let { profile ->
                Minecraft.getInstance().skinManager.createLookup(profile.partialProfile(), false)
            }
        }

        fun skinTexture(): Identifier? = skinLookup?.get()?.body()?.texturePath()
    }

    private data class EntityStackKey(val name: String, val texture: String?, val itemId: String?)

    private const val CACHE_SIZE = 128
}
