package com.skysoft.features.inventory

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.literal
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.stringArgument
import java.nio.file.InvalidPathException
import java.nio.file.Path
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

internal object InventoryButtonImportCommand {
    fun command(openEditor: () -> Int): LiteralArgumentBuilder<FabricClientCommandSource> =
        literal("buttons")
            .executes { openEditor() }
            .then(
                literal("import")
                    .executes { discover(it.source) }
                    .then(literal("source").then(sourceArgument()))
                    .then(literal("path").then(pathArgument()))
                    .then(literal("confirm").executes { confirm(it.source, openEditor) })
                    .then(literal("replace").executes { replace(it.source, openEditor) })
                    .then(literal("cancel").executes { cancel(it.source) })
                    .then(literal("undo").executes { undo(it.source, openEditor) }),
            )

    private fun discover(source: FabricClientCommandSource): Int =
        showDiscoveredSources(source, InventoryButtonImportService.discover())

    private fun sourceArgument(): RequiredArgumentBuilder<FabricClientCommandSource, Int> =
        RequiredArgumentBuilder.argument<FabricClientCommandSource, Int>("number", IntegerArgumentType.integer(1))
            .executes { context ->
                val number = IntegerArgumentType.getInteger(context, "number")
                val selected = InventoryButtonImportService.discoveredSources.getOrNull(number - 1)
                if (selected == null) {
                    SkysoftChat.error(context.source, "Import source $number is unavailable. Run /skysoft buttons import again.")
                    return@executes 0
                }
                prepare(context.source, selected)
            }

    private fun pathArgument(): RequiredArgumentBuilder<FabricClientCommandSource, String> =
        stringArgument("folder-or-file").executes { context ->
            val raw = StringArgumentType.getString(context, "folder-or-file").trim().removeSurrounding("\"")
            val path = try {
                Path.of(raw)
            } catch (_: InvalidPathException) {
                SkysoftChat.error(context.source, "That import path is invalid.")
                return@executes 0
            }
            showDiscoveredSources(context.source, InventoryButtonImportService.discover(path))
        }

    private fun showDiscoveredSources(
        source: FabricClientCommandSource,
        sources: List<InventoryButtonImportSource>,
    ): Int {
        if (sources.isEmpty()) {
            SkysoftChat.error(source, "No NEU or Firmament inventory button config was found.")
            SkysoftChat.feedback(source, "Use /skysoft buttons import path <old profile folder> to choose one manually.")
            return 0
        }
        if (sources.size == 1) return prepare(source, sources.single())
        SkysoftChat.feedback(source, "Found ${sources.size} inventory button configs. Choose one:")
        sources.forEachIndexed { index, importSource ->
            val command = "/skysoft buttons import source ${index + 1}"
            val line = Component.literal("[${index + 1}] ${importSource.kind.displayName} — ${importSource.profileName}")
                .withStyle {
                    it.withColor(ChatFormatting.AQUA)
                        .withClickEvent(ClickEvent.RunCommand(command))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal(importSource.path.toString())))
                }
            SkysoftChat.feedback(source, line)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun prepare(source: FabricClientCommandSource, importSource: InventoryButtonImportSource): Int {
        val plan = try {
            InventoryButtonImportService.prepare(importSource)
        } catch (exception: Exception) {
            SkysoftChat.error(source, exception.message ?: "Could not read the selected config.")
            return 0
        }
        val read = plan.read
        SkysoftChat.feedback(source, "${importSource.kind.displayName} — ${importSource.profileName}")
        SkysoftChat.feedback(
            source,
            "Found ${read.buttons.size} active buttons: ${plan.imported} ready, " +
                "${plan.duplicates} duplicates, ${plan.conflicts} position conflicts.",
        )
        if (!plan.canMerge && plan.canReplace) {
            SkysoftChat.feedback(source, "None can be merged without changing your current Skysoft layout.")
        }
        if (read.substitutedIcons + read.unsupportedIcons + read.giganticButtons + read.malformed > 0) {
            SkysoftChat.feedback(
                source,
                "Icons: ${read.substitutedIcons} substituted, ${read.unsupportedIcons} fallback; " +
                    "${read.giganticButtons} giant resized; ${read.malformed} malformed omitted.",
            )
        }
        if (read.unsupportedSettings.isNotEmpty()) {
            SkysoftChat.feedback(source, "Not imported: ${read.unsupportedSettings.joinToString()}.")
        }
        SkysoftChat.feedback(source, confirmationLine(plan))
        return Command.SINGLE_SUCCESS
    }

    private fun confirm(source: FabricClientCommandSource, openEditor: () -> Int): Int {
        val pending = InventoryButtonImportService.pendingPlan
        if (pending != null && !pending.canMerge) {
            SkysoftChat.error(source, "No buttons can be safely merged. Choose Replace Skysoft layout or Cancel.")
            return 0
        }
        val plan = InventoryButtonImportService.confirmMerge()
        if (plan == null) {
            SkysoftChat.error(source, "There is no pending inventory button import.")
            return 0
        }
        SkysoftChat.feedback(source, "Imported ${plan.imported} buttons. Existing Skysoft buttons were preserved.")
        SkysoftChat.feedback(source, undoLine())
        openEditor()
        return Command.SINGLE_SUCCESS
    }

    private fun replace(source: FabricClientCommandSource, openEditor: () -> Int): Int {
        val plan = InventoryButtonImportService.replaceWithImportedLayout()
        if (plan == null) {
            SkysoftChat.error(source, "There is no conflicting inventory button import to replace the layout with.")
            return 0
        }
        SkysoftChat.feedback(
            source,
            "Replaced the Skysoft layout with ${plan.read.buttons.size} ${plan.read.source.kind.displayName} buttons.",
        )
        SkysoftChat.feedback(source, undoLine())
        openEditor()
        return Command.SINGLE_SUCCESS
    }

    private fun cancel(source: FabricClientCommandSource): Int {
        if (InventoryButtonImportService.cancelPendingImport() == null) {
            SkysoftChat.error(source, "There is no pending inventory button import.")
            return 0
        }
        SkysoftChat.feedback(source, "Inventory button import cancelled.")
        return Command.SINGLE_SUCCESS
    }

    private fun undo(source: FabricClientCommandSource, openEditor: () -> Int): Int {
        if (InventoryButtonImportService.undoImport() == null) {
            SkysoftChat.error(source, "There is no inventory button import to undo in this session.")
            return 0
        }
        SkysoftChat.feedback(source, "Restored the inventory buttons from before the import.")
        openEditor()
        return Command.SINGLE_SUCCESS
    }

    private fun confirmationLine(plan: InventoryButtonImportPlan): Component = Component.empty().apply {
        if (plan.canMerge) {
            append(action("[Merge]", "/skysoft buttons import confirm", ChatFormatting.GREEN))
            append(Component.literal("  "))
        }
        if (plan.canReplace) {
            append(action("[Replace Skysoft layout]", "/skysoft buttons import replace", ChatFormatting.YELLOW))
            append(Component.literal("  "))
        }
        append(action("[Cancel]", "/skysoft buttons import cancel", ChatFormatting.RED))
    }

    private fun undoLine(): Component =
        action("[Undo import]", "/skysoft buttons import undo", ChatFormatting.YELLOW)

    private fun action(label: String, command: String, color: ChatFormatting): Component =
        Component.literal(label).withStyle {
            it.withColor(color)
                .withUnderlined(true)
                .withClickEvent(ClickEvent.RunCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Component.literal(command).withStyle(ChatFormatting.GRAY)))
        }
}
