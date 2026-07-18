package com.skysoft.features.pets

import com.google.gson.reflect.TypeToken
import com.skysoft.SkysoftMod
import com.skysoft.data.skyblock.SkyBlockRepoCacheFiles
import com.skysoft.data.skyblock.SkyBlockPetInfo
import com.skysoft.data.skyblock.SkyBlockStackFactory
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.removeColor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

private val LOCAL_REPO_CACHE_RETRY_DELAY = 30.seconds

internal object PetRepoConstants {
    fun load() {
        if (PetRepoCache.constantsLastFailure.passedSince() < PET_CONSTANTS_RETRY_DELAY) return
        if (!PetRepoCache.loadingConstants.compareAndSet(false, true)) return
        RemoteSkyBlockRepo.request(PetRepoCache.PETS_URL).thenApply {
            PetRepoCache.gson.fromJson(it, SkysoftPetsRepoJson::class.java)
        }.whenComplete { pets, error ->
            SkysoftErrorBoundary.run("Pet Repository constants async completion") {
                try {
                    if (error == null) {
                        PetRepoCache.petsJson = pets
                    } else {
                        PetRepoCache.constantsLastFailure = ElapsedTimeMark.now()
                        SkysoftMod.LOGGER.warn("Failed to load pet data", error)
                    }
                } finally {
                    PetRepoCache.loadingConstants.set(false)
                }
            }
        }
    }
}

private val PET_CONSTANTS_RETRY_DELAY = 30.seconds

internal object LocalSkyBlockRepo {
    fun load() {
        if (PetRepoCache.localRepoCacheLoaded) return
        if (PetRepoCache.localRepoCacheLastFailure.passedSince() < LOCAL_REPO_CACHE_RETRY_DELAY) return
        if (!PetRepoCache.loadingLocalRepoCache.compareAndSet(false, true)) return
        val request = CompletableFuture.supplyAsync {
            val items = readLocalItems(SkyBlockRepoCacheFiles.resolve("items.min.json"))
            val itemNameResolution = buildItemNameResolution(items)
            val pets = readLocalPets(SkyBlockRepoCacheFiles.resolve("pets.min.json"))
            LocalRepoSnapshot(items, itemNameResolution, pets)
        }
        PetRepoCache.localRepoLoadFuture = request
        request.whenComplete { snapshot, error ->
            SkysoftErrorBoundary.run("Local Pet Repository async completion") {
                if (PetRepoCache.localRepoLoadFuture !== request) return@run
                PetRepoCache.localRepoLoadFuture = null
                try {
                    if (error != null) {
                        SkysoftMod.LOGGER.warn("Failed to load local SkyBlock repo cache", error)
                        PetRepoCache.localRepoCacheLastFailure = ElapsedTimeMark.now()
                    } else {
                        PetRepoCache.localItemsByInternalName = snapshot.items
                        PetRepoCache.localItemNameResolution = snapshot.itemNameResolution
                        PetRepoCache.localPets = snapshot.pets
                        PetRepoCache.localRepoCacheLoaded = true
                    }
                } finally {
                    PetRepoCache.loadingLocalRepoCache.set(false)
                }
            }
        }
    }

    fun itemStackOrNull(internalName: String): ItemStack? =
        PetRepoCache.localItemsByInternalName[internalName]?.let(PetItemStacks::fromLocalItem)
            ?: petStackOrNull(internalName)

    fun petStackOrNull(internalName: String): ItemStack? {
        val (properName, rarity) = PetInternalNames.split(internalName) ?: return null
        val pet = PetRepoCache.localPets[properName] ?: return null
        val tier = pet.tiers[rarity.name] ?: return null
        val displayName = pet.name.takeIf { it.isNotBlank() } ?: PetRepository.getDisplayName(properName)
        val stack = SkyBlockStackFactory.texturedHead(
            tier.texture,
            Component.literal("§7[Lvl {LVL}] ${rarity.chatColorCode}$displayName"),
        )
        stack.setSkyBlockId(internalName)
        return stack
    }

    fun itemNameOrNull(internalName: String): String? =
        PetRepoCache.localItemsByInternalName[internalName]?.displayName
            ?: localPetNameOrNull(internalName)

    fun resolveItemByDisplayNameOrNull(itemName: String): String? =
        PetRepoCache.localItemNameResolution[itemName]
            ?: PetRepoCache.localItemNameResolution[itemName.removeColor()]
            ?: PetRepoCache.localItemNameResolution[itemName.removeColor().lowercase()]

    private fun readLocalItems(path: Path): Map<String, SkyblockRepoItemJson> {
        if (!Files.isRegularFile(path)) return emptyMap()
        return Files.newBufferedReader(path).use { reader ->
            PetRepoCache.gson.fromJson(reader, Array<SkyblockRepoItemJson>::class.java)
                .mapNotNull { item -> item.internalName?.let { it to item } }
                .toMap()
        }
    }

    private fun readLocalPets(path: Path): Map<String, SkyBlockPetInfo> {
        if (!Files.isRegularFile(path)) return emptyMap()
        return Files.newBufferedReader(path).use { reader ->
            PetRepoCache.gson.fromJson<Map<String, SkyBlockPetInfo>>(reader, localPetsMapType).orEmpty()
        }
    }

    private fun buildItemNameResolution(items: Map<String, SkyblockRepoItemJson>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        items.forEach { (internalName, item) ->
            val displayName = item.displayName ?: return@forEach
            result.putIfAbsent(displayName, internalName)
            result.putIfAbsent(displayName.removeColor(), internalName)
            result.putIfAbsent(displayName.removeColor().lowercase(), internalName)
        }
        return result
    }

    private fun localPetNameOrNull(internalName: String): String? {
        val (properName, rarity) = PetInternalNames.split(internalName) ?: return null
        val pet = PetRepoCache.localPets[properName] ?: return null
        val displayName = pet.name.takeIf { it.isNotBlank() } ?: PetRepository.getDisplayName(properName)
        return "${rarity.chatColorCode}$displayName"
    }

    private val localPetsMapType = object : TypeToken<Map<String, SkyBlockPetInfo>>() {}.type

    private data class LocalRepoSnapshot(
        val items: Map<String, SkyblockRepoItemJson>,
        val itemNameResolution: Map<String, String>,
        val pets: Map<String, SkyBlockPetInfo>,
    )
}

internal object RemoteSkyBlockRepo {
    fun requestItem(internalName: String) {
        if (!PetRepoCache.requestedItems.add(internalName)) return
        val encoded = internalName.replace(";", "%3B")
        request("${PetRepoCache.RAW_BASE}/items/$encoded.json")
            .thenApply { PetRepoCache.gson.fromJson(it, SkysoftNeuItemJson::class.java) }
            .whenComplete { item, error ->
                SkysoftErrorBoundary.run("Pet Repository item async completion") {
                    if (error == null && item != null) {
                        PetRepoCache.itemNames[internalName] = item.displayName ?: internalName
                        PetRepoCache.itemStacks[internalName] = PetItemStacks.fromNeuItem(item)
                    } else {
                        PetRepoCache.requestedItems.remove(internalName)
                        SkysoftMod.LOGGER.warn("Failed to request SkyBlock repo item $internalName", error)
                    }
                }
            }
    }

    fun loadItemIndexes() {
        if (!PetRepoCache.loadingItemIndexes.compareAndSet(false, true)) return
        request(PetRepoCache.GITHUB_TREE_URL)
            .thenApply { PetRepoCache.gson.fromJson(it, GithubTreeJson::class.java) }
            .whenComplete { tree, error ->
                SkysoftErrorBoundary.run("Pet Repository index async completion") {
                    try {
                        if (error == null && tree != null) {
                            PetRepoCache.petSkinInternalNames =
                                tree.tree.mapNotNull { it.petSkinInternalNameOrNull() }.toSet()
                            PetRepoCache.itemInternalNames =
                                tree.tree.mapNotNull { it.itemInternalNameOrNull() }.toSet()
                        } else {
                            SkysoftMod.LOGGER.warn("Failed to load SkyBlock item indexes", error)
                        }
                    } finally {
                        PetRepoCache.loadingItemIndexes.set(false)
                    }
                }
            }
    }

    fun request(url: String): CompletableFuture<String> = PetRepoCache.requests.getString(url)

    private fun GithubTreeEntry.petSkinInternalNameOrNull(): String? =
        path.takeIf { type == "blob" && it.startsWith("items/PET_SKIN_") && it.endsWith(".json") }
            ?.removePrefix("items/")
            ?.removeSuffix(".json")

    private fun GithubTreeEntry.itemInternalNameOrNull(): String? =
        path.takeIf { type == "blob" && it.startsWith("items/") && it.endsWith(".json") }
            ?.removePrefix("items/")
            ?.removeSuffix(".json")
}
