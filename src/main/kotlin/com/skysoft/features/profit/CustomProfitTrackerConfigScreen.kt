package com.skysoft.features.profit

import com.skysoft.config.CustomProfitTrackerConfig
import com.skysoft.config.CustomProfitTrackerLocation
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.data.SkyBlockLocationCatalog
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.annotations.SearchTag
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.observer.Property
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.Type
import java.util.LinkedHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object CustomProfitTrackerConfigScreen {
    private lateinit var categories: LinkedHashMap<String, ProcessedCategory>
    private lateinit var editor: MoulConfigEditor<CustomProfitTrackerEditorConfig>

    fun open() {
        show(MinecraftClient.screen(), null)
    }

    fun open(trackerId: String) {
        show(MinecraftClient.screen(), trackerId)
    }

    private fun show(parent: Screen?, selectedTrackerId: String?) {
        MinecraftClient.setScreen(createScreen(parent, selectedTrackerId))
    }

    private fun createScreen(parent: Screen?, selectedTrackerId: String?): Screen {
        val shell = CustomProfitTrackerEditorConfig()
        categories = LinkedHashMap()
        categories[CREATE_CATEGORY_ID] = runtimeCategory(
            CreateCustomProfitTrackerPageConfig(::createTracker, ::importTracker),
            CREATE_CATEGORY_ID,
            { "Create Tracker" },
            "Create or import a custom Profit Tracker.",
        )
        customTrackers().forEach { tracker ->
            categories[categoryId(tracker.id)] = trackerCategory(tracker)
        }
        editor = MoulConfigEditor(categories, shell).apply {
            val selected = selectedTrackerId?.let { categories[categoryId(it)] } ?: categories[CREATE_CATEGORY_ID]
            selected?.let(::setSelectedCategory)
        }
        return object : MoulConfigScreenComponent(
            Component.empty(),
            GuiContext(GuiElementComponent(editor)),
            parent,
        ) {
            override fun removed() {
                super.removed()
                repairAndSave()
            }
        }
    }

    private fun createTracker(name: String) {
        val tracker = CustomProfitTrackerConfig(name = name)
        customTrackers() += tracker
        repairAndSave()
        addTrackerCategory(tracker)
    }

    private fun importTracker(code: String) {
        val tracker = CustomProfitTrackerSharing.decode(code)
        if (tracker == null) {
            SkysoftChat.chat("The custom tracker code is not valid.")
            return
        }
        customTrackers() += tracker
        repairAndSave()
        addTrackerCategory(tracker)
    }

    private fun shareTracker(id: String) {
        repairAndSave()
        val tracker = customTrackers().firstOrNull { it.id == id } ?: return
        Minecraft.getInstance().keyboardHandler.setClipboard(CustomProfitTrackerSharing.encode(tracker))
        SkysoftChat.chat("Copied ${tracker.name} to the clipboard.")
    }

    private fun deleteTracker(id: String) {
        val tracker = customTrackers().firstOrNull { it.id == id } ?: return
        ProfitTracker.deleteCustomTrackerData(ProfitTrackerTarget.custom(id))
        customTrackers().remove(tracker)
        repairAndSave()
        categories.remove(categoryId(id))?.let { removed -> editor.allOptions.removeAll(removed.options.toSet()) }
        editor.updateSearchResults()
        editor.setSelectedCategory(categories.getValue(CREATE_CATEGORY_ID))
    }

    private fun addTrackerCategory(tracker: CustomProfitTrackerConfig) {
        val category = trackerCategory(tracker)
        categories[category.identifier] = category
        editor.allOptions.addAll(category.options)
        category.options.forEach { option -> option.editor.activeConfigGUI = editor }
        editor.updateSearchResults()
        editor.setSelectedCategory(category)
    }

    private fun trackerCategory(tracker: CustomProfitTrackerConfig): ProcessedCategory = runtimeCategory(
        CustomProfitTrackerPageConfig(
            tracker,
            onShare = { shareTracker(tracker.id) },
            onDeleteConfirmed = { deleteTracker(tracker.id) },
        ),
        categoryId(tracker.id),
        { tracker.name.ifBlank { "Unnamed Tracker" } },
        "Configure ${tracker.name.ifBlank { "this custom tracker" }}.",
    )

    private fun repairAndSave() {
        SkysoftConfigGui.config().profitTrackers.custom.repairLoadedValues()
        SkysoftConfigGui.config().saveNow()
    }

    private fun customTrackers(): MutableList<CustomProfitTrackerConfig> =
        SkysoftConfigGui.config().profitTrackers.custom.trackers
}

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
    val settings = tracker.config.settings

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

private fun runtimeCategory(
    config: Config,
    id: String,
    name: () -> String,
    description: String,
): ProcessedCategory {
    val source = SkysoftMoulConfigGuis.processConfig(config).allCategories.values.single()
    val category = RuntimeProcessedCategory(id, name, description)
    val wrapped = source.options.associateWith { option -> RuntimeProcessedOption(option, category, id) }
    category.optionValues = source.options.map(wrapped::getValue)
    category.anchorValues = source.accordionAnchors.mapValues { (_, option) -> wrapped.getValue(option) }
    return category
}

private class RuntimeProcessedCategory(
    private val id: String,
    private val name: () -> String,
    private val description: String,
) : ProcessedCategory {
    lateinit var optionValues: List<ProcessedOption>
    lateinit var anchorValues: Map<Int, ProcessedOption>

    override fun getDisplayName(): StructuredText = StructuredText.of(name())
    override fun getDescription(): StructuredText = StructuredText.of(description)
    override fun getIdentifier(): String = id
    override fun getParentCategoryId(): String? = null
    override fun getOptions(): List<ProcessedOption> = optionValues
    override fun getAccordionAnchors(): Map<Int, ProcessedOption> = anchorValues
    override fun getDebugDeclarationLocation(): String = "custom Profit Tracker category $id"
}

private class RuntimeProcessedOption(
    private val delegate: ProcessedOption,
    private val category: ProcessedCategory,
    private val prefix: String,
) : ProcessedOption {
    override fun getSearchTags(): Array<SearchTag> = delegate.searchTags
    override fun getAccordionId(): Int = delegate.accordionId
    override fun getEditor(): GuiOptionEditor = delegate.editor
    override fun getCategory(): ProcessedCategory = category
    override fun getName(): StructuredText = delegate.name
    override fun getDescription(): StructuredText = delegate.description
    override fun getPath(): String = "$prefix.${delegate.path}"
    override fun getConfig(): Config = delegate.config
    override fun get(): Any? = delegate.get()
    override fun getType(): Type = delegate.type
    override fun set(value: Any?): Boolean = delegate.set(value)
    override fun isVisible(): Boolean = delegate.isVisible
    override fun explicitNotifyChange() = delegate.explicitNotifyChange()
    override fun getDebugDeclarationLocation(): String = requireNotNull(delegate.debugDeclarationLocation)
}

private fun categoryId(trackerId: String): String = "tracker:$trackerId"

private const val CREATE_CATEGORY_ID = "create"
