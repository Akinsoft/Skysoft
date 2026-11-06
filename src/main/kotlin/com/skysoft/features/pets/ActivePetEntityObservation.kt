package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import java.util.UUID
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

internal data class ActivePetEntityObservation(
    val entity: Entity,
    val texture: String,
    val identity: PetVisualIdentity,
    val nameEntity: ArmorStand?,
    val nameRelativeY: Double?,
) {
    fun matches(currentPet: StoredPetData): Boolean = identity == currentPet.visualIdentity()
}

internal data class PetVisualIdentity(
    val uuid: UUID?,
    val petInternalName: String,
    val skinInternalName: String?,
    val displayIconTexture: String?,
)

internal fun StoredPetData.visualIdentity(): PetVisualIdentity =
    PetVisualIdentity(uuid, petInternalName, skinInternalName, displayIconTexture)
