package com.skysoft.data.skyblock.pets

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockStackFactory
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

object PetRepository {
    fun getSkinStackOrNull(
        skinInternalName: String?,
        displayIconTexture: String? = null,
    ): ItemStack? {
        if (skinInternalName == null) return null
        PetSkins.load()
        displayIconTexture?.let { texture ->
            return SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin"))
        }
        PetSkins.animatedTexture(skinInternalName)?.let { texture ->
            return PetRepoCache.skinStack(texture)
        }
        return SkyBlockDataRepository.stack(SkyBlockDataRepository.itemKey(skinInternalName))
    }

    fun getAnimatedSkinFrames(
        skinInternalName: String?,
        firstFrameOnly: Boolean = false,
        animationSpeed: Float = 1f,
        displayIconTexture: String? = null,
    ): List<PetItemFrame>? {
        if (skinInternalName == null) return null
        PetSkins.load()
        val effectiveSpeed = animationSpeed.takeIf { !firstFrameOnly && it > 0f } ?: 0f
        return PetRepoCache.animatedSkinFrames(
            key = {
                val animation = PetSkins.animated(skinInternalName, displayIconTexture)
                PetAnimationFramesKey(
                    animation = animation,
                    staticSkinInternalName = skinInternalName.takeIf { animation == null },
                    staticDisplayIconTexture = displayIconTexture.takeIf { animation == null },
                    firstFrameOnly = firstFrameOnly.takeIf { animation != null } ?: true,
                    animationSpeed = effectiveSpeed.takeIf { animation != null } ?: 0f,
                )
            },
            create = { key ->
                val animation = key.animation
                if (animation == null) {
                    getSkinStackOrNull(
                        key.staticSkinInternalName,
                        key.staticDisplayIconTexture,
                    )?.let { listOf(PetItemFrame(it)) }
                } else {
                    animation.textures
                        .take(if (key.firstFrameOnly) 1 else animation.textures.size)
                        .mapIndexed { index, texture ->
                            val sourceTicks = animation.ticksPerTexture.getOrNull(index) ?: animation.ticks
                            val ticks = if (key.animationSpeed == 0f) {
                                1
                            } else {
                                (sourceTicks / key.animationSpeed).roundToInt().coerceAtLeast(1)
                            }
                            PetItemFrame(
                                SkyBlockStackFactory.texturedHead(texture, Component.literal("Pet Skin")),
                                ticks,
                            )
                        }
                }
            },
        )
    }

    fun getDisplayName(properPetName: String): String =
        PetRepoConstants.data.displayNameMap[properPetName] ?: properPetName.split('_').joinToString(" ") {
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
        return PetRepoConstants.data.customPetLeveling[properName]?.maxLevel ?: DEFAULT_MAX_PET_LEVEL
    }

    fun getPetType(petInternalName: String): String? {
        val properName = PetInternalNames.properName(petInternalName) ?: return null
        return PetRepoConstants.data.petTypes[properName]
    }

    private const val DEFAULT_MAX_PET_LEVEL = 100

    fun getPetXpMultiplier(petInternalName: String): Double {
        val properName = PetInternalNames.properName(petInternalName) ?: return 1.0
        return PetRepoConstants.data.customPetLeveling[properName]?.xpMultiplier ?: 1.0
    }

    fun levelToXp(level: Int, petInternalName: String): Double? {
        val rarityOffset = PetLevels.rarityOffset(petInternalName) ?: return null
        if (level < 0 || level > getMaxLevel(petInternalName)) return null
        if (level <= 1) return 0.0
        val levelTree = PetLevels.fullTree(petInternalName)
        val levelsToSum = level - 1
        val endIndex = rarityOffset + levelsToSum
        if (endIndex > levelTree.size) return null
        var totalXp = 0.0
        for (index in rarityOffset until endIndex) {
            totalXp += levelTree[index]
        }
        return totalXp
    }

    fun xpToLevel(totalXp: Double, petInternalName: String, coerceToMax: Boolean = true): Int {
        var xp = totalXp.takeIf { it > 0 } ?: return 1
        val rarityOffset = PetLevels.rarityOffset(petInternalName) ?: return 1
        var level = 1
        val levelTree = PetLevels.fullTree(petInternalName)
        for (index in rarityOffset until levelTree.size) {
            val xpReq = levelTree[index]
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
