package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.features.pets.PetFeatureConfig
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class MiscFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Block Overlay", desc = "Customize the targeted block highlight.")
    val blockOverlay = BlockOverlayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Pets", desc = "Pet display and storage settings.")
    val pets = PetFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Rare Loot Sharing", desc = "Share valuable drops in party chat.")
    val rareLootSharing = RareLootSharingConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Auto Sprint", desc = "Auto sprinting.")
    @field:ConfigEditorBoolean
    var autoSprint = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Dead Entities",
        desc = "Hide entities during their death animation.",
    )
    @field:ConfigEditorBoolean
    var hideDeadEntities = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Solid Tooltip Background", desc = "Make tooltip backgrounds fully opaque.")
    @field:ConfigEditorBoolean
    var solidTooltipBackground = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Keep SkyBlock Resource Pack",
        desc = "Keep Hypixel's SkyBlock resource pack loaded between servers.",
    )
    @field:ConfigEditorBoolean
    var keepSkyBlockResourcePack = true

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Hide Vanilla Recipe Book",
        desc = "Hide the vanilla recipe book in your inventory on SkyBlock.",
    )
    @field:ConfigEditorBoolean
    var hideVanillaRecipeBook = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "No Hurt Cam", desc = "Stops the camera from shaking when taking damage.")
    @field:ConfigEditorBoolean
    var noHurtCam = false
}

class RareLootSharingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Share valuable drops in party chat.")
    @field:ConfigEditorBoolean
    var enabled = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Rare loot sharing settings.")
    @field:Accordion
    val settings = RareLootSharingSettingsConfig()
}

class RareLootSharingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rare Loot Value", desc = "Minimum coin value to share.")
    @field:ConfigEditorText
    var rareLootValue = "1,000,000"
}
