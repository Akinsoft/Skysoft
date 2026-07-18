package com.skysoft.config

import com.skysoft.config.discovery.NewSettingsConfigEditor
import com.skysoft.config.discovery.NewSettingsEditor
import com.skysoft.utils.MinecraftClient
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

object SkysoftConfigGui {
    private val config = SkysoftConfig.load()
    private var editor: MoulConfigEditor<SkysoftConfig>? = null

    fun open(search: String? = null) {
        val currentEditor = editor()
        val query = configSearchQuery(search)
        val matchingCategory = currentEditor.allCategories.values
            .firstOrNull { it.displayName.text.equals(query, ignoreCase = true) }
        if (matchingCategory != null) {
            currentEditor.search("")
            currentEditor.setSelectedCategory(matchingCategory)
        } else {
            currentEditor.search(query)
            selectSearchResult(currentEditor, query)
        }
        MinecraftClient.setScreen(createScreen(null))
    }

    fun createScreen(parent: Screen?): Screen =
        createScreen(parent, editor())

    internal fun openNewSettings(optionIds: Set<String>): NewSettingsEditor<SkysoftConfig>? {
        val filteredEditor = NewSettingsConfigEditor.create(config, optionIds) ?: return null
        MinecraftClient.setScreen(createScreen(null, filteredEditor.editor))
        return filteredEditor
    }

    private fun createScreen(parent: Screen?, configEditor: MoulConfigEditor<SkysoftConfig>): Screen =
        object : MoulConfigScreenComponent(
            Component.empty(),
            GuiContext(GuiElementComponent(configEditor)),
            parent,
        ) {
            override fun removed() {
                super.removed()
                config.saveNow()
            }
        }

    fun config(): SkysoftConfig = config

    private fun editor(): MoulConfigEditor<SkysoftConfig> {
        val currentEditor = editor
        if (currentEditor != null) {
            return currentEditor
        }

        return SkysoftMoulConfigGuis.createEditor(config).also { editor = it }
    }
}

internal fun configSearchQuery(search: String?): String = search.orEmpty()

internal fun <T : Config> selectSearchResult(editor: MoulConfigEditor<T>, query: String) {
    val matchingOption = matchingSearchOption(editor.allOptions, query) ?: return
    editor.goToOption(matchingOption)
}

internal fun matchingSearchOption(options: Iterable<ProcessedOption>, query: String): ProcessedOption? {
    if (query.isBlank()) return null
    val words = query.trim().lowercase(Locale.ROOT).split(Regex(" +"))
    return options.firstOrNull { option ->
        words.all(option.editor::fulfillsSearch)
    }
}
