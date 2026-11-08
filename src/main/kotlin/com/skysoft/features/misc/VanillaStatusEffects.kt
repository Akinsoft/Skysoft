package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements

internal object VanillaStatusEffects {
    fun register() {
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS) { vanilla ->
            HudElement { context, tick ->
                if (!areHidden()) vanilla.extractRenderState(context, tick)
            }
        }
    }

    @JvmStatic
    fun areHidden(): Boolean = SkysoftConfigGui.config().gui.vanillaUi.areVanillaStatusEffectsHidden
}
