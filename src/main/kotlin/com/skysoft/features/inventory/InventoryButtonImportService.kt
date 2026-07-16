package com.skysoft.features.inventory

import com.skysoft.config.InventoryButtonConfig
import com.skysoft.config.InventoryButtonDefaults
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.utils.gui.Rect
import java.nio.file.Path
import java.util.Locale

internal object InventoryButtonImportService {
    var discoveredSources: List<InventoryButtonImportSource> = emptyList()
        private set
    var pendingPlan: InventoryButtonImportPlan? = null
        private set
    var lastOutcome = "none"
        private set
    var lastError: String? = null
        private set
    private var undoSnapshot: InventoryButtonImportSnapshot? = null

    fun discover(): List<InventoryButtonImportSource> = recordDiscovery(discoverInventoryButtonImportSources())

    fun discover(path: Path): List<InventoryButtonImportSource> =
        recordDiscovery(discoverInventoryButtonImportSources(path))

    fun prepare(source: InventoryButtonImportSource): InventoryButtonImportPlan {
        return try {
            val read = InventoryButtonImportParser.read(source)
            planInventoryButtonImport(read, config().buttons).also {
                pendingPlan = it
                lastError = null
                lastOutcome = "prepared:${source.kind.name}:imported=${it.imported}:duplicates=${it.duplicates}:conflicts=${it.conflicts}"
            }
        } catch (exception: Exception) {
            pendingPlan = null
            lastError = exception.message ?: exception.javaClass.simpleName
            lastOutcome = "prepare_failed:${source.kind.name}"
            throw exception
        }
    }

    fun confirmMerge(): InventoryButtonImportPlan? {
        val plan = pendingPlan ?: return null
        applyPlan(plan, plan.mergedButtons)
        lastOutcome = "merged:${plan.read.source.kind.name}:imported=${plan.imported}"
        return plan
    }

    fun replaceWithImportedLayout(): InventoryButtonImportPlan? {
        val plan = pendingPlan?.takeIf(InventoryButtonImportPlan::canReplace) ?: return null
        applyPlan(plan, inventoryButtonsWithEditorSlots(plan.read.buttons))
        lastOutcome = "replaced:${plan.read.source.kind.name}:buttons=${plan.read.buttons.size}"
        return plan
    }

    fun undoImport(): InventoryButtonImportSnapshot? {
        val snapshot = undoSnapshot ?: return null
        val config = config()
        config.buttons = snapshot.buttons.map(InventoryButtonConfig::copyForImport).toMutableList()
        config.enabled = snapshot.isEnabled
        config.settings.clickType = snapshot.clickType
        config.details.tooltipDelay = snapshot.tooltipDelay
        SkysoftConfigGui.config().saveNow()
        InventoryButtonManager.clearIconCache()
        undoSnapshot = null
        lastError = null
        lastOutcome = "undone"
        return snapshot
    }

    fun cancelPendingImport(): InventoryButtonImportPlan? {
        val plan = pendingPlan ?: return null
        pendingPlan = null
        lastOutcome = "cancelled"
        return plan
    }

    private fun recordDiscovery(sources: List<InventoryButtonImportSource>): List<InventoryButtonImportSource> {
        discoveredSources = sources
        pendingPlan = null
        lastError = null
        lastOutcome = "discovered:${sources.size}"
        return sources
    }

    private fun snapshot(config: com.skysoft.config.InventoryButtonsConfig) = InventoryButtonImportSnapshot(
        config.buttons.map(InventoryButtonConfig::copyForImport).toMutableList(),
        config.enabled,
        config.settings.clickType,
        config.details.tooltipDelay,
    )

    private fun applyPlan(plan: InventoryButtonImportPlan, buttons: List<InventoryButtonConfig>) {
        val config = config()
        undoSnapshot = snapshot(config)
        config.buttons = buttons.map(InventoryButtonConfig::copyForImport).toMutableList()
        plan.read.settings.isEnabled?.let { config.enabled = it }
        plan.read.settings.clickType?.let { config.settings.clickType = it }
        plan.read.settings.tooltipDelay?.let { config.details.tooltipDelay = it }
        config.repairLoadedValues()
        SkysoftConfigGui.config().saveNow()
        InventoryButtonManager.clearIconCache()
        pendingPlan = null
        lastError = null
    }

    private fun config() = SkysoftConfigGui.config().inventory.inventoryButtons
}

internal fun planInventoryButtonImport(
    read: InventoryButtonImportReadResult,
    existingButtons: List<InventoryButtonConfig>,
): InventoryButtonImportPlan {
    val merged = existingButtons.map(InventoryButtonConfig::copyForImport).toMutableList()
    val commands = merged.filter(InventoryButtonConfig::isActive).mapTo(mutableSetOf()) { normalizedCommand(it.command) }
    var imported = 0
    var duplicates = 0
    var conflicts = 0
    read.buttons.forEach { importedButton ->
        val command = normalizedCommand(importedButton.command)
        if (!commands.add(command)) {
            duplicates++
            return@forEach
        }
        val importedBounds = importBounds(importedButton)
        if (merged.any { it.isActive() && importBounds(it).intersects(importedBounds) }) {
            commands.remove(command)
            conflicts++
            return@forEach
        }
        val inactiveSlot = merged.indexOfFirst { !it.isActive() && importBounds(it) == importedBounds }
        if (inactiveSlot >= 0) merged[inactiveSlot] = importedButton.copyForImport() else merged += importedButton.copyForImport()
        imported++
    }
    return InventoryButtonImportPlan(read, merged, imported, duplicates, conflicts)
}

private fun importBounds(button: InventoryButtonConfig): Rect = InventoryButtonCanvas(
    Rect(0, 0, IMPORT_INVENTORY_WIDTH, InventoryButtonDefaults.PLAYER_INVENTORY_HEIGHT),
    playerInventory = true,
).position(button).let { Rect(it.x, it.y, InventoryButtonManager.BUTTON_SIZE, InventoryButtonManager.BUTTON_SIZE) }

internal fun inventoryButtonsWithEditorSlots(buttons: List<InventoryButtonConfig>): List<InventoryButtonConfig> =
    buttons + InventoryButtonDefaults.create().filter { slot ->
        buttons.none { button -> importBounds(button).intersects(importBounds(slot)) }
    }

private fun normalizedCommand(command: String): String = command.trim().removePrefix("/").lowercase(Locale.ROOT)

private const val IMPORT_INVENTORY_WIDTH = 176
