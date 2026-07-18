package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import kotlin.math.abs
import kotlin.math.pow

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
        ActivePetEntityTracker.registerConsumer("Visible Pet Position") { config.enabled }
        SkysoftClientEvents.onEndTick(
            "Visible Pet Position tick",
            isActive = { config.enabled || activePetEntityId != null || activePetNameEntityId != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Visible Pet Position disconnect reset", ::clear)
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

        val observation = ActivePetEntityTracker.current()
        if (observation == null) {
            if (ticks - lastTargetSeenTick > PetPositionTiming.TARGET_GRACE_TICKS) clear()
            return
        }
        trackPet(observation)
    }

    private fun trackPet(observation: ActivePetEntityObservation) {
        val petEntity = observation.entity
        val targetChanged = activePetEntityId != petEntity.id || activePetTexture != observation.texture

        activePetEntityId = petEntity.id
        activePetTexture = observation.texture
        activePetNameEntityId = observation.nameEntity?.id ?: activePetNameEntityId.takeUnless { targetChanged }
        lastTargetSeenTick = ticks

        if (targetChanged) {
            visualHeadY = null
            clearHeadRenderPosition()
            lastHeadRenderAge = null
        }

        if (observation.nameRelativeY != null) {
            updateNameRelativeY(observation.nameRelativeY, targetChanged)
        } else if (targetChanged) {
            nameRelativeY = null
        }
    }

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

    private object PetPositionTiming {
        const val TICKS_PER_SECOND = 20
        const val TARGET_SCAN_INTERVAL = 2
        const val TARGET_GRACE_TICKS = TICKS_PER_SECOND * 2
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
