package com.skysoft.features.bazaar

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal fun isBazaarTrackerVisible(minecraft: Minecraft): Boolean =
    config.enabled &&
        (!config.details.isOnlyInMenus || MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>) &&
        !MinecraftClient.isGuiHidden(minecraft) &&
        HypixelLocationState.inSkyBlock &&
        (!config.details.hideWhenEmpty || storage.activeOrders.isNotEmpty())
