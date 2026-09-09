package com.skysoft.features.pets

import com.skysoft.SkysoftMod
import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemUtilities.formattedHoverName
import com.skysoft.features.pets.PetItemUtilities.toExactPetDataOrNull
import com.skysoft.utils.RegexUtilities.group
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

private val PET_MENU_ADD_CONFIRMATION_MAX_AGE = 10.seconds

internal object PetStorageHeldPetAdd {
    private val pendingAdds = mutableListOf<PendingPetMenuAdd>()

    fun recordUseItem() {
        if (!HypixelLocationState.inSkyBlock) return
        pruneExpiredPendingAdds()
        val pending = heldPetAddOrNull() ?: return
        pending.petData.uuid?.let { petUuid ->
            pendingAdds.removeAll { it.petData.uuid == petUuid }
        }
        pendingAdds += pending
    }

    fun confirmAdded(confirmedPetName: String) {
        pruneExpiredPendingAdds()
        if (pendingAdds.isEmpty()) {
            SkysoftMod.LOGGER.warn(
                "Saw pet-menu add confirmation for {} but no recent held pet use was recorded",
                confirmedPetName,
            )
            return
        }

        val pendingIndex = pendingAdds.indexOfFirst { it.matches(confirmedPetName) }
        if (pendingIndex < 0) {
            val pendingSummaries = pendingAdds.joinToString { it.debugSummary() }
            SkysoftMod.LOGGER.warn(
                "Ignoring pet-menu add confirmation for {} because it did not match pending held pets: {}",
                confirmedPetName,
                pendingSummaries,
            )
            return
        }
        val pending = pendingAdds.removeAt(pendingIndex)

        pending.petData.uuid ?: run {
            SkysoftMod.LOGGER.warn(
                "Saw pet-menu add confirmation for {} but the held pet item did not include a UUID",
                confirmedPetName,
            )
            return
        }
        PetStorageService.storePet(pending.petData)
    }

    private fun heldPetAddOrNull(): PendingPetMenuAdd? {
        val player = Minecraft.getInstance().player ?: return null
        return listOf(player.mainHandItem, player.offhandItem).firstNotNullOfOrNull { stack ->
            stack.toPendingPetMenuAddOrNull()
        }
    }

    private fun ItemStack.toPendingPetMenuAddOrNull(): PendingPetMenuAdd? {
        val petData = takeUnless { it.isEmpty }?.toExactPetDataOrNull() ?: return null
        val displayName = petMenuPetStackNamePattern.matchEntire(formattedHoverName())?.group("pet")?.trim()
        return PendingPetMenuAdd(petData, displayName, ElapsedTimeMark.now())
    }

    private fun pruneExpiredPendingAdds() {
        pendingAdds.removeAll { it.createdAt.passedSince() > PET_MENU_ADD_CONFIRMATION_MAX_AGE }
    }

    private fun PendingPetMenuAdd.matches(confirmedPetName: String): Boolean =
        listOf(displayName, petData.displayName, petData.cleanName)
            .filterNotNull()
            .distinct()
            .any { confirmedPetName.normalizedPetName() == it.normalizedPetName() }

    private fun String.normalizedPetName(): String =
        cleanSkyBlockText().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun StoredPetData.debugSummary(): String =
        "uuid=${uuid ?: "none"}, level=$level, xp=${exp ?: 0.0}"

    private fun PendingPetMenuAdd.debugSummary(): String =
        "${displayName ?: petData.displayName} (${petData.debugSummary()})"

    private data class PendingPetMenuAdd(
        val petData: StoredPetData,
        val displayName: String?,
        val createdAt: ElapsedTimeMark,
    )
}
