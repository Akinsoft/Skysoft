package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class StorageFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Storage Overlay", desc = "Browse SkyBlock storage in a searchable overlay.")
    val storageOverlay = StorageOverlayConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Storage Previews", desc = "Preview the contents of SkyBlock storage items.")
    val storagePreviews = StoragePreviewsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Sack Display", desc = "Show sack contents beside open sack menus.")
    val sackDisplay = SackDisplayConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Sacks Tracker", desc = "Show selected sack item quantities on a movable HUD.")
    val sackHud = SackHudConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Hide Sacks Messages", desc = "Hide item transfer summaries from Sacks.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var hideSacksMessages = false

    override fun repairLoadedValues() = repairLoadedConfigs(storageOverlay, storagePreviews, sackDisplay, sackHud)
}
