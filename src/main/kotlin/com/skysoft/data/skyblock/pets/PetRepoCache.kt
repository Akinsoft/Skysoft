package com.skysoft.data.skyblock.pets

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockStackFactory
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

internal object PetRepoCache {
    val gson = Gson()
    private val skinStacks = ConcurrentHashMap<String, ItemStack>()
    val animatedSkinMatches = ConcurrentHashMap<String, AnimatedSkinJson>()
    val missingAnimatedSkinMatches = ConcurrentHashMap.newKeySet<String>()
    private val animationCacheLock = Any()
    private val animatedSkinFrames = HashMap<PetAnimationFramesKey, List<PetItemFrame>>()

    private var catalogVersion = -1L

    @Volatile
    var petAnimations: PetAnimationsJson? = null
        set(value) {
            synchronized(animationCacheLock) {
                field = value
                clearAnimationCaches()
            }
        }

    @Volatile
    var learnedPetAnimations: PetAnimationsJson = PetAnimationsJson()
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
        if (catalogVersion != SkyBlockDataRepository.snapshotVersion) {
            animatedSkinFrames.clear()
            catalogVersion = SkyBlockDataRepository.snapshotVersion
        }
        val resolvedKey = key()
        animatedSkinFrames[resolvedKey] ?: create(resolvedKey)?.also {
            animatedSkinFrames[resolvedKey] = it
        }
    }

    private fun clearAnimationCaches() {
        skinStacks.clear()
        animatedSkinFrames.clear()
        animatedSkinMatches.clear()
        missingAnimatedSkinMatches.clear()
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
