package com.skysoft.data

import com.google.gson.annotations.Expose
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockStackFactory
import com.skysoft.features.pets.PetInternalNames
import com.skysoft.features.pets.PetItemFrame
import com.skysoft.features.pets.PetRepository
import com.skysoft.features.pets.isDragonEggStagePet
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class StoredPetData(
    @field:Expose val petInternalName: String,
    @field:Expose var skinInternalName: String? = null,
    @field:Expose var heldItemInternalName: String? = null,
    @field:Expose var exp: Double? = null,
    @field:Expose val uuid: UUID? = null,
    @field:Expose var displayIconTexture: String? = null,
    @Transient var exactItemStack: ItemStack? = null,
) {
    val hasPetInternalName: Boolean get() = rawPetInternalName?.isNotBlank() == true

    private val rawPetInternalName: String? get() = petInternalName
    private val requiredPetInternalName: String get() = rawPetInternalName ?: error("StoredPetData is missing petInternalName")
    private val tierBoosted get() = heldItemInternalName == TIER_BOOST && PetRepository.hasValidHigherTier(requiredPetInternalName)
    private val properPetName get() =
        PetInternalNames.properName(requiredPetInternalName) ?: requiredPetInternalName.substringBefore(';')
    private val specifiedRarity get() = PetInternalNames.rarity(requiredPetInternalName) ?: SkyBlockRarity.COMMON

    val fauxInternalName: String get() = "$properPetName;${rarity.id}"
    val cleanName: String get() = PetRepository.getCleanPetName(fauxInternalName, colored = false)
    val displayName: String get() = if (isDragonEggStage) "$cleanName Egg" else cleanName
    val coloredName: String get() = "${rarity.chatColorCode}$cleanName"
    val coloredDisplayName: String get() = "${rarity.chatColorCode}$displayName"
    val level: Int get() = PetRepository.xpToLevel(exp ?: 0.0, fauxInternalName)
    val skinTag: String? get() = PetRepository.skinColorCodeOrNull(skinInternalName)?.let { "$it✦" }
    val rarity: SkyBlockRarity get() = if (tierBoosted) specifiedRarity.oneAbove() ?: specifiedRarity else specifiedRarity
    private val shouldUseDisplayIconTexture: Boolean get() = displayIconTexture != null && isDragonEggStage
    val isDragonEggStage: Boolean get() = isDragonEggStagePet(fauxInternalName, exp)

    val currentLevelXp: Double get() = levelXpBoundary(level)
    val nextLevelXp: Double get() = levelXpBoundary(level + 1)
    val levelProgressionPercentage: Double
        get() {
            val total = exp?.takeIf { it > 0.0 } ?: return 0.0
            if (level >= PetRepository.getMaxLevel(fauxInternalName)) return 100.0
            val start = levelXpBoundary(level)
            val span = levelXpBoundary(level + 1) - start
            return if (span <= 0.0) 0.0 else ((total - start) * 100.0 / span).coerceIn(0.0, 100.0)
        }

    private fun levelXpBoundary(targetLevel: Int): Double =
        PetRepository.levelToXp(targetLevel, fauxInternalName) ?: 0.0

    val overflowXp: Double
        get() {
            val maxLevel = PetRepository.getMaxLevel(fauxInternalName)
            return petOverflowExperience(exp, level, maxLevel, PetRepository.levelToXp(maxLevel, fauxInternalName))
        }

    fun getUserFriendlyName(
        includeLevel: Boolean = true,
        includeSkinTag: Boolean = true,
    ): String = petDisplayLabel(level, coloredDisplayName, skinTag, includeLevel, includeSkinTag)

    fun matchesDisplayName(name: String): Boolean =
        name.trim().let { trimmedName ->
            cleanName.equals(trimmedName, ignoreCase = true) ||
                displayName.equals(trimmedName, ignoreCase = true)
        }

    fun getItemStackOrNull(): ItemStack? =
        PetRepository.getSkinStackOrNull(skinInternalName, displayIconTexture)
            ?: displayIconStackOrNull()
            ?: exactItemStack?.copy()
            ?: PetRepository.itemStackOrNull(requiredPetInternalName)

    fun getAnimatedItemStackSequence(firstFrameOnly: Boolean = false, animationSpeed: Float = 1f): List<PetItemFrame>? =
        PetRepository.getAnimatedSkinFrames(
            skinInternalName,
            firstFrameOnly,
            animationSpeed,
            displayIconTexture,
        )
            ?: getItemStackOrNull()?.let { listOf(PetItemFrame(it)) }

    private fun displayIconStackOrNull(): ItemStack? {
        val texture = displayIconTexture?.takeIf { shouldUseDisplayIconTexture } ?: return null
        return SkyBlockStackFactory.texturedHead(texture, Component.literal(getUserFriendlyName()))
    }

    companion object {
        private const val TIER_BOOST = "PET_ITEM_TIER_BOOST"
    }
}

internal fun petOverflowExperience(
    totalExperience: Double?,
    currentLevel: Int,
    maxLevel: Int,
    maxLevelExperience: Double?,
): Double {
    if (currentLevel < maxLevel) return 0.0
    val total = totalExperience?.takeIf { it.isFinite() } ?: return 0.0
    val threshold = maxLevelExperience?.takeIf { it.isFinite() && it >= 0.0 } ?: return 0.0
    return (total - threshold).coerceAtLeast(0.0)
}

internal fun petDisplayLabel(
    level: Int,
    coloredDisplayName: String,
    skinTag: String?,
    includeLevel: Boolean,
    includeSkinTag: Boolean,
): String {
    val segments = mutableListOf<String>()
    if (includeLevel) segments += "\u00A77[Lvl $level]"
    segments += coloredDisplayName
    if (includeSkinTag && skinTag != null) segments += skinTag
    return segments.joinToString(" ")
}
