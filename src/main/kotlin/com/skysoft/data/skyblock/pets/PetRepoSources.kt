package com.skysoft.data.skyblock.pets

import com.google.gson.reflect.TypeToken
import com.skysoft.SkysoftMod
import com.skysoft.data.skyblock.NeuItemJson
import com.skysoft.data.skyblock.SkyBlockItemJson
import com.skysoft.data.skyblock.SkyBlockItemStacks
import com.skysoft.data.skyblock.SkyBlockCatalogCacheFiles
import com.skysoft.data.skyblock.SkyBlockPetInfo
import com.skysoft.data.skyblock.SkyBlockStackFactory
import com.skysoft.data.skyblock.setSkyBlockId
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.net.AsyncRequestSlot
import com.skysoft.utils.net.KeyedAsyncRequestSlots
import com.skysoft.utils.net.RefreshSchedule
import com.skysoft.utils.net.isCancellationFailure
import com.skysoft.utils.TextUtilities.removeColor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal object PetRepoConstants {
    fun load() {
        val stream = requireNotNull(javaClass.getResourceAsStream(PET_CONSTANTS_RESOURCE)) {
            "Missing bundled pet constants"
        }
        PetRepoCache.petsJson = stream.bufferedReader().use { reader ->
            PetRepoCache.gson.fromJson(reader, SkysoftPetsRepoJson::class.java)
        }
    }

    private const val PET_CONSTANTS_RESOURCE = "/assets/skysoft/data/pet_constants.json"
}

internal object LocalSkyBlockCatalog {
    private val requestSlot = AsyncRequestSlot<LocalRepoSnapshot>()
    private val failureSchedule = RefreshSchedule()

    fun load() {
        if (PetRepoCache.localRepoCacheLoaded || !failureSchedule.isDue(System.currentTimeMillis())) return
        requestSlot.startIfIdle(
            requestFactory = {
                CompletableFuture.supplyAsync {
                    val items = readLocalItems(SkyBlockCatalogCacheFiles.resolve("items.min.json"))
                    val itemNameResolution = buildItemNameResolution(items)
                    val pets = readLocalPets(SkyBlockCatalogCacheFiles.resolve("pets.min.json"))
                    LocalRepoSnapshot(items, itemNameResolution, pets)
                }
            },
        ) { snapshot, error ->
            SkysoftErrorBoundary.run("Local Pet Repository async completion") {
                if (error != null || snapshot == null) {
                    SkysoftMod.LOGGER.warn("Failed to load local SkyBlock repo cache", error)
                    failureSchedule.schedule(System.currentTimeMillis(), LOCAL_REPO_CACHE_RETRY_DELAY_MILLIS)
                } else {
                    PetRepoCache.localItemsByInternalName = snapshot.items
                    PetRepoCache.localItemNameResolution = snapshot.itemNameResolution
                    PetRepoCache.localPets = snapshot.pets
                    PetRepoCache.localRepoCacheLoaded = true
                    failureSchedule.reset()
                }
            }
        }
    }

    fun cancelPending() {
        requestSlot.cancel()
    }

    fun itemStackOrNull(internalName: String): ItemStack? =
        PetRepoCache.localItemsByInternalName[internalName]?.let(SkyBlockItemStacks::fromLocalItem)
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

    private fun readLocalItems(path: Path): Map<String, SkyBlockItemJson> {
        if (!Files.isRegularFile(path)) return emptyMap()
        return Files.newBufferedReader(path).use { reader ->
            PetRepoCache.gson.fromJson(reader, Array<SkyBlockItemJson>::class.java)
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

    private fun buildItemNameResolution(items: Map<String, SkyBlockItemJson>): Map<String, String> {
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
        val items: Map<String, SkyBlockItemJson>,
        val itemNameResolution: Map<String, String>,
        val pets: Map<String, SkyBlockPetInfo>,
    )

    private const val LOCAL_REPO_CACHE_RETRY_DELAY_MILLIS = 30_000L
}

internal object RemoteSkyBlockCatalog {
    private val itemRequests = KeyedAsyncRequestSlots<String, NeuItemJson>()
    private val itemIndexRequest = AsyncRequestSlot<GithubTreeJson>()

    fun requestItem(internalName: String) {
        val encoded = internalName.replace(";", "%3B")
        itemRequests.startIfIdle(
            internalName,
            requestFactory = {
                request("${PetRepoCache.RAW_BASE}/items/$encoded.json")
                    .thenApply { PetRepoCache.gson.fromJson(it, NeuItemJson::class.java) }
            },
        ) { item, error ->
            SkysoftErrorBoundary.run("Pet Repository item async completion") {
                if (error == null && item != null) {
                    PetRepoCache.itemNames[internalName] = item.displayName ?: internalName
                    PetRepoCache.itemStacks[internalName] = SkyBlockItemStacks.fromNeuItem(item)
                } else if (error?.isCancellationFailure() != true) {
                    SkysoftMod.LOGGER.warn("Failed to request SkyBlock repo item $internalName", error)
                }
            }
        }
    }

    fun loadItemIndexes() {
        itemIndexRequest.startIfIdle(
            requestFactory = {
                request(PetRepoCache.GITHUB_TREE_URL)
                    .thenApply { PetRepoCache.gson.fromJson(it, GithubTreeJson::class.java) }
            },
        ) { tree, error ->
            SkysoftErrorBoundary.run("Pet Repository index async completion") {
                if (error == null && tree != null) {
                    PetRepoCache.petSkinInternalNames = tree.tree.mapNotNull { it.petSkinInternalNameOrNull() }.toSet()
                    PetRepoCache.itemInternalNames = tree.tree.mapNotNull { it.itemInternalNameOrNull() }.toSet()
                } else if (error?.isCancellationFailure() != true) {
                    SkysoftMod.LOGGER.warn("Failed to load SkyBlock item indexes", error)
                }
            }
        }
    }

    fun cancelPending() {
        itemRequests.cancelAll()
        itemIndexRequest.cancel()
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
