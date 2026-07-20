package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
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
        val petHeldItem = petHeldItemName?.let(PetRepository::resolvePetItemOrNull)
        val resolvedPet = PetStorageService.resolvePetDataOrNull(
            name = petName,
            rarity = rarity,
            heldItem = petHeldItem,
            heldItemKnown = hoverInfo.isNotEmpty(),
            skinTag = skinTag,
            skinTagKnown = skinTag != null,
            level = level,
        ) ?: StoredPetData(
            petInternalName = petInternalName,
            exp = PetRepository.levelToXp(level, petInternalName),
        )

        PetStoragePetItems.applyKnownData(resolvedPet, skinInternalName = petSkin)
        when {
            petHeldItem != null -> resolvedPet.heldItemInternalName = petHeldItem
            hoverInfo.isNotEmpty() && petHeldItemName == null -> resolvedPet.heldItemInternalName = null
        }
        PetRepository.levelToXp(level, resolvedPet.fauxInternalName)?.let { minimumExp ->
            if ((resolvedPet.exp ?: 0.0) < minimumExp) resolvedPet.exp = minimumExp
        }
        val previousPet = ActivePetTracker.currentPet
        ActivePetTracker.assertFoundCurrentData(resolvedPet, PetDataAssertionSource.AUTOPET)
        PetXpEstimator.recordAutopetSwap(resolvedPet, previousPet, autopetTriggerOrNull(hoverInfo))
        PetStorageService.markDirty()
        return true
    }

    private fun resolveAppliedPetItemOrNull(itemName: String): String? {
        val heldItemName = Minecraft.getInstance().player?.mainHandItem?.hoverName?.formattedText()
            ?.removeResets()
            ?.trim()
        if (heldItemName?.removeColor() == itemName.removeColor()) {
            PetRepository.resolvePetItemOrNull(heldItemName)?.let { return it }
        }
        return PetRepository.resolvePetItemOrNull(itemName)
    }

    private fun updateCurrentPetHeldItem(heldItem: String) {
        val currentPet = ActivePetTracker.currentPet ?: return
        if (currentPet.heldItemInternalName == heldItem) return
        currentPet.heldItemInternalName = heldItem
        currentPet.uuid?.let { uuid -> PetStorageService.petStorage.pets.addOrReplace(currentPet) { it.uuid == uuid } }
        ActivePetTracker.assertFoundCurrentData(currentPet, PetDataAssertionSource.CHAT)
        PetStorageService.markDirty()
    }

    private fun hoverTextLines(component: Component): List<String> =
        component.hoverTextComponents().flatMap { hover -> hover.formattedText().split("\n") }

    private fun autopetTriggerOrNull(lines: List<String>): String? =
        lines.map { it.cleanSkyBlockText() }
            .zipWithNext()
            .firstOrNull { (line, trigger) -> line == "When:" && trigger.isNotBlank() }
            ?.second
}
