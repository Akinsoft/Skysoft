package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class FixesConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Menu Drop Fix",
        desc = "Prevent the SkyBlock Menu from opening when dropping hovered inventory items.\n§cUse at your own risk.",
    )
    @field:ConfigEditorBoolean
    var preventSkyBlockMenuOpeningOnInventoryDrop = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Held Item Update Fix",
        desc = "Prevent same-item SkyBlock data updates from replaying the hand-swap animation or resetting block breaking.",
    )
    @field:ConfigEditorBoolean
    var isHeldItemUpdateFixEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Glitch Mobs",
        desc = "Hide nametagless rare mob player models left behind by Hypixel.",
    )
    @field:ConfigEditorBoolean
    var hideGlitchMobs = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Bugged Nameplates",
        desc = "Hide bugged floating nameplates left behind by Hypixel.",
    )
    @field:ConfigEditorBoolean
    var hideBuggedNameplates = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Player Head Skin Fix",
        desc = "Stops custom player heads from flashing the default player face while their real skin loads.",
    )
    @field:ConfigEditorBoolean
    var playerHeadSkinFix = true
}
