package com.skysoft.features.misc.custombars

import com.skysoft.config.CustomBarDisplayMode
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import net.minecraft.client.Minecraft
import kotlin.math.ceil

internal object CustomBarState {
    private val config get() = SkysoftConfigGui.config().gui.customBars
    private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()
    var health: BarValue? = null
        private set
    var mana: BarValue? = null
        private set
    var vitality: BarValue? = null
        private set
    private var defense: Int? = null
    private var riftDamage: Int? = null
    private var trackedRiftState: Boolean? = null

    fun register(isActive: () -> Boolean) {
        HypixelLocationState.onChange("Custom Bars location", isActive = { true }) { location ->
            if (!location.inSkyBlock) return@onChange
            val currentRiftState = location.currentIsland == SkyBlockIsland.THE_RIFT
            if (trackedRiftState != currentRiftState) {
                reset()
                trackedRiftState = currentRiftState
            }
        }
        ChatEvents.onActionBar("Custom Bars tracking", isActive) { message ->
            update(CustomBarsActionBarParser.parse(message.plainText))
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onDisconnect("Custom Bars reset", ::reset)
        TabListApi.onChange(
            "Custom Bars",
            isActive = {
                isActive() && inRift && config.settings.displays.defense == CustomBarDisplayMode.CUSTOM
            },
            listener = ::updateRiftDamage,
        )
    }

    private fun update(parsed: ParsedCustomBarActionBar) {
        parsed.health?.let { health = it }
        parsed.mana?.let { mana = it }
        parsed.vitality?.let { vitality = it }
        parsed.defense?.let { defense = it }
    }

    private fun reset() {
        health = null
        mana = null
        vitality = null
        defense = null
        riftDamage = null
    }

    fun displayedHealth(): BarValue? {
        if (!inRift) return health
        val player = Minecraft.getInstance().player ?: return null
        return BarValue(
            ceil(player.health.toDouble()).toInt(),
            ceil(player.maxHealth.toDouble()).toInt(),
        )
    }

    private fun updateRiftDamage() {
        riftDamage = RiftCustomBarValues.parseDamage(TabListApi.skyBlockLines.map { it.cleanSkyBlockText() })
    }

    fun displayedDefense(): Int? = if (inRift) riftDamage else defense

}
