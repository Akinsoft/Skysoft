package com.skysoft.config

import com.google.gson.annotations.Expose
import com.skysoft.config.core.ConfigRepairable
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class StoragePreviewsConfig : ConfigRepairable {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Enabled", desc = "Show storage contents on item tooltips.")
    @field:ConfigEditorBoolean
    var enabled = false

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Settings", desc = "Choose which storage items show previews.")
    @field:Accordion
    val settings = StoragePreviewsSettingsConfig()

    override fun repairLoadedValues() {
        settings.gridScale = settings.gridScale
            .takeIf(Float::isFinite)
            ?.coerceIn(MIN_STORAGE_PREVIEW_SCALE, MAX_STORAGE_PREVIEW_SCALE)
            ?: DEFAULT_STORAGE_PREVIEW_SCALE
    }
}

class StoragePreviewsSettingsConfig {
    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Grid Scale", desc = "Scale of items and slots in storage previews.")
    @field:ConfigEditorSlider(minValue = 0.5f, maxValue = 2f, minStep = 0.05f)
    var gridScale = DEFAULT_STORAGE_PREVIEW_SCALE

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Cake Bags", desc = "Preview New Year Cake Bags.")
    @field:ConfigEditorBoolean
    var cakeBags = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Personal Deletors", desc = "Preview Personal Deletor slots.")
    @field:ConfigEditorBoolean
    var personalDeletors = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Personal Compactors", desc = "Preview Personal Compactor slots.")
    @field:ConfigEditorBoolean
    var personalCompactors = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Backpacks", desc = "Preview backpacks and cached Storage pages.")
    @field:ConfigEditorBoolean
    var backpacks = true

    @JvmField
    @field:Expose
    @field:ConfigOption(name = "Ender Chests", desc = "Preview cached Ender Chest pages.")
    @field:ConfigEditorBoolean
    var enderChests = true
}

const val MIN_STORAGE_PREVIEW_SCALE = 0.5f

const val MAX_STORAGE_PREVIEW_SCALE = 2f

const val DEFAULT_STORAGE_PREVIEW_SCALE = 1f
