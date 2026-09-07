package com.skysoft.features.pets

import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.data.skyblock.SkyBlockItemNames
import com.skysoft.features.pets.ActivePetTracker.PetDataAssertionSource
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.RegexUtilities.groupOrNull
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.formattedText
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.TextUtilities.removeResets
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.chat.hoverTextComponents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

internal object PetStorageChat {
    fun handleIncomingMessage(component: Component): ChatMessageVisibility {
        val message = component.formattedText()
        ActivePetTracker.handleChat(message)
        PetXpEstimator.handleChat(message)
        handlePetMenuAddChat(message)
        handleHeldItemChat(message)
        val handledAutopetMessage = tryHandleAutopetChat(message, component)
        return when {
            handledAutopetMessage && PetStorageService.config.hideAutopet -> ChatMessageVisibility.HIDE
            else -> ChatMessageVisibility.SHOW
        }
    }

    private fun handlePetMenuAddChat(message: String) {
        val match = petMenuAddSuccessPattern.matchEntire(message.cleanSkyBlockText()) ?: return
        PetStorageHeldPetAdd.confirmAdded(match.group("pet").trim())
    }

    private fun handleHeldItemChat(message: String) {
        val match = petItemHeldMessagePattern.matchEntire(message) ?: return
        val itemName = match.group("item").cleanSkyBlockText()
        val petHeldItem = resolveAppliedPetItemOrNull(itemName) ?: return
        updateCurrentPetHeldItem(petHeldItem)
    }

    private fun tryHandleAutopetChat(message: String, component: Component): Boolean {
        val match = autoPetMessagePattern.matchEntire(message) ?: return false
        val petName = match.group("pet")
        val level = match.group("level").formatInt()
        val rarity = PetStoragePetItems.rarityOrNull(match) ?: return true
        val petInternalName = PetRepository.petWithRarityToInternalName(petName, rarity)
        val petSkin = PetStoragePetItems.petSkinInternalNameOrNull(match, petInternalName)
        val skinTag = (match.groupOrNull("skin") ?: match.groupOrNull("altskin"))?.replace(" ", "")
        val hoverInfo = hoverTextLines(component)
        val petHeldItemName = hoverInfo.firstNotNullOfOrNull { line ->
            autoPetHoverHeldItemPattern.matchEntire(line.removeResets())?.group("item")
        }?.trim()
        val petHeldItem = petHeldItemName?.let(SkyBlockItemNames::resolveItemId)
        val resolvedPet = PetStorageService.resolvePetDataOrNull(
            name = petName,
            rarity = rarity,
            heldItem = petHeldItem,
            heldItemName = petHeldItemName,
            heldItemKnown = hoverInfo.isNotEmpty(),
            skinTag = skinTag,
            skinTagKnown = skinTag != null,
            level = level,
        ) ?: return true

        val equippedPet = resolvedPet.copy(
            skinInternalName = petSkin ?: resolvedPet.skinInternalName,
            heldItemInternalName = when {
                petHeldItem != null -> petHeldItem
                hoverInfo.isNotEmpty() && petHeldItemName == null -> null
                else -> resolvedPet.heldItemInternalName
            },
        )
        val minimumExp = PetRepository.levelToXp(level, equippedPet.fauxInternalName)
        val updatedPet = if (minimumExp != null && (equippedPet.exp ?: 0.0) < minimumExp) {
            equippedPet.copy(exp = minimumExp)
        } else {
            equippedPet
        }
        val previousPet = ActivePetTracker.currentPet
        ActivePetTracker.assertFoundCurrentData(updatedPet, PetDataAssertionSource.AUTOPET)
        PetXpEstimator.recordAutopetSwap(updatedPet, previousPet, autopetTriggerOrNull(hoverInfo))
        return true
    }

    private fun resolveAppliedPetItemOrNull(itemName: String): String? {
        val heldItemName = Minecraft.getInstance().player?.mainHandItem?.hoverName?.formattedText()
            ?.removeResets()
            ?.trim()
        if (heldItemName?.removeColor() == itemName.removeColor()) {
            SkyBlockItemNames.resolveItemId(heldItemName)?.let { return it }
        }
        return SkyBlockItemNames.resolveItemId(itemName)
    }

    private fun updateCurrentPetHeldItem(heldItem: String) {
        val currentPet = ActivePetTracker.currentPet ?: return
        if (currentPet.heldItemInternalName == heldItem) return
        ActivePetTracker.assertFoundCurrentData(currentPet.copy(heldItemInternalName = heldItem), PetDataAssertionSource.CHAT)
    }

    private fun hoverTextLines(component: Component): List<String> =
        component.hoverTextComponents().flatMap { hover -> hover.formattedText().split("\n") }

    private fun autopetTriggerOrNull(lines: List<String>): String? =
        lines.map { it.cleanSkyBlockText() }
            .zipWithNext()
            .firstOrNull { (line, trigger) -> line == "When:" && trigger.isNotBlank() }
            ?.second
}
