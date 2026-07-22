package com.skysoft.features.pets

import com.google.gson.Gson
import com.skysoft.data.skyblock.SkyBlockItemJson
import com.skysoft.data.skyblock.SkyBlockPetInfo
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.net.PendingHttpRequests
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object PetRepoCache {
    const val RAW_BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master"
    const val PETS_URL = "$RAW_BASE/constants/pets.json"
    const val GITHUB_TREE_URL =
        "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/git/trees/master?recursive=1"

    val gson = Gson()
    val requests = PendingHttpRequests()
    val loadingLocalRepoCache = AtomicBoolean(false)
    var localRepoLoadFuture: CompletableFuture<*>? = null
    val loadingConstants = AtomicBoolean(false)
    val requestedItems = ConcurrentHashMap.newKeySet<String>()
    val loadingItemIndexes = AtomicBoolean(false)
    val itemStacks = ConcurrentHashMap<String, ItemStack>()
    val itemNames = ConcurrentHashMap<String, String>()
    val skinStacks = ConcurrentHashMap<String, ItemStack>()
    val animatedSkinMatches = ConcurrentHashMap<String, AnimatedSkinJson>()
    val missingAnimatedSkinMatches = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var localRepoCacheLoaded = false

    @Volatile
    var localRepoCacheLastFailure = ElapsedTimeMark.farPast()

    @Volatile
    var constantsLastFailure = ElapsedTimeMark.farPast()

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
            animatedSkinMatches.clear()
            missingAnimatedSkinMatches.clear()
            field = value
        }

    @Volatile
    var learnedPetAnimations: PetAnimationsJson = PetAnimationsJson()
        set(value) {
            animatedSkinMatches.clear()
            missingAnimatedSkinMatches.clear()
            field = value
        }

    @Volatile
    var petSkinInternalNames: Set<String>? = null

    @Volatile
    var itemInternalNames: Set<String>? = null
}
