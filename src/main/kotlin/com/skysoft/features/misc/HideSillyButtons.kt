package com.skysoft.features.misc

import com.skysoft.config.HiddenMenuButton
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.SkysoftErrorBoundary
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier

internal object HideSillyButtons {
    private val config get() = SkysoftConfigGui.config().misc.hideSillyButtons

    fun register() {
        val phase = Identifier.fromNamespaceAndPath("skysoft", "menu_button_layout")
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, phase)
        ScreenEvents.AFTER_INIT.register(phase) { _, screen, _, _ ->
            SkysoftErrorBoundary.run("Hide Silly Buttons") {
                if (config.enabled) {
                    val widgets = Screens.getWidgets(screen)
                    val menu = if (screen is TitleScreen || screen is PauseScreen) {
                        widgets.filter { it.visible && it.height == Button.DEFAULT_HEIGHT && it.y >= 0 && it.y < screen.height }
                    } else {
                        emptyList()
                    }
                    widgets.removeIf(::shouldHide)
                    if (menu.isNotEmpty()) {
                        HiddenMenuLayout.compact(screen, widgets, menu, config.details.fullWidthModsButton)
                    }
                    if (screen.focused != null && screen.focused !in screen.children()) screen.focused = null
                }
            }
        }
    }

    @JvmStatic
    fun shouldHideRealmsNotifications(): Boolean =
        config.enabled && HiddenMenuButton.MINECRAFT_REALMS in config.settings.buttons.get()

    @JvmStatic
    fun realmsButton(screen: TitleScreen): AbstractWidget? {
        if (!config.enabled) return null
        return Screens.getWidgets(screen).firstOrNull {
            it.visible && (it.message.contents as? TranslatableContents)?.key == HiddenMenuButton.MINECRAFT_REALMS.translationKey
        }
    }

    private fun shouldHide(button: AbstractWidget): Boolean {
        val contents = button.message.contents as? TranslatableContents ?: return false
        val buttons = config.settings.buttons.get()
        if (contents.key == "menu.feedback") {
            return HiddenMenuButton.GIVE_FEEDBACK in buttons && HiddenMenuButton.REPORT_BUGS in buttons
        }
        val selected = buttons.firstOrNull { it.translationKey == contents.key } ?: return false
        return when (selected) {
            HiddenMenuButton.ACCESSIBILITY_SETTINGS, HiddenMenuButton.CHANGE_LANGUAGE -> button.width == button.height
            else -> true
        }
    }
}
