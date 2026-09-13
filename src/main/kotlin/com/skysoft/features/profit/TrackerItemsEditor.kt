package com.skysoft.features.profit

import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.utils.TextUtilities.removeColor
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorDraggableList
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigEditorTrackerItems

internal class TrackerItemSelection(private val items: MutableList<String>) : AbstractMutableList<Int>() {
    private var choices = emptyList<String>()
    private var indices = emptyMap<String, Int>()
    var itemIdsByLabel: Map<String, String> = emptyMap()
        private set

    fun refreshChoices(): Array<String> {
        val names = SkyBlockDataRepository.entries
            .filter { it.key.kind == ItemListEntryKind.SKYBLOCK }
            .associate { it.key.id to it.formattedDisplayName }
        choices = (names.keys + items).toList()
        indices = choices.withIndex().associate { it.value to it.index }
        val duplicateNames = choices.groupingBy { (names[it] ?: it).removeColor() }.eachCount()
        val labels = choices.map { id ->
            val name = names[id] ?: id
            val suffix = if (duplicateNames.getValue(name.removeColor()) > 1) " §8($id)" else ""
            "   $name$suffix"
        }
        itemIdsByLabel = labels.withIndex().associate { (index, label) -> label.removeColor() to choices[index] }
        return labels.toTypedArray()
    }

    override val size: Int get() = items.size

    override fun get(index: Int): Int = indices.getValue(items[index])

    override fun add(index: Int, element: Int) {
        items.add(index, choices[element])
    }

    override fun removeAt(index: Int): Int = indices.getValue(items.removeAt(index))

    override fun set(index: Int, element: Int): Int = indices.getValue(items.set(index, choices[element]))
}

internal class TrackerItemsEditor(option: ProcessedOption) : GuiOptionEditor(option) {
    private val selection = option.get() as TrackerItemSelection
    private var version = Long.MIN_VALUE
    private var editor: GuiOptionEditorDraggableList? = null
    private var guiContext: GuiContext? = null

    private fun currentEditor(): GuiOptionEditorDraggableList {
        val currentVersion = SkyBlockDataRepository.snapshotVersion
        if (editor == null || version != currentVersion) {
            editor = GuiOptionEditorDraggableList(option, selection.refreshChoices(), true).also {
                guiContext?.let(it::setGuiContext)
            }
            version = currentVersion
        }
        return requireNotNull(editor).also { it.activeConfigGUI = activeConfigGUI }
    }

    override fun getHeight(): Int = currentEditor().height

    override fun setGuiContext(guiContext: GuiContext) {
        this.guiContext = guiContext
        currentEditor().setGuiContext(guiContext)
    }

    override fun render(context: RenderContext, x: Int, y: Int, width: Int) =
        currentEditor().render(TrackerItemIconRenderContext(context, selection.itemIdsByLabel), x, y, width)

    override fun renderOverlay(context: RenderContext, x: Int, y: Int, width: Int) =
        currentEditor().renderOverlay(
            TrackerItemIconRenderContext(context, selection.itemIdsByLabel), x, y, width,
        )

    override fun keyboardInput(event: KeyboardEvent): Boolean = currentEditor().keyboardInput(event)

    override fun mouseInput(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent): Boolean =
        currentEditor().mouseInput(x, y, width, mouseX, mouseY, event)

    override fun mouseInputOverlay(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent): Boolean =
        currentEditor().mouseInputOverlay(x, y, width, mouseX, mouseY, event)

    override fun fulfillsSearch(word: String): Boolean = currentEditor().fulfillsSearch(word)
}
