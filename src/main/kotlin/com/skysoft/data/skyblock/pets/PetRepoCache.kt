package com.skysoft.data.skyblock.pets

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockItemJson
import com.skysoft.data.skyblock.SkyBlockPetInfo
import com.skysoft.utils.net.PendingHttpRequests
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

internal object PetRepoCache {
    const val RAW_BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master"
    const val GITHUB_TREE_URL =
        "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/git/trees/master?recursive=1"

    val gson = Gson()
    val requests = PendingHttpRequests()
    val itemStacks = ConcurrentHashMap<String, ItemStack>()
    val itemNames = ConcurrentHashMap<String, String>()
    val skinStacks = ConcurrentHashMap<String, ItemStack>()
    val animatedSkinMatches = ConcurrentHashMap<String, AnimatedSkinJson>()
    val missingAnimatedSkinMatches = ConcurrentHashMap.newKeySet<String>()
    private val animationCacheLock = Any()
    private val animatedSkinFrames = HashMap<PetAnimationFramesKey, List<PetItemFrame>>()

    @Volatile
    var localRepoCacheLoaded = false

    @Volatile
    var localItemsByInternalName: Map<String, SkyBlockItemJson> = emptyMap()

    @Volatile
    var localItemNameResolution: Map<String, String> = emptyMap()

    @Volatile
    var localPets: Map<String, SkyBlockPetInfo> = emptyMap()

    @Volatile
    var petsJson: SkysoftPetsRepoJson? = null

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

    @Volatile
    var petSkinInternalNames: Set<String>? = null

    @Volatile
    var itemInternalNames: Set<String>? = null

    fun animatedSkinFrames(
        key: () -> PetAnimationFramesKey,
        create: (PetAnimationFramesKey) -> List<PetItemFrame>?,
    ): List<PetItemFrame>? = synchronized(animationCacheLock) {
        val resolvedKey = key()
        animatedSkinFrames[resolvedKey] ?: create(resolvedKey)?.also {
            animatedSkinFrames[resolvedKey] = it
        }
    }

    private fun clearAnimationCaches() {
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
