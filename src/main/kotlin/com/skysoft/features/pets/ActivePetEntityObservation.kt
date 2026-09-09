package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import java.util.UUID
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

internal data class ActivePetEntityObservation(
    val entity: Entity,
    val texture: String,
    val identity: ActivePetObservationIdentity,
    val nameEntity: ArmorStand?,
    val nameRelativeY: Double?,
) {
    fun matches(currentPet: StoredPetData): Boolean = identity == ActivePetObservationIdentity(
        currentPet.uuid,
        currentPet.petInternalName,
        currentPet.skinInternalName,
        currentPet.displayIconTexture,
    )
}

internal data class ActivePetObservationIdentity(
    val uuid: UUID?,
    val petInternalName: String,
    val skinInternalName: String?,
    val displayIconTexture: String?,
)
