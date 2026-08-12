package com.skysoft.features.profit

import com.skysoft.data.SkyBlockLocationCatalog
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorDraggableList
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigEditorSkyBlockLocations

internal class SkyBlockLocationSelection(indices: Collection<Int>) : ArrayList<Int>(indices) {
    var closeDropdown: () -> Unit = {}

    override fun contains(element: Int): Boolean {
        if (super.contains(element)) return true
        val choice = SkyBlockLocationCatalog.choices.getOrNull(element) ?: return false
        return choice.area != null && any { selectedIndex ->
            SkyBlockLocationCatalog.choices.getOrNull(selectedIndex)
                ?.let { selected -> selected.island == choice.island && selected.area == null } == true
        }
    }

    override fun add(element: Int): Boolean = super.add(element).also { added ->
        if (added && SkyBlockLocationCatalog.choices.getOrNull(element)?.area == null) closeDropdown()
    }
}

internal class SkyBlockLocationsEditor(option: ProcessedOption) :
    GuiOptionEditorDraggableList(option, SkyBlockLocationCatalog.labels, true) {
    init {
        (option.get() as? SkyBlockLocationSelection)?.closeDropdown = { closeOverlay() }
    }
}
