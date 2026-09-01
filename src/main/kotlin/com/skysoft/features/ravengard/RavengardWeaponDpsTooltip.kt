package com.skysoft.features.ravengard

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.ravengard.RAVENGARD_NAMESPACE
import com.skysoft.utils.ItemTooltipEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

object RavengardWeaponDpsTooltip {
    fun register() {
        ItemTooltipEvents.register("Ravengard weapon DPS", ::isEnabled) tooltip@{ stack, _, _, tooltip ->
            if (!isRavengardWeapon(stack)) return@tooltip
            addRavengardWeaponDps(tooltip)
        }
    }

    private fun isEnabled(): Boolean =
        HypixelLocationState.inRavengard && SkysoftConfigGui.config().ravengard.showWeaponDps
}

internal fun addRavengardWeaponDps(lines: MutableList<Component>) {
    val damage = lines.firstNotNullOfOrNull { line -> ravengardStatValue(line, DAMAGE) } ?: return
    val attackSpeedIndex = lines.indexOfFirst { line -> ravengardStatValue(line, ATTACK_SPEED) != null }
    if (attackSpeedIndex < 0) return
    val attackSpeed = ravengardStatValue(lines[attackSpeedIndex], ATTACK_SPEED) ?: return
    if (damage.signum() <= 0 || attackSpeed.signum() <= 0) return
    val dps = damage.multiply(attackSpeed).stripTrailingZeros().toPlainString()
    lines.add(attackSpeedIndex + 1, Component.literal("$dps DPS").withStyle(ChatFormatting.GRAY))
}

private fun isRavengardWeapon(stack: ItemStack): Boolean =
    isRavengardWeapon(
        stack.get(DataComponents.TOOLTIP_STYLE),
        stack.get(DataComponents.ITEM_MODEL),
        stack.has(DataComponents.WEAPON),
    )

internal fun isRavengardWeapon(
    tooltipStyle: Identifier?,
    model: Identifier?,
    hasWeaponComponent: Boolean,
): Boolean {
    if (tooltipStyle?.namespace != RAVENGARD_NAMESPACE) return false
    if (hasWeaponComponent) return true
    return model?.namespace == RAVENGARD_NAMESPACE && model.path.startsWith(WEAPON_MODEL_PREFIX)
}

private const val DAMAGE = "Damage"
private const val ATTACK_SPEED = "Attack Speed"
private const val WEAPON_MODEL_PREFIX = "item/weapons/"
