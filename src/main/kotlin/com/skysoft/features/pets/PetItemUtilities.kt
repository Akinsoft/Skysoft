package com.skysoft.features.pets

import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.getStringOrNull
import com.skysoft.utils.TextUtilities.parseUUIDOrNull
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

object PetItemUtilities {
    private val gson = Gson()

    data class PetInfo(
        @Expose val type: String,
        @Expose val active: Boolean = false,
        @Expose val exp: Double = 0.0,
        @Expose val tier: SkyBlockRarity,
        @Expose val hideInfo: Boolean = false,
        @Expose val heldItem: String? = null,
        @Expose val candyUsed: Int = 0,
        @Expose val skin: String? = null,
        @Expose val uuid: String? = null,
        @Expose val uniqueId: String? = null,
        @Expose val hideRightClick: Boolean? = null,
        @Expose val noMove: Boolean? = null,
    ) {
        val ownedUuid: UUID? get() = uniqueId?.parseUUIDOrNull() ?: uuid?.parseUUIDOrNull()
        val properSkinItem: String? get() = skin?.let { "PET_SKIN_$skin" }
    }

    fun ItemStack.getPetInfo(): PetInfo? {
        val colorlessName = hoverName.string
        if (colorlessName.contains("→") || colorlessName.contains("{LVL}")) return null
        val petInfoJson = extraAttributes()
            ?.getStringOrNull("petInfo")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return gson.fromJson(petInfoJson, PetInfo::class.java)
    }

    fun ItemStack.toExactPetDataOrNull(): StoredPetData? {
        val petInfo = getPetInfo() ?: return null
        val internalName = skyBlockId() ?: "${petInfo.type};${petInfo.tier.id}"
        return StoredPetData(
            petInternalName = internalName,
            skinInternalName = petInfo.properSkinItem,
            heldItemInternalName = petInfo.heldItem,
            exp = petInfo.exp,
            uuid = petInfo.ownedUuid,
            displayIconTexture = playerHeadTextureOrNull(),
            exactItemStack = copy(),
        )
    }

    fun ItemStack.playerHeadTextureOrNull(): String? {
        if (isEmpty || item != Items.PLAYER_HEAD) return null
        val profile = get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().properties().get("textures").firstOrNull()?.value?.takeIf { it.isNotBlank() }
    }
}
