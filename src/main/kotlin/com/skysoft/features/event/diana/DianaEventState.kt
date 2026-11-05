package com.skysoft.features.event.diana

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object DianaEventState {
    private val config get() = SkysoftConfigGui.config().events.diana
    private var cachedHotbarSpadeTick = Long.MIN_VALUE
    private var cachedHasSpadeInHotbar = false

    fun canUseHelper(): Boolean = config.burrowHelper.enabled && isOnHub() && hasSpadeInHotbar()

    fun isOnHub(): Boolean = SkyBlockIsland.HUB.isInIsland()

    fun isMythologicalRitualActive(): Boolean =
        MayorPerkApi.mythologicalRitualActive

    fun hasSpadeInHotbar(): Boolean {
        val minecraft = Minecraft.getInstance()
        val tick = minecraft.level?.gameTime
        if (tick != null && tick == cachedHotbarSpadeTick) return cachedHasSpadeInHotbar
        val inventory = minecraft.player?.inventory
        cachedHotbarSpadeTick = tick ?: Long.MIN_VALUE
        cachedHasSpadeInHotbar = tick != null && inventory != null &&
            (0 until HOTBAR_SIZE).any { slot -> inventory.getItem(slot).isDianaSpade() }
        return cachedHasSpadeInHotbar
    }

    fun isHoldingSpade(): Boolean =
        Minecraft.getInstance().player?.mainHandItem.isDianaSpade()

    fun ItemStack?.isDianaSpade(): Boolean = this?.skyBlockId() in DIANA_SPADE_IDS

    private const val HOTBAR_SIZE = 9
    private val DIANA_SPADE_IDS = setOf("ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE")
}
