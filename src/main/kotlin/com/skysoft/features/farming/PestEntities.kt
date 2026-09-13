package com.skysoft.features.farming

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.data.skyblock.pets.PetSkins
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

internal object PestEntities {
    fun texturesByName(): Map<String, String> = SkyBlockDataRepository.entries.asSequence()
        .filter { entry -> entry.key.kind == ItemListEntryKind.ENTITY && "Pest" in entry.tags }
        .mapNotNull { entry ->
            SkyBlockDataRepository.entity(entry.key.id)?.texture?.let { texture ->
                entry.displayName to PetSkins.textureIdentity(texture)
            }
        }
        .toMap()

    fun matching(textures: Collection<String>): Set<ArmorStand> = ClientEntitySnapshot.entities().asSequence()
        .filterIsInstance<ArmorStand>()
        .filterTo(mutableSetOf()) { armorStand ->
            armorStand.isAlive && armorStand.isInvisible && !armorStand.isMarker &&
                armorStand.getItemBySlot(EquipmentSlot.HEAD).playerHeadTexture()
                    ?.let { texture -> PetSkins.textureIdentity(texture) in textures } == true
        }
}
