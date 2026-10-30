package com.skysoft.config.discovery

import com.skysoft.config.SkysoftMoulConfigGuis
import com.skysoft.config.categoriesForGame
import com.skysoft.config.core.fieldOrNull
import com.skysoft.config.core.hierarchyFields
import com.skysoft.config.core.implementation
import com.skysoft.data.hypixel.SkysoftGame
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategoryImpl
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.util.LinkedHashMap

internal object NewSettingsConfigEditor {
    fun <T : Config> create(
        config: T,
        requestedOptionIds: Set<String>,
        game: SkysoftGame,
    ): MoulConfigEditor<T>? {
        val filteredCategories = filter(config, requestedOptionIds) ?: return null
        val categories = categoriesForGame(filteredCategories, game)
        if (categories.values.all { it.options.isEmpty() }) return null
        return MoulConfigEditor(categories, config)
    }

    private fun filter(config: Config, requestedOptionIds: Set<String>): LinkedHashMap<String, ProcessedCategoryImpl>? {
        val processor = SkysoftMoulConfigGuis.processConfig(config)
        val schema = NewSettingsSchema.from(processor)
        val requestedOptions = requestedOptionIds.mapNotNullTo(linkedSetOf()) { schema.byId[it]?.option }
        if (requestedOptions.isEmpty()) return null

        val includedOptions = requiredOptions(processor, schema, requestedOptions)
        val categories = processor.allCategories.mapValues { (_, category) -> category.implementation() }
        categories.values.forEach { category ->
            category.options.removeIf { it !in includedOptions }
            category.accordionAnchors.entries.removeIf { it.value !in includedOptions }
        }

        val requiredCategoryIds = requiredCategoryIds(includedOptions, processor)
        return categories.filterTo(LinkedHashMap()) { (id, _) -> id in requiredCategoryIds }
    }

    private fun requiredOptions(
        processor: MoulConfigProcessor<*>,
        schema: NewSettingsSchema,
        requestedOptions: Set<ProcessedOption>,
    ): Set<ProcessedOption> {
        val includedOptions = requestedOptions.toMutableSet()
        var previousSize: Int
        do {
            previousSize = includedOptions.size
            includedOptions.toList().forEach { option ->
                addAccordionAnchor(option, includedOptions)
                addVisibilityRequirements(option, processor, schema, includedOptions)
            }
        } while (includedOptions.size != previousSize)
        return includedOptions
    }

    private fun addAccordionAnchor(option: ProcessedOption, includedOptions: MutableSet<ProcessedOption>) {
        if (option.accordionId < 0) return
        option.category.accordionAnchors[option.accordionId]?.let(includedOptions::add)
    }

    private fun addVisibilityRequirements(
        option: ProcessedOption,
        processor: MoulConfigProcessor<*>,
        schema: NewSettingsSchema,
        includedOptions: MutableSet<ProcessedOption>,
    ) {
        val field = option.fieldOrNull() ?: return
        val condition = field.getAnnotation(ConfigVisibleIf::class.java) ?: return
        val controller = field.declaringClass.hierarchyFields()
            .firstOrNull { it.name == condition.value }
            ?.let(processor::getOptionFromField)
        if (controller != null) {
            includedOptions += controller
            return
        }

        val parentPath = option.path.substringBeforeLast('.', missingDelimiterValue = "")
        schema.descriptors
            .filter { it.path.substringBeforeLast('.', missingDelimiterValue = "") == parentPath }
            .mapTo(includedOptions, NewSettingDescriptor::option)
    }

    private fun requiredCategoryIds(
        includedOptions: Set<ProcessedOption>,
        processor: MoulConfigProcessor<*>,
    ): Set<String> {
        val categoryIds = includedOptions.mapTo(linkedSetOf()) { it.category.identifier }
        var previousSize: Int
        do {
            previousSize = categoryIds.size
            categoryIds.toList().forEach { categoryId ->
                processor.allCategories[categoryId]?.parentCategoryId?.let(categoryIds::add)
            }
        } while (categoryIds.size != previousSize)
        return categoryIds
    }
}
