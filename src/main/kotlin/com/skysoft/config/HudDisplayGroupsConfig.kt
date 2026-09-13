package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class PlayerHudConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Selected Item Name", desc = "Customize the item name shown above the hotbar.")
    val selectedItemName = SelectedItemNameConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Action Bar", desc = "Customize the action bar position and visuals.")
    val actionBar = SkysoftActionBarConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Skill EXP Display", desc = "Move Skill EXP gains out of the action bar.")
    val skillExpDisplay = SkillExpDisplayConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Inventory HUD", desc = "Show your inventory, armor, and equipment in-game.")
    val inventoryHud = InventoryHudConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Custom Bars", desc = "Replace SkyBlock status displays with custom bars.")
    val customBars = CustomBarsConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:ConfigOption(
        name = "SkyBlock Level Bar",
        desc = "Show your SkyBlock Level and progress on Minecraft's experience bar.",
    )
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var isSkyBlockLevelBarEnabled = false

    override fun repairLoadedValues() {
        selectedItemName.repairLoadedValues()
        actionBar.repairLoadedValues()
        inventoryHud.repairLoadedValues()
        customBars.repairLoadedValues()
    }
}

class InformationDisplaysConfig {
    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Party Display", desc = "Show your current party on screen.")
    val partyDisplay = PartyDisplayConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Day Display", desc = "Show the current Minecraft day.")
    val dayDisplay = DayDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Spotify Display", desc = "Show your current Spotify playback.")
    val spotifyDisplay = SpotifyDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Real Time Display", desc = "Show your local time.")
    val realTimeDisplay = RealTimeDisplayConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Server Info Display", desc = "Show FPS, TPS, and ping.")
    val serverInfoDisplay = ServerInfoDisplayConfig()
}
