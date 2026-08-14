package com.skysoft.features.ravengard

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.renderables.withIsolatedPose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object CrownValueOverlay {
    @JvmStatic
    fun render(context: GuiGraphicsExtractor, slot: Slot) {
        if (!isEnabled()) return
        val value = slot.item.crownValue() ?: return
        val font = Minecraft.getInstance().font
        val scale = minOf(TEXT_SCALE, MAX_TEXT_WIDTH / font.width(CROWN + value).coerceAtLeast(1))
        context.withIsolatedPose {
            pose().translate(slot.x.toFloat(), slot.y.toFloat())
            pose().scale(scale, scale)
            text(font, CROWN, 0, 0, CROWN_COLOR, true)
            text(font, value, font.width(CROWN), 0, VALUE_COLOR, true)
        }
    }

    private fun isEnabled(): Boolean =
        HypixelLocationState.inRavengard && SkysoftConfigGui.config().ravengard.showCrownValues
}

private fun ItemStack.crownValue(): String? =
    get(DataComponents.LORE)?.lines()?.let(::crownValueFromLore)

internal fun crownValueFromLore(lines: Iterable<Component>): String? =
    lines.firstNotNullOfOrNull { line ->
        CROWN_VALUE_PATTERN.matchEntire(line.string.trim())?.groups?.get("value")?.value
    }

private val CROWN_VALUE_PATTERN = Regex("""^👑(?<value>\d[\d,]*) Crowns?$""")
private const val CROWN = "👑"
private const val TEXT_SCALE = 0.5f
private const val MAX_TEXT_WIDTH = 15f
private const val CROWN_COLOR = 0xFFFFFFFF.toInt()
private const val VALUE_COLOR = 0xFFFFCE47.toInt()
