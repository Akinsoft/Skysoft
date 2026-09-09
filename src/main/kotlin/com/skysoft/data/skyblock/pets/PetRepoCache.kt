package com.skysoft.data.skyblock.pets

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockStackFactory
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

internal object PetRepoCache {
    private val skinStacks = ConcurrentHashMap<String, ItemStack>()
    private val animationCacheLock = Any()
    private val animatedSkinFrames = HashMap<PetAnimationFramesKey, List<PetItemFrame>>()

    private var catalogVersion = -1L

    @Volatile
    var petAnimations: PetAnimationCatalog? = null
        set(value) {
            synchronized(animationCacheLock) {
                field = value
                clearAnimationCaches()
            }
        }

    @Volatile
    var learnedPetAnimations = PetAnimationCatalog(PetAnimationsJson())
        set(value) {
            synchronized(animationCacheLock) {
                field = value
                clearAnimationCaches()
            }
        }

    fun skinStack(texture: String): ItemStack = skinStacks.computeIfAbsent(texture) {
        SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin"))
    }.copy()

    fun animatedSkinFrames(
        key: () -> PetAnimationFramesKey,
        create: (PetAnimationFramesKey) -> List<PetItemFrame>?,
    ): List<PetItemFrame>? = synchronized(animationCacheLock) {
        val currentVersion = SkyBlockDataRepository.snapshotVersion
        if (catalogVersion != currentVersion) {
            animatedSkinFrames.clear()
            catalogVersion = currentVersion
        }
        val resolvedKey = key()
        animatedSkinFrames[resolvedKey] ?: create(resolvedKey)?.also {
            animatedSkinFrames[resolvedKey] = it
        }
    }

    private fun clearAnimationCaches() {
        skinStacks.clear()
        animatedSkinFrames.clear()
    }
}

internal class PetAnimationFramesKey(
    val animation: AnimatedSkinJson?,
    val staticSkinInternalName: String?,
    val staticDisplayIconTexture: String?,
    val firstFrameOnly: Boolean,
    val animationSpeed: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is PetAnimationFramesKey &&
            animation === other.animation &&
            staticSkinInternalName == other.staticSkinInternalName &&
            staticDisplayIconTexture == other.staticDisplayIconTexture &&
            firstFrameOnly == other.firstFrameOnly &&
            animationSpeed == other.animationSpeed

    override fun hashCode(): Int {
        var result = System.identityHashCode(animation)
        result = 31 * result + staticSkinInternalName.hashCode()
        result = 31 * result + staticDisplayIconTexture.hashCode()
        result = 31 * result + firstFrameOnly.hashCode()
        return 31 * result + animationSpeed.hashCode()
    }
}
