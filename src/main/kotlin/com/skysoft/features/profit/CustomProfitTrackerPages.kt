package com.skysoft.features.profit

import com.skysoft.config.CustomProfitTrackerConfig
import com.skysoft.config.CustomProfitTrackerLocation
import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockLocationCatalog
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.observer.Property

internal class CustomProfitTrackerEditorConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("Custom Trackers")

    override fun saveNow() = SkysoftConfigGui.config().saveNow()
}

internal class CreateCustomProfitTrackerPageConfig(
    create: (String) -> Unit,
    import: (String) -> Unit,
) : Config() {
    @JvmField
    @field:Category(name = "Create Tracker", desc = "Create or import a custom Profit Tracker.")
    val page = CreateCustomProfitTrackerPage(create, import)
}

internal class CreateCustomProfitTrackerPage(
    create: (String) -> Unit,
    import: (String) -> Unit,
) {
    @JvmField
    @field:ConfigOption(name = "Name", desc = "Set the tracker name.")
    @field:ConfigEditorText
    @field:ConfigOrder(10)
    var name = "Custom Tracker"

    @JvmField
    @field:ConfigOption(name = "Create Tracker", desc = "Create a blank custom Profit Tracker.")
    @field:ConfigEditorButton(buttonText = "Create")
    @field:ConfigOrder(20)
    val createTracker = Runnable { create(name) }

    @JvmField
    @field:ConfigOption(name = "Import Code", desc = "Paste a shared custom tracker code.")
    @field:ConfigEditorText
    @field:ConfigOrder(30)
    var importCode = ""

    @JvmField
    @field:ConfigOption(name = "Import Tracker", desc = "Create a tracker from the shared code.")
    @field:ConfigEditorButton(buttonText = "Import")
    @field:ConfigOrder(40)
    val importTracker = Runnable { import(importCode) }
}

internal class CustomProfitTrackerPageConfig(
    tracker: CustomProfitTrackerConfig,
    onShare: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) : Config() {
    @JvmField
    @field:Category(name = "Tracker", desc = "Configure this custom Profit Tracker.")
    val page = CustomProfitTrackerPage(
        tracker,
        onShare,
        onDeleteConfirmed,
    )
}

internal class CustomProfitTrackerPage(
    tracker: CustomProfitTrackerConfig,
    onShare: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    @JvmField
    val deletePending: Property<Boolean> = Property.of(false)

    @JvmField
    @field:ConfigOption(name = "Enabled", desc = "Track profit at the configured locations.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(10)
    val enabled: Property<Boolean> = Property.of(tracker.config.enabled).also { property ->
        property.addObserver { _, value -> tracker.config.enabled = value }
    }

    @JvmField
    @field:ConfigOption(name = "Name", desc = "Set the tracker name.")
    @field:ConfigEditorText
    @field:ConfigOrder(20)
    val name: Property<String> = Property.of(tracker.name).also { property ->
        property.addObserver { _, value -> tracker.name = value }
    }

    @JvmField
    @field:ConfigOption(name = "Locations", desc = "Choose where this tracker is active.")
    @field:Accordion
    @field:ConfigOrder(30)
    val locations = CustomProfitTrackerLocationsPage(tracker)

    @JvmField
    @field:ConfigOption(name = "Settings", desc = "Profit Tracker settings.")
    @field:Accordion
    @field:ConfigOrder(40)
    val settings = CustomProfitTrackerSettingsPage(tracker)

    @JvmField
    @field:ConfigOption(name = "Details", desc = "Profit Tracker appearance.")
    @field:Accordion
    @field:ConfigOrder(50)
    val details = tracker.config.details

    @JvmField
    @field:ConfigLink(owner = CustomProfitTrackerPage::class, field = "enabled")
    val position = tracker.config.position

    @JvmField
    @field:ConfigOption(name = "Share", desc = "Copy this tracker to the clipboard.")
    @field:ConfigEditorButton(buttonText = "Share")
    @field:ConfigOrder(60)
    val share = Runnable { onShare() }

    @JvmField
    @field:ConfigOption(name = "Delete Tracker", desc = "Delete this custom tracker.")
    @field:ConfigEditorButton(buttonText = "Delete")
    @field:ConfigOrder(70)
    val delete = Runnable { deletePending.set(true) }

    @JvmField
    @field:ConfigOption(name = "Confirm Delete", desc = "Delete this tracker and all of its statistics.")
    @field:ConfigEditorButton(buttonText = "Confirm")
    @field:ConfigVisibleIf("deletePending")
    @field:ConfigOrder(80)
    val confirmDelete = Runnable { onDeleteConfirmed() }

    @JvmField
    @field:ConfigOption(name = "Cancel Delete", desc = "Keep this custom tracker.")
    @field:ConfigEditorButton(buttonText = "Cancel")
    @field:ConfigVisibleIf("deletePending")
    @field:ConfigOrder(90)
    val cancelDelete = Runnable { deletePending.set(false) }
}

internal class CustomProfitTrackerSettingsPage(tracker: CustomProfitTrackerConfig) {
    @JvmField
    @field:ConfigOption(name = "Track Coins", desc = "Include purse coin gains while this tracker is active.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(10)
    val trackCoins: Property<Boolean> = Property.of(tracker.trackCoins).also { property ->
        property.addObserver { _, value -> tracker.trackCoins = value }
    }

    @JvmField
    @field:ConfigOption(name = "Price Source", desc = "Choose how tracked items are valued.")
    @field:ConfigEditorDropdown
    @field:ConfigOrder(20)
    val priceSource: Property<ProfitTrackerPriceSource> = Property.of(tracker.config.settings.priceSource).also { property ->
        property.addObserver { _, value -> tracker.config.settings.priceSource = value }
    }

    @JvmField
    @field:ConfigOption(name = "Pause After", desc = "Pause time tracking after a period without tracked activity.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(30)
    val pauseAfter: Property<Boolean> = Property.of(tracker.config.settings.pauseAfter).also { property ->
        property.addObserver { _, value -> tracker.config.settings.pauseAfter = value }
    }

    @JvmField
    @field:ConfigOption(name = "Inactivity Time", desc = "Seconds without tracked activity before time tracking pauses.")
    @field:ConfigVisibleIf("pauseAfter")
    @field:ConfigEditorSlider(minValue = 15f, maxValue = 900f, minStep = 15f)
    @field:ConfigOrder(40)
    val pauseAfterSeconds: Property<Int> = Property.of(tracker.config.settings.pauseAfterSeconds).also { property ->
        property.addObserver { _, value -> tracker.config.settings.pauseAfterSeconds = value }
    }

    @JvmField
    @field:ConfigOption(name = "Maximum Items", desc = "Maximum tracked item rows shown at once.")
    @field:ConfigEditorSlider(minValue = 1f, maxValue = 15f, minStep = 1f)
    @field:ConfigOrder(50)
    val maximumItems: Property<Int> = Property.of(tracker.config.settings.maximumItems).also { property ->
        property.addObserver { _, value -> tracker.config.settings.maximumItems = value }
    }
}

internal class CustomProfitTrackerLocationsPage(tracker: CustomProfitTrackerConfig) {
    @JvmField
    @field:ConfigOption(name = "Any Island", desc = "Use this tracker on every SkyBlock island.")
    @field:ConfigEditorBoolean
    @field:ConfigOrder(10)
    val anyIsland: Property<Boolean> = Property.of(tracker.locations.anyIsland).also { property ->
        property.addObserver { _, value -> tracker.locations.anyIsland = value }
    }

    @JvmField
    @field:ConfigOption(name = "Island Locations", desc = "Choose the islands or sub-locations where this tracker is active.")
    @field:ConfigEditorSkyBlockLocations
    @field:ConfigVisibleIf(value = "anyIsland", expected = false)
    @field:ConfigOrder(20)
    val islandLocations: Property<MutableList<Int>> = Property.of<MutableList<Int>>(
        SkyBlockLocationCatalog.choices.mapIndexedNotNull { index, choice ->
            val location = tracker.locations.entries.firstOrNull { it.island == choice.island.name }
                ?: return@mapIndexedNotNull null
            index.takeIf {
                choice.area == null && location.areas.isEmpty() ||
                    choice.area != null && choice.area in location.areas
            }
        }.let(::SkyBlockLocationSelection),
    ).also { property ->
        property.addObserver { _, selectedIndices ->
            val selected = selectedIndices.distinct().mapNotNull { index ->
                SkyBlockLocationCatalog.choices.getOrNull(index)?.let { index to it }
            }
            val newestByIsland = selected.associate { (_, choice) -> choice.island to choice }
            val normalized = selected.filter { (_, choice) ->
                val newest = newestByIsland.getValue(choice.island)
                newest.area == null && choice.area == null || newest.area != null && choice.area != null
            }
            val normalizedIndices = normalized.map { it.first }
            if (selectedIndices != normalizedIndices) {
                selectedIndices.clear()
                selectedIndices.addAll(normalizedIndices)
            }
            tracker.locations.entries.clear()
            normalized.groupBy { (_, choice) -> choice.island }.forEach { (island, choices) ->
                tracker.locations.entries += CustomProfitTrackerLocation(
                    island.name,
                    choices.mapNotNull { (_, choice) -> choice.area }.toMutableList(),
                )
            }
        }
    }
}
