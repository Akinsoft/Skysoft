package com.skysoft.features.helditem

import com.skysoft.config.SkysoftConfigGui
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity

object HeldItemSwing {
    @JvmStatic
    fun duration(entity: LivingEntity, vanillaDuration: Int): Int {
        val minecraft = Minecraft.getInstance()
        if (entity !== minecraft.player) return vanillaDuration

        val hand = entity.swingingArm ?: InteractionHand.MAIN_HAND
        val itemStack = entity.getItemInHand(hand)
        if (!HeldItemCustomization.isEligible(itemStack)) return vanillaDuration

        val config = SkysoftConfigGui.config().gui.heldItem
        if (!config.enabled) return vanillaDuration

        val transform = HeldItemTransforms.effectiveTransform(itemStack)
        return adjustedDuration(
            vanillaDuration = vanillaDuration,
            baseDuration = itemStack.swingAnimation.duration(),
            speed = transform.swingSpeed,
            ignoresMiningEffects = config.settings.ignoresMiningEffects,
        )
    }

    internal fun adjustedDuration(
        vanillaDuration: Int,
        baseDuration: Int,
        speed: Float,
        ignoresMiningEffects: Boolean,
    ): Int {
        require(speed > 0f)
        if (speed == 1f && !ignoresMiningEffects) return vanillaDuration
        val duration = if (ignoresMiningEffects) baseDuration else vanillaDuration
        return (duration / speed).roundToInt().coerceAtLeast(MIN_VISIBLE_DURATION)
    }

    private const val MIN_VISIBLE_DURATION = 2
}
