package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class FixesConfig {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Menu Drop Fix",
        desc = "Prevent the SkyBlock Menu from opening when dropping hovered inventory items.\n§cUse at your own risk.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var preventSkyBlockMenuOpeningOnInventoryDrop = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Held Item Update Fix",
        desc = "Prevent same-item SkyBlock data updates from replaying the hand-swap animation or resetting block breaking.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isHeldItemUpdateFixEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Throwing Axe Ghost Fix",
        desc = "Hide log animations left behind by Throwing Axe.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideThrowingAxeGhostBlocks = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Centered Crosshair",
        desc = "Fix the vanilla crosshair being slightly off-center.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isCenteredCrosshairFixEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Hide Glitch Mobs",
        desc = "Hide nametagless rare mob player models left behind by Hypixel.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideGlitchMobs = true

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "Hide Bugged Nameplates",
        desc = "Hide bugged floating nameplates left behind by Hypixel.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideBuggedNameplates = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Player Head Skin Fix",
        desc = "Stops custom player heads from flashing the default player face while their real skin loads.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var playerHeadSkinFix = true
}
