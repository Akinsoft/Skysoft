package com.skysoft.features.helditem

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.skysoft.config.HeldItemSwingStyle
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.MinecraftRenderer
import com.skysoft.utils.SkysoftErrorBoundary
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack

object HeldItemSwingVisuals {
    private var itemOnlySwing: ItemOnlySwing? = null

    @JvmStatic
    fun renderWithSwing(itemStack: ItemStack, attack: Float, arm: HumanoidArm, isWhackSwing: Boolean, render: Runnable) {
        val previousSwing = itemOnlySwing
        itemOnlySwing = null
        try {
            SkysoftErrorBoundary.run("Held Item swing state") { begin(itemStack, attack, arm, isWhackSwing) }
            render.run()
        } finally {
            itemOnlySwing = previousSwing
        }
    }

    private fun begin(itemStack: ItemStack, attack: Float, arm: HumanoidArm, isWhackSwing: Boolean) {
        if (!HeldItemCustomization.isEligible(itemStack)) {
            itemOnlySwing = null
            return
        }
        val config = SkysoftConfigGui.config().world.heldItem
        val transform = HeldItemTransforms.effectiveTransform(itemStack)
        itemOnlySwing = ItemOnlySwing(itemStack, attack, arm).takeIf {
            config.enabled &&
                transform.swingStyle == HeldItemSwingStyle.ITEM_ONLY &&
                isWhackSwing
        }
    }

    @JvmStatic
    fun replaceVanillaSwing(): SwingReplacementResult {
        val swing = itemOnlySwing ?: return SwingReplacementResult.UNCHANGED
        swing.isVanillaSwingReplaced = true
        return SwingReplacementResult.REPLACED
    }

    @JvmStatic
    fun apply(itemStack: ItemStack, poseStack: PoseStack) {
        val swing = itemOnlySwing?.takeIf {
            it.itemStack === itemStack && it.isVanillaSwingReplaced
        } ?: return
        applyItemOnlySwing(poseStack, swing.attack, swing.arm)
    }

    internal fun applyItemOnlySwing(poseStack: PoseStack, attack: Float, arm: HumanoidArm) {
        if (attack <= 0f) return
        val direction = if (arm == HumanoidArm.RIGHT) 1f else -1f
        val arc = sin(sqrt(attack.toDouble()) * Math.PI).toFloat()
        val twist = sin(attack * attack * Math.PI).toFloat()
        MinecraftRenderer.rotate(poseStack, Axis.YP.rotationDegrees(direction * twist * SWING_Y_DEGREES))
        MinecraftRenderer.rotate(poseStack, Axis.ZP.rotationDegrees(direction * arc * SWING_Z_DEGREES))
        MinecraftRenderer.rotate(poseStack, Axis.XP.rotationDegrees(arc * SWING_X_DEGREES))
    }

    private data class ItemOnlySwing(
        val itemStack: ItemStack,
        val attack: Float,
        val arm: HumanoidArm,
        var isVanillaSwingReplaced: Boolean = false,
    )

    private const val SWING_X_DEGREES = -80f
    private const val SWING_Y_DEGREES = -20f
    private const val SWING_Z_DEGREES = -20f
}

enum class SwingReplacementResult {
    REPLACED,
    UNCHANGED,
}
