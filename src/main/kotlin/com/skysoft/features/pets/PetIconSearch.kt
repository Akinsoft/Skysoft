package com.skysoft.features.pets

import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.utils.TextUtilities.removeColor
import net.minecraft.world.item.ItemStack
import java.util.Locale

internal object PetIconSearch {
    fun search(query: String, limit: Int): List<ItemIconCandidate> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        if (!PetRepoCache.localRepoCacheLoaded) LocalSkyBlockCatalog.load()
        return sequence {
            yieldLocalItems(words)
            yieldLocalPets(words)
            yieldRemoteItems(words, limit)
        }
            .distinctBy { it.internalName }
            .sortedBy { it.displayName.removeColor().lowercase(Locale.ROOT) }
            .take(limit)
            .mapNotNull { it.toCandidate() }
            .toList()
    }

    private fun words(query: String): List<String> =
        query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun matches(internalName: String, displayName: String, words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val searchable = buildString {
            append(internalName).append(' ')
            append(internalName.replace('_', ' ')).append(' ')
            append(displayName.removeColor())
        }.lowercase(Locale.ROOT)
        return words.all { it in searchable }
    }

    private suspend fun SequenceScope<ItemIconMatch>.yieldLocalItems(words: List<String>) {
        PetRepoCache.localItemsByInternalName.forEach { (internalName, item) ->
            val displayName = item.displayName ?: internalName
            if (matches(internalName, displayName, words)) {
                yield(ItemIconMatch(internalName, displayName) { PetItemStacks.fromLocalItem(item) })
            }
        }
    }

    private suspend fun SequenceScope<ItemIconMatch>.yieldLocalPets(words: List<String>) {
        PetRepoCache.localPets.forEach { (properName, pet) ->
            pet.tiers.forEach { (rarityName, _) ->
                val rarity = SkyBlockRarity.getByName(rarityName) ?: return@forEach
                val internalName = PetRepository.petWithRarityToInternalName(properName, rarity)
                val petName = pet.name.takeIf { it.isNotBlank() }
                    ?: PetRepository.getDisplayName(properName)
                val displayName = "${rarity.chatColorCode}$petName"
                if (matches(internalName, displayName, words)) {
                    yield(ItemIconMatch(internalName, displayName) { LocalSkyBlockCatalog.petStackOrNull(internalName) })
                }
            }
        }
    }

    private suspend fun SequenceScope<ItemIconMatch>.yieldRemoteItems(words: List<String>, limit: Int) {
        val remoteNames = PetRepoCache.itemInternalNames ?: run {
            RemoteSkyBlockCatalog.loadItemIndexes()
            emptySet()
        }
        remoteNames.asSequence()
            .filterNot { it in PetRepoCache.localItemsByInternalName }
            .filter { matches(it, PetRepoCache.itemNames[it] ?: it, words) }
            .take(limit)
            .forEach { internalName ->
                val displayName = PetRepository.itemName(internalName) ?: internalName
                yield(
                    ItemIconMatch(internalName, displayName) {
                        PetRepository.itemStackOrNull(internalName)
                            ?: PetItemStacks.placeholder(internalName, displayName)
                    },
                )
            }
    }

    private data class ItemIconMatch(
        val internalName: String,
        val displayName: String,
        val stack: () -> ItemStack?,
    ) {
        fun toCandidate(): ItemIconCandidate? =
            stack()?.let { ItemIconCandidate(internalName, displayName, it) }
    }
}
