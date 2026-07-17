package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
object VisiblePetPosition {
    private val config get() = SkysoftConfigGui.config().pets.visiblePetPosition

    private var activePetEntityId: Int? = null
    private var activePetNameEntityId: Int? = null
    private var activePetTexture: String? = null
    private var visualHeadY: Double? = null
    private var rawHeadX: Double? = null
    private var rawHeadY: Double? = null
    private var rawHeadZ: Double? = null
    private var headVisualYOffset = 0.0
    private var lastHeadRenderAge: Float? = null
    private var lastVisualMode: String = "none"
    private var lastVisualTargetRelativeY: Double? = null
    private var nameRelativeY: Double? = null
    private var lastTargetSeenTick = -PetPositionTiming.TARGET_GRACE_TICKS
    private var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
    }

    @JvmStatic
    fun adjustRenderState(entity: Entity, state: EntityRenderState) {
        if (!enabled) return

        val offset = config.details.heightOffset.get().toDouble()
        when (entity.id) {
            activePetEntityId -> {
                val player = Minecraft.getInstance().player ?: return
                adjustHeadRenderState(state, offset, player)
            }
            activePetNameEntityId -> adjustNameRenderState(state, offset)
        }
    }

    private fun adjustHeadRenderState(state: EntityRenderState, offset: Double, player: Player) {
        val rawY = state.y
        if (!config.settings.stopBouncing) {
            visualHeadY = rawY
            lastVisualMode = "raw"
            lastVisualTargetRelativeY = rawY - player.y
            state.y = rawY + offset
            rememberHeadRenderPosition(state.x, rawY, state.z, state.y)
            return
        }
        val targetY = headTargetY(state, player)
        state.y = smoothVisualHeadY(targetY, state.ageInTicks) + offset
        rememberHeadRenderPosition(state.x, rawY, state.z, state.y)
    }

    private fun adjustNameRenderState(state: EntityRenderState, offset: Double) {
        if (!config.settings.stopBouncing) {
            state.y += offset
            return
        }
        val nameOffset = nameRelativeY ?: PetNameBounds.DEFAULT_OFFSET_Y
        val headY = visualHeadY ?: (state.y - nameOffset)
        state.y = headY + nameOffset + offset
    }

    @JvmStatic
    fun shouldInflateCulling(entity: Entity): Boolean =
        enabled && (entity.id == activePetEntityId || entity.id == activePetNameEntityId)

    @JvmStatic
    fun adjustParticleY(x: Double, y: Double, z: Double): Double {
        if (!enabled || abs(headVisualYOffset) < PetParticleBounds.MIN_OFFSET) return y

        // TODO: Find a way to identify pet particles by source or stable particle types instead of only proximity.
        val headX = rawHeadX ?: return y
        val headY = rawHeadY ?: return y
        val headZ = rawHeadZ ?: return y
        val dx = x - headX
        val dz = z - headZ
        if (dx * dx + dz * dz > PetParticleBounds.HORIZONTAL_DISTANCE_SQ) return y

        val relativeY = y - headY
        if (relativeY !in PetParticleBounds.RELATIVE_Y_MIN..PetParticleBounds.RELATIVE_Y_MAX) return y

        return y + headVisualYOffset
    }

    private fun tick() {
        ticks++
        if (!enabled) {
            clear()
            return
        }
        if (ticks % PetPositionTiming.TARGET_SCAN_INTERVAL != 0) return

        val context = trackingContext() ?: return
        trackPet(context)
    }

    private fun trackingContext(): TrackingContext? {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: run {
            clear()
            return null
        }
        val player = minecraft.player ?: run {
            clear()
            return null
        }
        val currentPet = ActivePetTracker.currentPet ?: run {
            clear()
            return null
        }
        return TrackingContext(
            entities = level.entitiesForRendering().toList(),
            player = player,
            currentPet = currentPet,
            expectedTextures = currentPet.expectedTextures(),
        )
    }

    private fun trackPet(context: TrackingContext) {
        val candidates = context.entities
            .asSequence()
            .mapNotNull { it.petCandidate(context.expectedTextures, context.player) }
            .toList()
        val petCandidate = candidates.selectPetCandidate()
        val petEntity = petCandidate?.entity

        if (petEntity == null) {
            if (ticks - lastTargetSeenTick > PetPositionTiming.TARGET_GRACE_TICKS) {
                clear()
            }
            return
        }

        val targetChanged = activePetEntityId != petEntity.id || activePetTexture != petCandidate.texture
        val nameCandidate = context.entities.findPetNameCandidate(context.currentPet, petEntity)

        activePetEntityId = petEntity.id
        activePetTexture = petCandidate.texture
        activePetNameEntityId = nameCandidate?.entity?.id ?: activePetNameEntityId.takeUnless { targetChanged }
        lastTargetSeenTick = ticks

        if (targetChanged) {
            visualHeadY = null
            clearHeadRenderPosition()
            lastHeadRenderAge = null
        }

        if (nameCandidate != null) {
            updateNameRelativeY(nameCandidate.relativeY, targetChanged)
        } else if (targetChanged) {
            nameRelativeY = null
        }
    }

    private fun List<PetCandidate>.selectPetCandidate(): PetCandidate? {
        val exactCandidates = filter { it.exactTextureMatch }
        val selectableCandidates = exactCandidates.ifEmpty {
            filter { it.isFallbackAcceptable }
        }
        return selectableCandidates
            .minWithOrNull(compareByDescending<PetCandidate> { it.score }.thenBy { it.distanceSq })
    }

    private fun Iterable<Entity>.findPetNameCandidate(currentPet: StoredPetData, petEntity: Entity): NameCandidate? =
        asSequence()
            .filterIsInstance<ArmorStand>()
            .mapNotNull { it.petNameCandidate(currentPet, petEntity) }
            .minWithOrNull(compareByDescending<NameCandidate> { it.score }.thenBy { it.distanceSq })

    private fun ArmorStand.petNameCandidate(currentPet: StoredPetData, petEntity: Entity): NameCandidate? {
        if (!isAlive || id == petEntity.id || !hasCustomName()) return null
        if (EquipmentSlot.VALUES.any { !getItemBySlot(it).isEmpty }) return null

        val customNameText = getCustomName()?.string?.stripFormatting() ?: return null
        if (!customNameText.contains(currentPet.cleanName, ignoreCase = true)) return null

        val relativeY = y - petEntity.y
        if (relativeY !in PetNameBounds.MIN_OFFSET_Y..PetNameBounds.MAX_OFFSET_Y) return null

        val dx = x - petEntity.x
        val dz = z - petEntity.z
        if (dx * dx + dz * dz > PetNameBounds.MAX_HORIZONTAL_DISTANCE_SQ) return null

        val distanceSq = distanceToSqr(petEntity)
        if (!isValidPetPositionDistanceSq(distanceSq)) return null
        val score = PetCandidateScores.NAME_BASE -
            (kotlin.math.sqrt(distanceSq) * PetCandidateScores.NAME_DISTANCE_SCALE).roundToInt() -
            (abs(relativeY - PetNameBounds.DEFAULT_OFFSET_Y) * PetCandidateScores.NAME_HEIGHT_SCALE).roundToInt()
        return NameCandidate(
            entity = this,
            relativeY = relativeY,
            score = score,
            distanceSq = distanceSq,
        )
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

        val texture = petEquipment.stack.playerHeadTexture()
        val exactTextureMatch = texture != null && texture in expectedTextures
        val otherEquipmentCount = EquipmentSlot.VALUES.count { slot ->
            slot != petEquipment.slot && !getItemBySlot(slot).isEmpty
        }
        val onlyPetEquipment = otherEquipmentCount == 0
        val score = scoreCandidate(exactTextureMatch, onlyPetEquipment, otherEquipmentCount, distanceSq)
        return PetCandidate(
            entity = this,
            texture = texture,
            exactTextureMatch = exactTextureMatch,
            otherEquipmentCount = otherEquipmentCount,
            score = score,
            distanceSq = distanceSq,
        )
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

        val texture = stack.playerHeadTexture()
        val exactTextureMatch = texture != null && texture in expectedTextures
        val score = scoreCandidate(exactTextureMatch, distanceSq)
        return PetCandidate(
            entity = this,
            texture = texture,
            exactTextureMatch = exactTextureMatch,
            otherEquipmentCount = 0,
            score = score,
            distanceSq = distanceSq,
        )
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

    private fun com.skysoft.data.StoredPetData.expectedTextures(): Set<String> =
        getAnimatedItemStackSequence(firstFrameOnly = false)
            ?.mapNotNull { it.stack.playerHeadTexture() }
            ?.toSet()
            .orEmpty()

    private fun ItemStack.playerHeadTexture(): String? {
        if (isEmpty || item != Items.PLAYER_HEAD) return null
        val profile = get(DataComponents.PROFILE) ?: return null
        return profile.texture()
    }

    private fun ResolvableProfile.texture(): String? =
        partialProfile().properties().get("textures").firstOrNull()?.value

    private fun updateNameRelativeY(relativeY: Double, targetChanged: Boolean) {
        if (config.settings.stopBouncing) {
            nameRelativeY = PetNameBounds.DEFAULT_OFFSET_Y
            return
        }
        nameRelativeY = if (targetChanged || nameRelativeY == null) {
            relativeY
        } else {
            smooth(nameRelativeY ?: relativeY, relativeY)
        }
    }

    private fun headTargetY(state: EntityRenderState, player: Player): Double {
        val rawRelativeY = state.y - player.y
        val dx = state.x - player.x
        val dz = state.z - player.z
        val isIdle = dx * dx + dz * dz <= PetHeadSmoothing.IDLE_HORIZONTAL_DISTANCE_SQ &&
            rawRelativeY in PetHeadSmoothing.IDLE_RELATIVE_Y_MIN..PetHeadSmoothing.IDLE_RELATIVE_Y_MAX
        lastVisualMode = if (isIdle) "idle" else "follow"
        val targetY = if (isIdle) player.y + PetHeadSmoothing.IDLE_HEAD_RELATIVE_Y else state.y
        lastVisualTargetRelativeY = targetY - player.y
        return targetY
    }

    private fun smoothVisualHeadY(rawY: Double, ageInTicks: Float): Double {
        val previous = visualHeadY
        val previousAge = lastHeadRenderAge
        lastHeadRenderAge = ageInTicks

        if (previous == null || previousAge == null || ageInTicks < previousAge) {
            visualHeadY = rawY
            return rawY
        }

        val deltaTicks = (ageInTicks - previousAge).toDouble().coerceIn(0.0, PetHeadSmoothing.MAX_RENDER_DELTA_TICKS)
        val next = previous + (rawY - previous) * visualHeadAlpha(previous, rawY, deltaTicks)
        visualHeadY = next
        return next
    }

    private fun visualHeadAlpha(previous: Double, rawY: Double, deltaTicks: Double): Double {
        val perTickAlpha = when (abs(rawY - previous)) {
            in 0.0..PetHeadSmoothing.BOB_DELTA_Y ->
                if (lastVisualMode == "idle") PetHeadSmoothing.IDLE_ALPHA_PER_TICK else PetHeadSmoothing.BOB_ALPHA_PER_TICK
            in 0.0..PetHeadSmoothing.FOLLOW_DELTA_Y -> PetHeadSmoothing.FOLLOW_ALPHA_PER_TICK
            else -> PetHeadSmoothing.JUMP_ALPHA_PER_TICK
        }
        return 1.0 - (1.0 - perTickAlpha).pow(deltaTicks)
    }

    private fun smooth(previous: Double, current: Double): Double =
        previous + (current - previous) * PetHeadSmoothing.RELATIVE_HEIGHT_SMOOTHING

    private fun rememberHeadRenderPosition(x: Double, rawY: Double, z: Double, renderedY: Double) {
        rawHeadX = x
        rawHeadY = rawY
        rawHeadZ = z
        headVisualYOffset = renderedY - rawY
    }

    private fun clearHeadRenderPosition() {
        rawHeadX = null
        rawHeadY = null
        rawHeadZ = null
        headVisualYOffset = 0.0
    }

    private val PetCandidate.isFallbackAcceptable: Boolean
        get() = !exactTextureMatch &&
            otherEquipmentCount == 0 &&
            distanceSq <= PetCandidateBounds.FALLBACK_MAX_DISTANCE_TO_PLAYER_SQ &&
            score >= PetCandidateScores.MIN_FALLBACK

    private fun String.stripFormatting(): String =
        replace(Regex("§."), "")

    private val enabled: Boolean
        get() = config.enabled && HypixelLocationState.inSkyBlock

    private fun clear() {
        activePetEntityId = null
        activePetNameEntityId = null
        activePetTexture = null
        visualHeadY = null
        clearHeadRenderPosition()
        lastHeadRenderAge = null
        lastVisualMode = "none"
        lastVisualTargetRelativeY = null
        nameRelativeY = null
    }

    private data class PetCandidate(
        val entity: Entity,
        val texture: String?,
        val exactTextureMatch: Boolean,
        val otherEquipmentCount: Int,
        val score: Int,
        val distanceSq: Double,
    )

    private data class PetEquipment(
        val slot: EquipmentSlot,
        val stack: ItemStack,
    )

    private data class NameCandidate(
        val entity: ArmorStand,
        val relativeY: Double,
        val score: Int,
        val distanceSq: Double,
    )

    private data class TrackingContext(
        val entities: List<Entity>,
        val player: Player,
        val currentPet: StoredPetData,
        val expectedTextures: Set<String>,
    )

    private object PetPositionTiming {
        const val TICKS_PER_SECOND = 20
        const val TARGET_SCAN_INTERVAL = 2
        const val TARGET_GRACE_TICKS = TICKS_PER_SECOND * 2
    }

    private object PetCandidateScores {
        const val EXACT_TEXTURE = 10_000
        const val ITEM_DISPLAY_BASE = 450
        const val NAME_BASE = 1_000
        const val NAME_DISTANCE_SCALE = 100
        const val NAME_HEIGHT_SCALE = 100
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

    private object PetNameBounds {
        const val DEFAULT_OFFSET_Y = 1.45
        const val MIN_OFFSET_Y = 0.75
        const val MAX_OFFSET_Y = 2.75
        const val MAX_HORIZONTAL_DISTANCE = 2.5
        const val MAX_HORIZONTAL_DISTANCE_SQ = MAX_HORIZONTAL_DISTANCE * MAX_HORIZONTAL_DISTANCE
    }

    private object PetHeadSmoothing {
        const val IDLE_HEAD_RELATIVE_Y = 0.85
        const val IDLE_RELATIVE_Y_MIN = -2.35
        const val IDLE_RELATIVE_Y_MAX = 2.05
        const val IDLE_HORIZONTAL_DISTANCE = 2.5
        const val IDLE_HORIZONTAL_DISTANCE_SQ = IDLE_HORIZONTAL_DISTANCE * IDLE_HORIZONTAL_DISTANCE
        const val BOB_DELTA_Y = 0.75
        const val FOLLOW_DELTA_Y = 2.0
        const val IDLE_ALPHA_PER_TICK = 0.45
        const val BOB_ALPHA_PER_TICK = 0.08
        const val FOLLOW_ALPHA_PER_TICK = 0.35
        const val JUMP_ALPHA_PER_TICK = 0.65
        const val MAX_RENDER_DELTA_TICKS = 1.0
        const val RELATIVE_HEIGHT_SMOOTHING = 0.15
    }

    private object PetParticleBounds {
        const val MIN_OFFSET = 0.01
        const val HORIZONTAL_DISTANCE = 1.75
        const val HORIZONTAL_DISTANCE_SQ = HORIZONTAL_DISTANCE * HORIZONTAL_DISTANCE
        const val RELATIVE_Y_MIN = -1.25
        const val RELATIVE_Y_MAX = 2.25
    }
}

internal fun isValidPetPositionDistanceSq(distanceSq: Double): Boolean =
    distanceSq.isFinite() && distanceSq >= 0.0
