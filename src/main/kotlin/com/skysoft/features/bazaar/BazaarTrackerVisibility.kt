package com.skysoft.features.bazaar

import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal fun isBazaarTrackerVisible(minecraft: Minecraft): Boolean {
    if (!config.enabled) return false
    val screen = MinecraftClient.screen(minecraft)
    if (!shouldShowBazaarTrackerOnScreen(config.details.isOnlyInMenus, screen is AbstractContainerScreen<*>)) return false
    if (MinecraftClient.isGuiHidden(minecraft)) return false
    if (!HypixelLocationState.inSkyBlock) return false
    if (!shouldShowBazaarTrackerContent(config.details.hideWhenEmpty, storage.activeOrders.isNotEmpty())) return false
    return true
}

internal fun shouldShowBazaarTrackerOnScreen(
    isOnlyInMenus: Boolean,
    isContainerMenuOpen: Boolean,
): Boolean = !isOnlyInMenus || isContainerMenuOpen
