package com.skysoft.features.combat

import com.skysoft.events.sound.ClientSoundEvent
import com.skysoft.events.sound.ClientSoundEvents
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs

internal object BetterShurikenSoundCorrelation {
    private var lastThrowAtMillis = 0L
    private var confirmationAtMillis = 0L
    private var recentTarget: TimedTarget? = null
    private var recentProjection: TimedProjection? = null
    private var targetConsumer: ((SoundTargetResolution) -> Unit)? = null

    fun register(consumer: (SoundTargetResolution) -> Unit) {
        targetConsumer = consumer
        ClientSoundEvents.register("Better Shuriken sound correlation", ::onSound)
    }

    fun noteThrow() {
        val now = System.currentTimeMillis()
        val retainedConfirmation = now - confirmationAtMillis <= PRE_THROW_CONFIRMATION_WINDOW_MILLIS
        lastThrowAtMillis = now
        if (!retainedConfirmation) confirmationAtMillis = 0L
        recentTarget = null
        recentProjection = null
    }

    fun noteProjectedImpact(mob: LivingEntity) {
        recentProjection = TimedProjection(mob, System.currentTimeMillis())
    }

    fun recentTarget(): LivingEntity? {
        val target = recentTarget ?: return null
        return target.mob.takeIf { System.currentTimeMillis() - target.atMillis <= SOUND_OVERRIDE_WINDOW_MILLIS }
    }

    fun hasRecentConfirmation(): Boolean =
        System.currentTimeMillis() - confirmationAtMillis <= SOUND_OVERRIDE_WINDOW_MILLIS

    fun clear() {
        lastThrowAtMillis = 0L
        confirmationAtMillis = 0L
        recentTarget = null
        recentProjection = null
    }

    private fun onSound(event: ClientSoundEvent) {
        val now = System.currentTimeMillis()
        if (now - lastThrowAtMillis > THROW_SOUND_WINDOW_MILLIS) return
        if (event.isShurikenConfirmation) {
            confirmationAtMillis = now
            val projectedTarget = recentProjection
                ?.takeIf { projection -> now - projection.atMillis <= SOUND_OVERRIDE_WINDOW_MILLIS }
                ?.mob
            if (projectedTarget != null) {
                recentTarget = TimedTarget(projectedTarget, now)
                targetConsumer?.invoke(SoundTargetResolution(projectedTarget, null))
            }
            return
        }
        if (!event.isTargetHurtSound || now - confirmationAtMillis > HURT_SOUND_WINDOW_MILLIS) return
        val location = event.location ?: return
        val target = closestTarget(Vec3(location.x, location.y, location.z)) ?: return
        val replacedMobUuid = recentProjection
            ?.takeIf { projection ->
                projection.mob.uuid != target.uuid && now - projection.atMillis <= SOUND_OVERRIDE_WINDOW_MILLIS
            }
            ?.mob
            ?.uuid
        recentTarget = TimedTarget(target, now)
        targetConsumer?.invoke(SoundTargetResolution(target, replacedMobUuid))
    }

    private fun closestTarget(location: Vec3): LivingEntity? {
        val level = Minecraft.getInstance().level ?: return null
        return level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { entity -> entity.isAlive && entity !is ArmorStand && entity !is Player }
            .map { entity -> entity to entity.boundingBox.distanceToSqr(location) }
            .minByOrNull { (_, distance) -> distance }
            ?.takeIf { (_, distance) -> distance <= MAX_SOUND_TARGET_DISTANCE_SQUARED }
            ?.first
    }

    private val ClientSoundEvent.isShurikenConfirmation: Boolean
        get() = sound.location().toString() == SHURIKEN_CONFIRMATION_SOUND &&
            source == SoundSource.PLAYERS &&
            abs(volume - SHURIKEN_CONFIRMATION_VOLUME) <= SOUND_VALUE_TOLERANCE &&
            abs(pitch - SHURIKEN_CONFIRMATION_PITCH) <= SOUND_VALUE_TOLERANCE

    private val ClientSoundEvent.isTargetHurtSound: Boolean
        get() = source == SoundSource.HOSTILE && sound.location().path.endsWith(HURT_SOUND_SUFFIX)

    data class SoundTargetResolution(
        val target: LivingEntity,
        val replacedMobUuid: UUID?,
    )

    private data class TimedTarget(val mob: LivingEntity, val atMillis: Long)

    private data class TimedProjection(val mob: LivingEntity, val atMillis: Long)

    private const val SHURIKEN_CONFIRMATION_SOUND = "minecraft:entity.experience_orb.pickup"
    private const val SHURIKEN_CONFIRMATION_VOLUME = 1.0f
    private const val SHURIKEN_CONFIRMATION_PITCH = 1.4920635f
    private const val HURT_SOUND_SUFFIX = ".hurt"
    private const val SOUND_VALUE_TOLERANCE = 0.0001f
    private const val THROW_SOUND_WINDOW_MILLIS = 2_000L
    private const val PRE_THROW_CONFIRMATION_WINDOW_MILLIS = 250L
    private const val HURT_SOUND_WINDOW_MILLIS = 100L
    private const val SOUND_OVERRIDE_WINDOW_MILLIS = 500L
    private const val MAX_SOUND_TARGET_DISTANCE_SQUARED = 1.5 * 1.5
}
