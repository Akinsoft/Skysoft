package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class PetEntityMatcher(
    private val currentPet: StoredPetData,
    private val expectedTextures: Set<String>,
    private val player: Player,
) {
    val identity: ActivePetObservationIdentity = currentPet.observationIdentity()

    fun observe(entity: Entity, entities: List<Entity>): ActivePetEntityObservation? =
        entity.petCandidate(expectedTextures, player)?.select(entities, currentPet)?.observation(identity)

    fun find(entities: List<Entity>): ActivePetEntityObservation? =
        entities.asSequence()
            .mapNotNull { it.petCandidate(expectedTextures, player) }
            .toList()
            .selectPetCandidate(entities, currentPet)
            ?.observation(identity)

    private fun List<PetCandidate>.selectPetCandidate(
        entities: List<Entity>,
        currentPet: StoredPetData,
    ): SelectedPetCandidate? {
        val exactCandidates = filter { it.exactTextureMatch }
        if (exactCandidates.isNotEmpty()) {
            return exactCandidates.best()?.select(entities, currentPet)
        }
        return filter { it.isFallbackAcceptable }
            .mapNotNull { it.select(entities, currentPet) }
            .minWithOrNull(
                compareByDescending<SelectedPetCandidate> { it.candidate.score }
                    .thenBy { it.candidate.distanceSq },
            )
    }

    private fun List<PetCandidate>.best(): PetCandidate? =
        minWithOrNull(compareByDescending<PetCandidate> { it.score }.thenBy { it.distanceSq })

    private fun PetCandidate.select(
        entities: List<Entity>,
        currentPet: StoredPetData,
    ): SelectedPetCandidate? {
        val name = entities.findPetNameCandidate(currentPet, entity)
        if (!exactTextureMatch && (!isFallbackAcceptable || name == null)) return null
        return SelectedPetCandidate(this, name)
    }

    private fun Entity.petCandidate(expectedTextures: Set<String>, player: Player): PetCandidate? =
        when (this) {
            is ArmorStand -> petCandidate(expectedTextures, player)
            is Display.ItemDisplay -> petCandidate(expectedTextures, player)
            else -> null
        }

    private fun ArmorStand.petCandidate(expectedTextures: Set<String>, player: Player): PetCandidate? {
        val distanceSq = petCandidateDistanceSq(player) ?: return null
        val petEquipment = petEquipment(expectedTextures) ?: return null
        val texture = petEquipment.stack.playerHeadTexture() ?: return null
        val exactTextureMatch = texture in expectedTextures
        val otherEquipmentCount = EquipmentSlot.VALUES.count { slot ->
            slot != petEquipment.slot && !getItemBySlot(slot).isEmpty
        }
        val onlyPetEquipment = otherEquipmentCount == 0
        val score = scoreCandidate(exactTextureMatch, onlyPetEquipment, otherEquipmentCount, distanceSq)
        return PetCandidate(this, texture, exactTextureMatch, otherEquipmentCount, score, distanceSq)
    }

    private fun ArmorStand.petEquipment(expectedTextures: Set<String>): PetEquipment? {
        val playerHeadEquipment = EquipmentSlot.VALUES.mapNotNull { slot ->
            val stack = getItemBySlot(slot)
            if (stack.isEmpty || stack.item != Items.PLAYER_HEAD) null else PetEquipment(slot, stack)
        }
        return playerHeadEquipment.firstOrNull { equipment ->
            equipment.stack.playerHeadTexture()?.let { it in expectedTextures } == true
        } ?: playerHeadEquipment.firstOrNull()
    }

    private fun Display.ItemDisplay.petCandidate(expectedTextures: Set<String>, player: Player): PetCandidate? {
        val distanceSq = petCandidateDistanceSq(player) ?: return null
        val stack = itemStack
        if (stack.isEmpty || stack.item != Items.PLAYER_HEAD) return null
        val texture = stack.playerHeadTexture() ?: return null
        val exactTextureMatch = texture in expectedTextures
        val score = scoreCandidate(exactTextureMatch, distanceSq)
        return PetCandidate(this, texture, exactTextureMatch, 0, score, distanceSq)
    }

    private fun Entity.petCandidateDistanceSq(player: Player): Double? {
        val distanceSq = distanceToSqr(player)
        if (!isAlive || !isValidPetPositionDistanceSq(distanceSq)) return null
        if (distanceSq > PetCandidateBounds.MAX_DISTANCE_TO_PLAYER_SQ) return null

        val relativeY = y - player.y
        if (relativeY !in PetCandidateBounds.MIN_RELATIVE_Y..PetCandidateBounds.MAX_RELATIVE_Y) return null

        val dx = x - player.x
        val dz = z - player.z
        if (dx * dx + dz * dz > PetCandidateBounds.MAX_HORIZONTAL_DISTANCE_TO_PLAYER_SQ) return null
        return distanceSq
    }

    private fun ArmorStand.scoreCandidate(
        exactTextureMatch: Boolean,
        onlyHead: Boolean,
        otherEquipmentCount: Int,
        distanceSq: Double,
    ): Int {
        var score = 0
        if (exactTextureMatch) score += PetCandidateScores.EXACT_TEXTURE
        if (isInvisible) score += PetCandidateScores.INVISIBLE_ARMOR_STAND
        if (isMarker) score += PetCandidateScores.MARKER_ARMOR_STAND
        if (isSmall) score += PetCandidateScores.SMALL_ARMOR_STAND
        if (onlyHead) {
            score += PetCandidateScores.HEAD_ONLY_ARMOR_STAND
        } else {
            score -= PetCandidateScores.EXTRA_EQUIPMENT_PENALTY * otherEquipmentCount
        }
        if (!hasCustomName()) score += PetCandidateScores.UNNAMED_ENTITY
        score += proximityScore(distanceSq)
        return score
    }

    private fun Display.ItemDisplay.scoreCandidate(exactTextureMatch: Boolean, distanceSq: Double): Int {
        var score = PetCandidateScores.ITEM_DISPLAY_BASE
        if (exactTextureMatch) score += PetCandidateScores.EXACT_TEXTURE
        if (!hasCustomName()) score += PetCandidateScores.UNNAMED_ENTITY
        score += proximityScore(distanceSq)
        return score
    }

    private fun proximityScore(distanceSq: Double): Int =
        ((PetCandidateBounds.MAX_DISTANCE_TO_PLAYER - kotlin.math.sqrt(distanceSq)) * PetCandidateScores.PROXIMITY_SCALE)
            .roundToInt()
            .coerceAtLeast(0)

    private fun SelectedPetCandidate.observation(identity: ActivePetObservationIdentity): ActivePetEntityObservation =
        ActivePetEntityObservation(
            entity = candidate.entity,
            texture = candidate.texture,
            identity = identity,
            nameEntity = name?.entity,
            nameRelativeY = name?.relativeY,
        )

    private val PetCandidate.isFallbackAcceptable: Boolean
        get() = !exactTextureMatch &&
            otherEquipmentCount == 0 &&
            distanceSq <= PetCandidateBounds.FALLBACK_MAX_DISTANCE_TO_PLAYER_SQ &&
            score >= PetCandidateScores.MIN_FALLBACK

    private data class PetCandidate(
        val entity: Entity,
        val texture: String,
        val exactTextureMatch: Boolean,
        val otherEquipmentCount: Int,
        val score: Int,
        val distanceSq: Double,
    )

    private data class PetEquipment(
        val slot: EquipmentSlot,
        val stack: ItemStack,
    )

    private data class SelectedPetCandidate(
        val candidate: PetCandidate,
        val name: PetNameCandidate?,
    )

    private object PetCandidateScores {
        const val EXACT_TEXTURE = 10_000
        const val ITEM_DISPLAY_BASE = 450
        const val INVISIBLE_ARMOR_STAND = 500
        const val MARKER_ARMOR_STAND = 300
        const val SMALL_ARMOR_STAND = 100
        const val HEAD_ONLY_ARMOR_STAND = 300
        const val EXTRA_EQUIPMENT_PENALTY = 250
        const val UNNAMED_ENTITY = 80
        const val PROXIMITY_SCALE = 50
        const val MIN_FALLBACK = 500
    }

    private object PetCandidateBounds {
        const val MAX_DISTANCE_TO_PLAYER = 8.0
        const val MAX_DISTANCE_TO_PLAYER_SQ = MAX_DISTANCE_TO_PLAYER * MAX_DISTANCE_TO_PLAYER
        const val FALLBACK_MAX_DISTANCE_TO_PLAYER = 3.5
        const val FALLBACK_MAX_DISTANCE_TO_PLAYER_SQ =
            FALLBACK_MAX_DISTANCE_TO_PLAYER * FALLBACK_MAX_DISTANCE_TO_PLAYER
        const val MAX_HORIZONTAL_DISTANCE_TO_PLAYER = 8.0
        const val MAX_HORIZONTAL_DISTANCE_TO_PLAYER_SQ =
            MAX_HORIZONTAL_DISTANCE_TO_PLAYER * MAX_HORIZONTAL_DISTANCE_TO_PLAYER
        const val MIN_RELATIVE_Y = -1.0
        const val MAX_RELATIVE_Y = 4.0
    }

}

private fun Iterable<Entity>.findPetNameCandidate(
    currentPet: StoredPetData,
    petEntity: Entity,
): PetNameCandidate? =
    asSequence()
        .filterIsInstance<ArmorStand>()
        .mapNotNull { it.petNameCandidate(currentPet, petEntity) }
        .minWithOrNull(compareByDescending<PetNameCandidate> { it.score }.thenBy { it.distanceSq })

private fun ArmorStand.petNameCandidate(currentPet: StoredPetData, petEntity: Entity): PetNameCandidate? {
    if (!isAlive || id == petEntity.id || !hasCustomName()) return null
    if (EquipmentSlot.VALUES.any { !getItemBySlot(it).isEmpty }) return null

    val name = getCustomName()?.string?.replace(Regex("§."), "") ?: return null
    if (!name.contains(currentPet.cleanName, ignoreCase = true)) return null

    val relativeY = y - petEntity.y
    if (relativeY !in PetNameBounds.MIN_OFFSET_Y..PetNameBounds.MAX_OFFSET_Y) return null

    val dx = x - petEntity.x
    val dz = z - petEntity.z
    if (dx * dx + dz * dz > PetNameBounds.MAX_HORIZONTAL_DISTANCE_SQ) return null

    val distanceSq = distanceToSqr(petEntity)
    if (!isValidPetPositionDistanceSq(distanceSq)) return null
    val score = PetNameScores.BASE -
        (kotlin.math.sqrt(distanceSq) * PetNameScores.DISTANCE_SCALE).roundToInt() -
        (abs(relativeY - PetNameBounds.DEFAULT_OFFSET_Y) * PetNameScores.HEIGHT_SCALE).roundToInt()
    return PetNameCandidate(this, relativeY, score, distanceSq)
}

private fun StoredPetData.observationIdentity(): ActivePetObservationIdentity =
    ActivePetObservationIdentity(uuid, petInternalName, skinInternalName, displayIconTexture)

private data class PetNameCandidate(
    val entity: ArmorStand,
    val relativeY: Double,
    val score: Int,
    val distanceSq: Double,
)

private object PetNameScores {
    const val BASE = 1_000
    const val DISTANCE_SCALE = 100
    const val HEIGHT_SCALE = 100
}

internal object PetNameBounds {
    const val DEFAULT_OFFSET_Y = 1.45
    const val MIN_OFFSET_Y = 0.75
    const val MAX_OFFSET_Y = 2.75
    const val MAX_HORIZONTAL_DISTANCE = 2.5
    const val MAX_HORIZONTAL_DISTANCE_SQ = MAX_HORIZONTAL_DISTANCE * MAX_HORIZONTAL_DISTANCE
}
