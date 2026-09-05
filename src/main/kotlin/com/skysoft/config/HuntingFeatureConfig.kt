package com.skysoft.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class HuntingFeatureConfig {
    @JvmField
    @field:Expose
    @field:Category(name = "Lotum Helper", desc = "Lotus Atoll Lotum helpers.")
    val lotumHelper = LotumHelperConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Lasso Display", desc = "Show lasso stamina and reel prompts on screen.")
    val lassoDisplay = LassoDisplayConfig()
}

class LotumHelperConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Draw a green line to clicked Lotums on Lotus Atoll.")
    @field:MainFeatureToggle
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Lotum highlight settings.")
    @field:Accordion
    val settings = LotumHelperSettingsConfig()
}

class LotumHelperSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Highlight Lotums", desc = "Highlight all Lotums on Lotus Atoll.")
    @field:ConfigEditorBoolean
    var highlightLotums = false
}
