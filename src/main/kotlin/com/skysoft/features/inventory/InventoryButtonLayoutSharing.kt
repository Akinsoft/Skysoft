package com.skysoft.features.inventory

import com.google.gson.GsonBuilder
import com.skysoft.config.InventoryButtonConfig
import com.skysoft.utils.serialization.ShareableConfigCodec

internal object InventoryButtonLayoutSharing {
    fun export(buttons: List<InventoryButtonConfig>): String {
        val layout = SharedInventoryButtonLayout(buttons.mapTo(mutableListOf(), InventoryButtonConfig::copy))
        return ShareableConfigCodec.encode(TYPE, SCHEMA_VERSION, GSON.toJson(layout))
    }

    fun import(value: String): MutableList<InventoryButtonConfig> {
        val envelope = ShareableConfigCodec.decode(value)
        require(envelope.type == TYPE) { "Clipboard contains a different Skysoft configuration." }
        require(envelope.schemaVersion == SCHEMA_VERSION) { "This Inventory Buttons configuration is not supported." }
        val layout = runCatching {
            GSON.fromJson(envelope.payload, SharedInventoryButtonLayout::class.java)
        }.getOrElse {
            throw IllegalArgumentException("Inventory Buttons configuration is not valid.", it)
        } ?: throw IllegalArgumentException("Inventory Buttons configuration is not valid.")
        require(layout.buttons.size <= MAX_BUTTONS) { "Inventory Buttons configuration has too many buttons." }
        layout.buttons.forEach { button ->
            require(button.command.length <= MAX_COMMAND_LENGTH) { "An inventory button command is too long." }
            require((button.icon?.length ?: 0) <= MAX_ICON_LENGTH) { "An inventory button icon is too long." }
            button.repairLoadedValues()
        }
        return layout.buttons.mapTo(mutableListOf()) { it.copy() }
    }

    private data class SharedInventoryButtonLayout(
        val buttons: MutableList<InventoryButtonConfig>,
    )

    private val GSON = GsonBuilder().disableHtmlEscaping().create()
    private const val TYPE = "inventory_buttons"
    private const val SCHEMA_VERSION = 1
    private const val MAX_BUTTONS = 512
    private const val MAX_COMMAND_LENGTH = 128
    private const val MAX_ICON_LENGTH = 256
}
