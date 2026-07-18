package com.skysoft.features.inventory

import com.skysoft.config.MAX_FULL_INVENTORY_EMPTY_SLOTS
import com.skysoft.config.MIN_FULL_INVENTORY_EMPTY_SLOTS
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.render.ScreenAlert
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenAlertSound
import com.skysoft.utils.render.ScreenTitleLine
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

object FullInventoryWarning {
    private const val TITLE = "Inventory Full"
    private const val TITLE_SCALE = 4.0f
    private const val TITLE_DURATION_MILLIS = 2_500L
    private const val ALERT_SOUND_VOLUME = 0.5f
    private const val ALERT_SOUND_PITCH = 1.0f

    private val config get() = SkysoftConfigGui.config().inventory.fullInventory
    private val title = Component.literal(TITLE).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)

    private var wasWarning = false

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Full Inventory Warning tick",
            isActive = { config.enabled || wasWarning },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Full Inventory Warning disconnect reset", ::reset)
    }

    private fun tick() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (!config.enabled || !HypixelLocationState.inSkyBlock || player == null || player.isCreative || player.isSpectator) {
            reset()
            return
        }

        val threshold = config.settings.emptySlots.coerceIn(
            MIN_FULL_INVENTORY_EMPTY_SLOTS,
            MAX_FULL_INVENTORY_EMPTY_SLOTS,
        )
        val warning = emptySlots(player) <= threshold
        if (warning && !wasWarning) {
            alert()
        }
        wasWarning = warning
    }

    private fun alert() {
        ScreenAlertRenderer.show(
            ScreenAlert(
                id = ALERT_ID,
                lines = listOf(ScreenTitleLine(title, TITLE_SCALE)),
                durationMillis = TITLE_DURATION_MILLIS,
                sound = if (config.details.playSound) {
                    ScreenAlertSound(
                        event = SoundEvents.NOTE_BLOCK_PLING.value(),
                        volume = ALERT_SOUND_VOLUME,
                        pitch = ALERT_SOUND_PITCH,
                    )
                } else {
                    null
                },
                preferredYOffset = TITLE_Y_OFFSET,
            ),
        )
    }

    private fun emptySlots(player: LocalPlayer): Int = player.inventory.getNonEquipmentItems().count { it.isEmpty }

    private fun reset() {
        wasWarning = false
        ScreenAlertRenderer.clear(ALERT_ID)
    }

    private const val ALERT_ID = "full_inventory_warning"
    private const val TITLE_Y_OFFSET = -60f
}
