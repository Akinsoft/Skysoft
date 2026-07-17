package com.skysoft.features.pets

import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

internal object ActivePetEntityTracker {
    private var observation: ActivePetEntityObservation? = null
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick("Active Pet entity tracking") { tick() }
        SkysoftClientEvents.onDisconnect("Active Pet entity disconnect reset", ::clear)
    }

    fun current(): ActivePetEntityObservation? = observation

    private fun tick() {
        ticks++
        val context = trackingContext() ?: run {
            clear()
            return
        }
        val identity = context.currentPet.observationIdentity()
        val previous = observation
        if (previous != null && previous.identity != identity) clear()
        val entities = context.level.entitiesForRendering().toList()

        val trackedEntity = observation?.entity?.id?.let(context.level::getEntity)
        val trackedCandidate = trackedEntity?.petCandidate(context.expectedTextures, context.player)
        val trackedSelection = trackedCandidate?.select(entities, context.currentPet)
        if (trackedSelection != null) {
            observation = trackedSelection.observation(identity)
            return
        }
        if (ticks % TARGET_SCAN_INTERVAL != 0) return

        observation = entities
            .asSequence()
            .mapNotNull { it.petCandidate(context.expectedTextures, context.player) }
            .toList()
            .selectPetCandidate(entities, context.currentPet)
            ?.observation(identity)
    }

    private fun trackingContext(): TrackingContext? {
        if (!HypixelLocationState.inSkyBlock) return null
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val player = minecraft.player ?: return null
        val currentPet = ActivePetTracker.currentPet ?: return null
        return TrackingContext(
            level = level,
            player = player,
            currentPet = currentPet,
            expectedTextures = currentPet.expectedTextures(),
        )
    }

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

    private fun clear() {
        observation = null
    }

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

    private data class TrackingContext(
        val level: net.minecraft.client.multiplayer.ClientLevel,
        val player: Player,
        val currentPet: StoredPetData,
        val expectedTextures: Set<String>,
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

    private const val TARGET_SCAN_INTERVAL = 2
}

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

private fun StoredPetData.expectedTextures(): Set<String> =
    getAnimatedItemStackSequence(firstFrameOnly = false)
        ?.mapNotNull { it.stack.playerHeadTexture() }
        ?.toSet()
        .orEmpty()

private fun StoredPetData.observationIdentity(): ActivePetObservationIdentity =
    ActivePetObservationIdentity(uuid, petInternalName, skinInternalName, displayIconTexture)

private fun ItemStack.playerHeadTexture(): String? {
    if (isEmpty || item != Items.PLAYER_HEAD) return null
    return get(DataComponents.PROFILE)?.texture()
}

private fun ResolvableProfile.texture(): String? =
    partialProfile().properties().get("textures").firstOrNull()?.value

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
