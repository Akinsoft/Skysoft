package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource
import com.skysoft.utils.NumberUtilities.formatDoubleOrNull
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import com.skysoft.utils.TextUtilities.removeResets
import net.minecraft.network.chat.Component

internal object PetTabWidgetReader {
    fun read() {
        PetWidgetStateTracker.syncLoadingState()
        val widget = petTabWidgetLinesOrNull() ?: return
        val component = widget.pet
        val match = petTabWidgetNamePattern.matchEntire(component.string) ?: run {
            PetWidgetStateTracker.setNotReady()
            return
        }
        val petName = match.group("pet")
        val level = match.group("level").formatInt()
        val rarity = SkyBlockRarity.getByComponent(component, petName) ?: run {
            PetWidgetStateTracker.setNotReady()
            return
        }
        val petInternalName = PetRepository.petWithRarityToInternalName(petName, rarity)
        val petSkin = PetStoragePetItems.petSkinInternalNameOrNull(match, petInternalName)
        val petSkinTag = (match.groupOrNull("skin") ?: match.groupOrNull("altskin"))?.replace(" ", "")
        val skinTagAbsenceKnown = petSkinTag == null
        val petHeldItem = widget.heldItem?.formattedText()
            ?.trim()
            ?.removeResets()
            ?.takeIf { it.isNotBlank() }
            ?.let(SkyBlockItemNames::resolveItemId)

        var isMaxedWithoutOverflowXp = false
        val petExp = petTabWidgetXpPattern.matchEntire(widget.xp.string)?.let { xpMatch ->
            if (xpMatch.groupOrNull("max") != null) {
                isMaxedWithoutOverflowXp = true
                return@let null
            }
            val currentLevelXp = PetRepository.levelToXp(level, petInternalName) ?: return@let null
            val current = xpMatch.groupOrNull("current") ?: return@let null
            val readXpGroup = current.formatDoubleOrNull() ?: return@let null
            PetExpRead(currentLevelXp + readXpGroup, PetStoragePetItems.isExactPetExpText(current))
        }

        val resolvedPet = PetStorageService.resolvePetDataOrNull(
            name = petName,
            rarity = rarity,
            level = level,
            heldItem = petHeldItem,
            skinTag = petSkinTag,
            skinTagKnown = skinTagAbsenceKnown,
            exp = petExp?.value,
        )
        val matchingCurrentPet = ActivePetTracker.currentPet?.takeIf { currentPet ->
            currentPet.matchesDisplayName(petName) &&
                currentPet.rarity == rarity &&
                currentPet.level == level &&
                PetStoragePetItems.matchesSkinTag(currentPet, petSkinTag, skinTagKnown = false)
        }
        val currentPetData = resolvedPet ?: matchingCurrentPet ?: StoredPetData(
            petInternalName = petInternalName,
            skinInternalName = petSkin,
            heldItemInternalName = petHeldItem,
            exp = petExp?.value ?: PetRepository.levelToXp(level, petInternalName) ?: 0.0,
        )
        val previousExp = currentPetData.exp
        val exactPetExp = petExp.exactValue?.let { PetStoragePetItems.reconcileDisplayedExp(currentPetData, it) }
        val appliedExactPetExp = exactPetExp?.takeUnless {
            PetXpEstimator.shouldIgnoreStalePetWidgetRead(
                currentPetData,
                readExp = it,
                previousExp = previousExp,
            )
        }
        val updatedPet = PetStoragePetItems.withKnownData(
            currentPetData,
            exp = appliedExactPetExp,
            skinInternalName = petSkin,
            heldItemInternalName = petHeldItem,
        )
        PetXpEstimator.resyncFromPetDataRead(
            updatedPet,
            exact = appliedExactPetExp != null,
            previousExp = previousExp,
            appliedExp = appliedExactPetExp,
        )
        ActivePetTracker.assertFoundCurrentData(updatedPet, PetDataAssertionSource.TAB)
        when {
            isMaxedWithoutOverflowXp -> PetWidgetStateTracker.setMaxedWithoutOverflowXp()
            exactPetExp != null -> PetWidgetStateTracker.setReady()
            else -> PetWidgetStateTracker.setNotReady()
        }
    }

    private fun petTabWidgetLinesOrNull(): PetTabWidgetLines? {
        if (!TabListApi.isSkyBlockDataLoaded) return null
        val lines = TabListApi.skyBlockLines
        if (isPetTabWidgetEmpty(lines)) {
            ActivePetTracker.assertNoCurrentPetFromTab()
            PetWidgetStateTracker.setReady()
            return null
        }
        return parsePetTabWidget(lines) ?: run {
            PetWidgetStateTracker.setNotReady()
            null
        }
    }

}

internal data class PetTabWidgetLines(
    val pet: Component,
    val heldItem: Component?,
    val xp: Component,
)

internal fun parsePetTabWidget(lines: List<Component>): PetTabWidgetLines? {
    val headerIndex = findPetTabWidgetHeaderIndex(lines)
    if (headerIndex < 0) return null
    val pet = lines.getOrNull(headerIndex + PET_TAB_WIDGET_PET_OFFSET) ?: return null
    if (!petTabWidgetNamePattern.matches(pet.string)) return null
    val next = lines.getOrNull(headerIndex + PET_TAB_WIDGET_SECOND_LINE_OFFSET) ?: return null
    return when {
        petTabWidgetXpPattern.matches(next.string) -> PetTabWidgetLines(pet, null, next)
        next.cleanSkyBlockText().isEmpty() -> null
        else -> lines.getOrNull(headerIndex + PET_TAB_WIDGET_XP_WITH_ITEM_OFFSET)
            ?.takeIf { petTabWidgetXpPattern.matches(it.string) }
            ?.let { PetTabWidgetLines(pet, next, it) }
    }
}

internal fun isPetTabWidgetEmpty(lines: List<Component>): Boolean {
    val headerIndex = findPetTabWidgetHeaderIndex(lines)
    return headerIndex >= 0 && lines.getOrNull(headerIndex + PET_TAB_WIDGET_PET_OFFSET)
        ?.cleanSkyBlockText() == "No pet selected"
}

private fun findPetTabWidgetHeaderIndex(lines: List<Component>): Int =
    lines.indexOfFirst { it.cleanSkyBlockText() == "Pet:" }

private const val PET_TAB_WIDGET_PET_OFFSET = 1
private const val PET_TAB_WIDGET_SECOND_LINE_OFFSET = 2
private const val PET_TAB_WIDGET_XP_WITH_ITEM_OFFSET = 3
