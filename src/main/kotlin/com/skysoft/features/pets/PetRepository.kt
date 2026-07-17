package com.skysoft.features.pets

import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataLoadState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockStackFactory
import com.skysoft.utils.TextUtilities.removeColor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

object PetRepository {
    fun register() {
        PetSkins.load()
        LocalSkyBlockRepo.load()
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!PetRepoCache.localRepoCacheLoaded) {
                LocalSkyBlockRepo.load()
            }
            if (PetRepoCache.petsJson == null) {
                PetRepoConstants.load()
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            PetRepoCache.requests.cancelAll()
        }
    }

    fun itemStackOrNull(internalName: String?): ItemStack? {
        if (internalName == null) return null
        SkyBlockDataRepository.stack(SkyBlockDataRepository.itemKey(internalName))?.let { return it }
        PetRepoCache.itemStacks[internalName]?.let { return it.copy() }
        LocalSkyBlockRepo.itemStackOrNull(internalName)?.let { stack ->
            PetRepoCache.itemStacks[internalName] = stack
            return stack.copy()
        }
        RemoteSkyBlockRepo.requestItem(internalName)
        return null
    }

    fun itemName(internalName: String?): String? {
        if (internalName == null) return null
        SkyBlockDataRepository.entry(SkyBlockDataRepository.itemKey(internalName))?.let { return it.formattedDisplayName }
        PetRepoCache.itemNames[internalName]?.let { return it }
        LocalSkyBlockRepo.itemNameOrNull(internalName)?.let { itemName ->
            PetRepoCache.itemNames[internalName] = itemName
            return itemName
        }
        RemoteSkyBlockRepo.requestItem(internalName)
        return internalName.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    fun searchItemIconCandidates(query: String, limit: Int = 512): List<ItemIconCandidate> {
        if (SkyBlockDataRepository.status.state == SkyBlockDataLoadState.READY) {
            return SkyBlockDataRepository.search(query).asSequence()
                .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
                .take(limit)
                .mapNotNull { entry ->
                    SkyBlockDataRepository.stack(entry.key)?.let { ItemIconCandidate(entry.key.id, entry.displayName, it) }
                }
                .toList()
        }
        return PetIconSearch.search(query, limit)
    }

    fun skinColorCodeOrNull(skinInternalName: String?): String? {
        if (skinInternalName == null) return null
        val itemName = PetRepoCache.itemNames[skinInternalName]
            ?: LocalSkyBlockRepo.itemNameOrNull(skinInternalName)?.also { PetRepoCache.itemNames[skinInternalName] = it }
            ?: run {
                RemoteSkyBlockRepo.requestItem(skinInternalName)
                return null
            }
        return colorCodePattern.find(itemName)?.value
    }

    fun findPetSkinInternalNameOrNull(petInternalName: String, skinMarker: String?): String? {
        val marker = skinMarker?.takeIf { it.contains('✦') } ?: return null
        val properName = PetInternalNames.properName(petInternalName) ?: return null
        val skinInternalNames = PetRepoCache.petSkinInternalNames ?: run {
            RemoteSkyBlockRepo.loadItemIndexes()
            return null
        }
        val candidates = skinInternalNames.filter { PetSkins.isSkinForPet(it, properName) }
        if (candidates.isEmpty()) return null
        candidates.forEach(RemoteSkyBlockRepo::requestItem)
        val colorCode = colorCodePattern.find(marker)?.value
        return candidates.mapNotNull { internalName ->
            PetRepoCache.itemNames[internalName]?.let { displayName -> internalName to displayName }
        }
            .filter { (_, displayName) -> colorCode == null || displayName.startsWith(colorCode) }
            .singleOrNull()
            ?.first
    }

    fun getSkinStackOrNull(
        skinInternalName: String?,
        displayIconTexture: String? = null,
    ): ItemStack? {
        if (skinInternalName == null) return null
        displayIconTexture?.let { texture ->
            return SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin"))
        }
        PetSkins.animatedTexture(skinInternalName)?.let { texture ->
            return PetRepoCache.skinStacks.computeIfAbsent(skinInternalName) {
                SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin"))
            }.copy()
        }
        return itemStackOrNull(skinInternalName)
    }

    fun getAnimatedSkinFrames(
        skinInternalName: String?,
        firstFrameOnly: Boolean = false,
        animationSpeed: Float = 1f,
        displayIconTexture: String? = null,
    ): List<PetItemFrame>? {
        if (skinInternalName == null) return null
        val animation = PetSkins.animated(skinInternalName, displayIconTexture)
            ?: return getSkinStackOrNull(skinInternalName, displayIconTexture)
                ?.let { listOf(PetItemFrame(it)) }
        return animation.textures
            .take(if (firstFrameOnly) 1 else animation.textures.size)
            .mapIndexed { index, texture ->
                val sourceTicks = animation.ticksPerTexture.getOrNull(index) ?: animation.ticks
                val ticks = if (firstFrameOnly || animationSpeed <= 0f) {
                    1
                } else {
                    (sourceTicks / animationSpeed).roundToInt().coerceAtLeast(1)
                }
                PetItemFrame(SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin")), ticks)
            }
    }

    fun resolvePetItemOrNull(itemName: String): String? {
        if (!PetRepoCache.localRepoCacheLoaded) LocalSkyBlockRepo.load()
        val clean = itemName.removeColor()
        val map = PetRepoCache.petsJson?.petItemResolution.orEmpty()
        return map[itemName]
            ?: map[clean]
            ?: map.entries.firstOrNull { (displayName, internalName) ->
                displayName.removeColor() == clean || internalName.replace('_', ' ').equals(clean, ignoreCase = true)
            }?.value
            ?: LocalSkyBlockRepo.resolveItemByDisplayNameOrNull(itemName)
            ?: LocalSkyBlockRepo.resolveItemByDisplayNameOrNull(clean)
    }

    fun getDisplayName(properPetName: String): String =
        PetRepoCache.petsJson?.displayNameMap?.get(properPetName) ?: properPetName.split('_').joinToString(" ") {
            it.lowercase().replaceFirstChar { char -> char.uppercase() }
        }

    fun petWithRarityToInternalName(petName: String, rarity: SkyBlockRarity): String =
        rawPetWithRarityToInternalName(normalizePetDisplayName(petName, rarity), rarity)

    fun getCleanPetName(petInternalName: String, colored: Boolean = true): String {
        val (properPetName, rarity) = PetInternalNames.split(petInternalName) ?: return ""
        return buildString {
            if (colored) append(rarity.chatColorCode)
            append(getDisplayName(properPetName))
        }
    }

    fun getMaxLevel(petInternalName: String): Int {
        val properName = PetInternalNames.properName(petInternalName) ?: return DEFAULT_MAX_PET_LEVEL
        return PetRepoCache.petsJson?.customPetLeveling?.get(properName)?.maxLevel ?: DEFAULT_MAX_PET_LEVEL
    }

    fun getPetType(petInternalName: String): String? {
        val properName = PetInternalNames.properName(petInternalName) ?: return null
        return PetRepoCache.petsJson?.petTypes?.get(properName)
    }

    private const val DEFAULT_MAX_PET_LEVEL = 100

    fun getPetXpMultiplier(petInternalName: String): Double {
        val properName = PetInternalNames.properName(petInternalName) ?: return 1.0
        return PetRepoCache.petsJson?.customPetLeveling?.get(properName)?.xpMultiplier ?: 1.0
    }

    fun levelToXp(level: Int, petInternalName: String): Double? {
        val rarityOffset = PetLevels.rarityOffset(petInternalName) ?: return null
        if (level < 0 || level > getMaxLevel(petInternalName)) return null
        if (level <= 1) return 0.0
        val levelTree = PetLevels.fullTree(petInternalName)
        val levelsToSum = level - 1
        if (rarityOffset + levelsToSum > levelTree.size) return null
        return levelTree.drop(rarityOffset).take(levelsToSum).sumOf { it.toDouble() }
    }

    fun xpToLevel(totalXp: Double, petInternalName: String, coerceToMax: Boolean = true): Int {
        var xp = totalXp.takeIf { it > 0 } ?: return 1
        val rarityOffset = PetLevels.rarityOffset(petInternalName) ?: return 1
        var level = 1
        for (xpReq in PetLevels.fullTree(petInternalName).drop(rarityOffset)) {
            if (xp < xpReq) break
            xp -= xpReq
            level++
        }
        return if (coerceToMax) level.coerceAtMost(getMaxLevel(petInternalName)) else level
    }

    fun hasValidHigherTier(petInternalName: String): Boolean {
        val (properName, rarity) = PetInternalNames.split(petInternalName) ?: return false
        val rarityAbove = rarity.oneAbove() ?: return false
        return levelToXp(1, "$properName;${rarityAbove.id}") != null
    }

    private val colorCodePattern = Regex("""§.""")
}

internal fun isDragonEggStagePet(petInternalName: String, exp: Double?): Boolean {
    if (!isDragonPetWithEggStage(petInternalName)) return false
    val level100Xp = PetRepository.levelToXp(DRAGON_EGG_END_LEVEL, petInternalName) ?: return false
    return (exp ?: 0.0) < level100Xp
}

private fun normalizePetDisplayName(petName: String, rarity: SkyBlockRarity): String {
    val trimmedPetName = petName.trim()
    val basePetName = trimmedPetName.removeSuffix(DRAGON_EGG_DISPLAY_SUFFIX)
    if (basePetName == trimmedPetName) return trimmedPetName

    val candidateInternalName = rawPetWithRarityToInternalName(basePetName, rarity)
    return if (isDragonPetWithEggStage(candidateInternalName)) basePetName else trimmedPetName
}

private fun rawPetWithRarityToInternalName(petName: String, rarity: SkyBlockRarity): String =
    "${PetInternalNames.canonicalName(petName.uppercase().replace(" ", "_"))};${rarity.id}"

private fun isDragonPetWithEggStage(petInternalName: String): Boolean {
    val properPetName = PetInternalNames.properName(petInternalName) ?: return false
    if (!properPetName.endsWith("_DRAGON")) return false
    return PetRepository.getMaxLevel(petInternalName) == DRAGON_PET_MAX_LEVEL
}

private const val DRAGON_EGG_DISPLAY_SUFFIX = " Egg"
private const val DRAGON_EGG_END_LEVEL = 100
private const val DRAGON_PET_MAX_LEVEL = 200
