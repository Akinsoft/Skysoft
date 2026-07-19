package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.data.skyblock.SkyBlockRarity
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class DroppedItemScalingConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Scale dropped SkyBlock items by rarity.")
    @field:ConfigEditorBoolean
    var isEnabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Dropped item sizes by rarity.")
    @field:Accordion
    val settings = DroppedItemScalingSettingsConfig()

    fun repairLoadedValues() {
        settings.repairLoadedValues()
    }
}

class DroppedItemScalingSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Common Size", desc = "Dropped Common item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var commonSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Uncommon Size", desc = "Dropped Uncommon item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var uncommonSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Rare Size", desc = "Dropped Rare item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var rareSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Epic Size", desc = "Dropped Epic item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var epicSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Legendary Size", desc = "Dropped Legendary item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var legendarySize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Mythic Size", desc = "Dropped Mythic item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var mythicSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Divine Size", desc = "Dropped Divine item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var divineSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Special Size", desc = "Dropped Special item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var specialSize = DEFAULT_SIZE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Very Special Size", desc = "Dropped Very Special item size. 100 matches the default.")
    @field:ConfigEditorSlider(minValue = MIN_SIZE.toFloat(), maxValue = MAX_SIZE.toFloat(), minStep = SIZE_STEP.toFloat())
    var verySpecialSize = DEFAULT_SIZE

    fun sizePercentFor(rarity: SkyBlockRarity): Int = when (rarity) {
        SkyBlockRarity.COMMON -> commonSize
        SkyBlockRarity.UNCOMMON -> uncommonSize
        SkyBlockRarity.RARE -> rareSize
        SkyBlockRarity.EPIC -> epicSize
        SkyBlockRarity.LEGENDARY -> legendarySize
        SkyBlockRarity.MYTHIC -> mythicSize
        SkyBlockRarity.DIVINE -> divineSize
        SkyBlockRarity.SPECIAL -> specialSize
        SkyBlockRarity.VERY_SPECIAL -> verySpecialSize
    }

    fun repairLoadedValues() {
        commonSize = repairSize(commonSize)
        uncommonSize = repairSize(uncommonSize)
        rareSize = repairSize(rareSize)
        epicSize = repairSize(epicSize)
        legendarySize = repairSize(legendarySize)
        mythicSize = repairSize(mythicSize)
        divineSize = repairSize(divineSize)
        specialSize = repairSize(specialSize)
        verySpecialSize = repairSize(verySpecialSize)
    }

    companion object {
        const val DEFAULT_SIZE = 100
        const val MIN_SIZE = 25
        const val MAX_SIZE = 1600
        const val SIZE_STEP = 5

        private fun repairSize(size: Int): Int = size.coerceIn(MIN_SIZE, MAX_SIZE)
    }
}
