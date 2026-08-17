package com.skysoft.config

import com.skysoft.data.hypixel.SkysoftGame
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategoryImpl
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import java.lang.reflect.Field
import java.util.LinkedHashMap

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigGames(vararg val value: SkysoftGame)

internal fun <T : ProcessedCategory> categoriesForGame(
    categories: LinkedHashMap<String, T>,
    game: SkysoftGame,
): LinkedHashMap<String, T> {
    fun supportsGame(category: ProcessedCategory): Boolean {
        val declaredGames = category.configGamesOrNull()
        val parentId = category.parentCategoryId
        return if (parentId == null) {
            game in requireNotNull(declaredGames) {
                "${category.reflectField().declaringClass.name}.${category.reflectField().name} must declare @ConfigGames"
            }
        } else {
            val parent = requireNotNull(categories[parentId]) { "Missing parent config category $parentId" }
            supportsGame(parent) && (declaredGames == null || game in declaredGames)
        }
    }

    return LinkedHashMap<String, T>().apply {
        categories.forEach { (id, category) ->
            if (!supportsGame(category)) return@forEach
            val implementation = category as? ProcessedCategoryImpl ?: error(
                "SoftConfig returned an unsupported category implementation: ${category.javaClass.name}",
            )
            implementation.options.removeIf { option -> !option.supportsGame(game) }
            implementation.accordionAnchors.entries.removeIf { it.value !in implementation.options }
            put(id, category)
        }
        entries.firstOrNull { (_, category) ->
            category.parentCategoryId == null && category.displayName.text == "Settings"
        }?.let { (id, category) ->
            remove(id)
            put(id, category)
        }
    }
}

private fun ProcessedCategory.reflectField(): Field {
    require(this is ProcessedCategoryImpl) {
        "SoftConfig returned an unsupported category implementation: ${javaClass.name}"
    }
    return reflectField
}

private fun ProcessedCategory.configGamesOrNull(): Array<out SkysoftGame>? =
    reflectField().getAnnotation(ConfigGames::class.java)?.value

private fun ProcessedOption.supportsGame(game: SkysoftGame): Boolean =
    (this as? ProcessedOption.HasField)
        ?.field
        ?.getAnnotation(ConfigGames::class.java)
        ?.value
        ?.contains(game)
        ?: true
