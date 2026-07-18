package com.skysoft.config.discovery

import com.skysoft.config.SkysoftMoulConfigGuis
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.ConfigVisibleIf
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategoryImpl
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.Field
import java.util.LinkedHashMap

internal data class NewSettingsEditor<T : Config>(
    val editor: MoulConfigEditor<T>,
    val requestedOptionCount: Int,
    val includedOptionCount: Int,
    val includedCategoryCount: Int,
    val includedOptionPaths: List<String>,
)

internal data class NewSettingsFilter<T : Config>(
    val config: T,
    val categories: LinkedHashMap<String, ProcessedCategoryImpl>,
    val requestedOptionCount: Int,
    val includedOptionCount: Int,
) {
    val includedCategoryCount: Int = categories.size
    val options: List<ProcessedOption> = categories.values.flatMap { it.options }
    val includedOptionPaths: List<String> = options.map { it.path }
}

internal object NewSettingsConfigEditor {
    fun <T : Config> create(config: T, requestedOptionIds: Set<String>): NewSettingsEditor<T>? {
        val filter = filter(config, requestedOptionIds) ?: return null
        return NewSettingsEditor(
            editor = MoulConfigEditor(filter.categories, config),
            requestedOptionCount = filter.requestedOptionCount,
            includedOptionCount = filter.includedOptionCount,
            includedCategoryCount = filter.includedCategoryCount,
            includedOptionPaths = filter.includedOptionPaths,
        )
    }

    fun <T : Config> filter(config: T, requestedOptionIds: Set<String>): NewSettingsFilter<T>? {
        val processor = SkysoftMoulConfigGuis.processConfig(config)
        val schema = NewSettingsSchema.from(processor)
        val requestedOptions = requestedOptionIds.mapNotNullTo(linkedSetOf()) { schema.byId[it]?.option }
        if (requestedOptions.isEmpty()) return null

        val includedOptions = requiredOptions(processor, schema, requestedOptions)
        val categories = processor.allCategories.values.map { category ->
            require(category is ProcessedCategoryImpl) {
                "SoftConfig returned an unsupported category implementation: ${category.javaClass.name}"
            }
            category
        }
        categories.forEach { category ->
            category.options.removeIf { it !in includedOptions }
            category.accordionAnchors.entries.removeIf { it.value !in includedOptions }
        }

        val requiredCategoryIds = requiredCategoryIds(includedOptions, processor)
        val filteredCategories = LinkedHashMap<String, ProcessedCategoryImpl>()
        processor.allCategories.forEach { (id, category) ->
            if (id in requiredCategoryIds) filteredCategories[id] = category as ProcessedCategoryImpl
        }
        return NewSettingsFilter(
            config = config,
            categories = filteredCategories,
            requestedOptionCount = requestedOptions.size,
            includedOptionCount = includedOptions.size,
        )
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
        val controller = findField(field.declaringClass, condition.value)?.let(processor::getOptionFromField)
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

    private fun findField(type: Class<*>, name: String): Field? {
        var currentType: Class<*>? = type
        while (currentType != null) {
            runCatching { currentType.getDeclaredField(name) }.getOrNull()?.let { return it }
            currentType = currentType.superclass
        }
        return null
    }
}

internal fun ProcessedOption.fieldOrNull(): Field? = (this as? ProcessedOption.HasField)?.field
