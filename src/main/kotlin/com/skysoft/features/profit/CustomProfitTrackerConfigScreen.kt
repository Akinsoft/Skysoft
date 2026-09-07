package com.skysoft.features.profit

import com.skysoft.config.CustomProfitTrackerConfig
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.SearchTag
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.Type
import java.util.LinkedHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object CustomProfitTrackerConfigScreen {
    fun open() = openEditor(null)

    fun open(trackerId: String) = openEditor(trackerId)

    private fun openEditor(selectedTrackerId: String?) {
        val parent = MinecraftClient.screen()
        MinecraftClient.setScreen(CustomProfitTrackerEditor(selectedTrackerId).createScreen(parent))
    }
}

private class CustomProfitTrackerEditor(selectedTrackerId: String?) {
    private val categories = LinkedHashMap<String, ProcessedCategory>().apply {
        put(
            CREATE_CATEGORY_ID,
            runtimeCategory(
                CreateCustomProfitTrackerPageConfig(::createTracker, ::importTracker),
                CREATE_CATEGORY_ID,
                { "Create Tracker" },
                "Create or import a custom Profit Tracker.",
            ),
        )
        customTrackers().forEach { tracker -> put(categoryId(tracker.id), trackerCategory(tracker)) }
    }
    private val editor = MoulConfigEditor(categories, CustomProfitTrackerEditorConfig()).apply {
        val selected = selectedTrackerId?.let { categories[categoryId(it)] } ?: categories[CREATE_CATEGORY_ID]
        selected?.let(::setSelectedCategory)
    }

    fun createScreen(parent: Screen?): Screen = object : MoulConfigScreenComponent(
        Component.empty(),
        GuiContext(GuiElementComponent(editor)),
        parent,
    ) {
        override fun removed() {
            super.removed()
            repairAndSave()
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

private fun runtimeCategory(
    config: Config,
    id: String,
    name: () -> String,
    description: String,
): ProcessedCategory {
    val source = SkysoftMoulConfigGuis.processConfig(config).allCategories.values.single()
    return RuntimeProcessedCategory(id, name, description, source)
}

private class RuntimeProcessedCategory(
    private val id: String,
    private val name: () -> String,
    private val description: String,
    source: ProcessedCategory,
) : ProcessedCategory {
    private val optionValues: List<ProcessedOption>
    private val anchorValues: Map<Int, ProcessedOption>

    init {
        val wrapped = source.options.associateWith { option -> RuntimeProcessedOption(option, this, id) }
        optionValues = source.options.map(wrapped::getValue)
        anchorValues = source.accordionAnchors.mapValues { (_, option) -> wrapped.getValue(option) }
    }

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
