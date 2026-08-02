package com.skysoft.config

import com.skysoft.data.hypixel.SkysoftGame
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.processor.BuiltinMoulConfigGuis
import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor

object SkysoftMoulConfigGuis {
    fun <T : Config> createEditor(config: T): MoulConfigEditor<T> {
        return MoulConfigEditor(processConfig(config))
    }

    fun createEditor(config: SkysoftConfig, game: SkysoftGame): MoulConfigEditor<SkysoftConfig> {
        val processor = processConfig(config)
        return MoulConfigEditor(categoriesForGame(processor.allCategories, game), config)
    }

    fun <T : Config> processConfig(config: T): MoulConfigProcessor<T> {
        val processor = MoulConfigProcessor(config)
        addProcessors(processor)
        ConfigProcessorDriver(processor).apply { warnForPrivateFields = false }.processConfig(config)
        processor.requireFinalized()
        return processor
    }

    fun addProcessors(processor: MoulConfigProcessor<*>) {
        BuiltinMoulConfigGuis.addProcessors(processor)
        processor.registerConfigEditor(ConfigEditorUpdate::class.java) { option, _ ->
            SkysoftUpdateEditor(option)
        }
    }
}
