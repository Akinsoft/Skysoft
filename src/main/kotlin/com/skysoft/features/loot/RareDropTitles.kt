package com.skysoft.features.loot

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockItemRarity
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.NumberUtilities.coinFormat
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenTitleLine
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

internal object RareDropTitles {
    private val config get() = SkysoftConfigGui.config().misc.rareDropTitles
    private val minimumValue = RareLootThresholdReader("rare drop title minimum value")

    fun show(drop: RareLootDrop, value: RareLootValue?) {
        val threshold = minimumValue.read(config.settings.minimumValue) ?: return
        val resolvedValue = value ?: return
        if (!RareLootEligibility.hasMinimumValue(threshold, resolvedValue)) return
        val rarity = SkyBlockItemRarity.fromInternalName(drop.itemId) ?: return
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(
                    ScreenTitleLine(
                        RareDropTitleFormatter.format(drop, resolvedValue, rarity),
                        TITLE_SCALE,
                    ),
                ),
                durationMillis = TITLE_DURATION_MILLIS,
            ),
        )
    }

    fun clear() {
        minimumValue.clear()
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private const val ALERT_ID = "rare_drop_title"
    private const val TITLE_DURATION_MILLIS = 2_500L
    private const val TITLE_SCALE = 2.7f
}

internal object RareDropTitleFormatter {
    fun format(drop: RareLootDrop, value: RareLootValue, rarity: SkyBlockRarity): Component =
        Component.empty()
            .append(Component.literal(drop.prefix()).withStyle(ChatFormatting.WHITE))
            .append(
                Component.literal(drop.displayName).withStyle { style ->
                    if (drop.itemId?.startsWith(ULTIMATE_ENCHANTMENT_PREFIX) == true) {
                        style.withColor(ChatFormatting.LIGHT_PURPLE)
                    } else {
                        style.withColor(rarity.color.rgb and RGB_MASK)
                    }
                },
            )
            .append(Component.literal(" (${value.coins.coinFormat()})").withStyle(ChatFormatting.GOLD))

    private fun RareLootDrop.prefix(): String =
        if (amount > 1) "+ ${amount}x " else "+ "

    private const val ULTIMATE_ENCHANTMENT_PREFIX = "ENCHANTMENT_ULTIMATE_"
}
