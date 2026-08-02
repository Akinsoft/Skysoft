package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import com.skysoft.config.core.repairLoadedConfigs
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class RavengardFeatureConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:Category(name = "Rarity Highlight", desc = "Highlight container items by Ravengard rarity.")
    val rarityHighlight = RarityHighlightConfig()

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Crown Values",
        desc = "Show item Crown values in the top-left of container slots.",
    )
    @field:ConfigEditorBoolean
    var showCrownValues = false

    @JvmField
    @field:Expose
    @field:ConfigOption(
        name = "Item Comparison",
        desc = "Show the equipped item beside wearable armor and accessories.",
    )
    @field:ConfigEditorBoolean
    var showItemComparison = false

    override fun repairLoadedValues() = repairLoadedConfigs(rarityHighlight)
}
