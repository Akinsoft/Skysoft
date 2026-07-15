package com.skysoft.features.event.diana

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.ScreenTitleRenderer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

internal object DianaWarpTitleRenderer {
    fun register(suggestionProvider: () -> DianaWarpSuggestion?) {
        ScreenTitleRenderer.registerTitleOverlay(
            id = "diana_warp_hint",
            visible = { isVisible(suggestionProvider) },
            render = { context -> render(context, suggestionProvider) },
        )
    }

    private fun isVisible(suggestionProvider: () -> DianaWarpSuggestion?): Boolean =
        suggestionProvider() != null && !MinecraftClient.isGuiHidden(Minecraft.getInstance())

    private fun render(context: GuiGraphicsExtractor, suggestionProvider: () -> DianaWarpSuggestion?) {
        val suggestion = suggestionProvider() ?: return
        val title = Component.literal("Warp to ${suggestion.point.displayName}")
            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
        val yOffset = if (ScreenAlertRenderer.hasActiveAlerts()) {
            WARP_TITLE_WITH_RARE_MOB_ALERT_Y_OFFSET
        } else {
            WARP_TITLE_Y_OFFSET
        }
        ScreenTitleRenderer.draw(context, title, WARP_TITLE_SCALE, yOffset)
    }

    private const val WARP_TITLE_SCALE = 2.2f
    private const val WARP_TITLE_Y_OFFSET = -92f
    private const val WARP_TITLE_WITH_RARE_MOB_ALERT_Y_OFFSET = -42f
}
