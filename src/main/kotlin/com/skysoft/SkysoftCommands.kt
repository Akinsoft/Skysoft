package com.skysoft

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.discovery.NewSettingsDiscovery
import com.skysoft.features.event.diana.DianaBurrowHelper
import com.skysoft.features.helditem.HeldItemEditorScreen
import com.skysoft.features.waypoints.WaypointPanel
import com.skysoft.features.inventory.buttons.InventoryButtonEditorScreen
import com.skysoft.features.inventory.buttons.InventoryButtonImportCommand
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.itemlist.ItemListSearchCommand
import com.skysoft.features.misc.MouseLock
import com.skysoft.features.misc.WarpAliases
import com.skysoft.features.misc.autosprint.AutoSprint
import com.skysoft.features.misc.blockoverlay.BlockOverlay
import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.profit.CustomProfitTrackerConfigScreen
import com.skysoft.gui.DeferredScreenRequests
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.commands.SkysoftCommandRegistry
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

internal object SkysoftCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        SkysoftCommandRegistry(dispatcher).apply {
            root { openMenu() }
            child("edit") { name -> literal(name).executes { openScreen("HUD editor", SkysoftHudEditor::open) } }
            child { InventoryButtonImportCommand.command(::openButtonEditor) }
            child("invbuttons") { name -> literal(name).executes { openButtonEditor() } }
            child("helditem") { name -> literal(name).executes { openScreen("Held item editor", HeldItemEditorScreen::open) } }
            child("waypoints") { name -> literal(name).executes { openScreen("Waypoints Display", WaypointPanel::open) } }
            child("customtrackers") { name ->
                literal(name).executes { openScreen("Custom Profit Tracker editor") { CustomProfitTrackerConfigScreen.open() } }
            }
            child("clearburrows") { name -> literal(name).executes { clearBurrows(it.source) } }
            child("mouselock", "ssmouselock") { name -> literal(name).executes { MouseLock.toggle(it.source) } }
            child("new") { name -> literal(name).executes { openNewSettings(it.source) } }
            child("protect") { name -> literal(name).executes { ItemProtectionManager.toggleHeldItem(it.source) } }
            child("update", "ssupdate") { name -> literal(name).executes { checkUpdate() } }
            child("download") { name -> literal(name).executes { downloadUpdate(it.source) } }
            child {
                literal("autosprint")
                    .then(literal("additem").executes { AutoSprint.addHeldItem(it.source) })
            }
            child {
                literal("blockoverlay")
                    .then(literal("additem").executes { BlockOverlay.addHeldItem(it.source) })
            }
            fallback("search") {
                openMenu(StringArgumentType.getString(it, "search"))
            }
            register()
        }
        ItemListSearchCommand.register(dispatcher)
        WarpAliases.registerSuggestions(dispatcher)
    }

    private fun openScreen(name: String, open: () -> Unit): Int {
        DeferredScreenRequests.request(name, open)
        return Command.SINGLE_SUCCESS
    }

    private fun openMenu(search: String? = null): Int = openScreen("Config") { SkysoftConfigGui.open(search) }

    private fun openButtonEditor(): Int = openScreen("Inventory button editor", InventoryButtonEditorScreen::open)

    private fun clearBurrows(source: FabricClientCommandSource): Int {
        SkysoftChat.feedback(
            source,
            if (DianaBurrowHelper.didClearBurrows()) {
                "Cleared your saved Diana burrows."
            } else {
                "No SkyBlock profile is currently loaded."
            },
        )
        return Command.SINGLE_SUCCESS
    }

    private fun openNewSettings(source: FabricClientCommandSource): Int {
        if (!NewSettingsDiscovery.hasPresentedSettings()) {
            SkysoftChat.feedback(source, NO_NEW_SETTINGS_MESSAGE)
            return Command.SINGLE_SUCCESS
        }
        return openScreen("New settings") {
            if (!NewSettingsDiscovery.didOpenPresentedSettings()) SkysoftChat.chat(NO_NEW_SETTINGS_MESSAGE)
        }
    }

    private fun checkUpdate(): Int {
        ModUpdateChecker.check(force = true)
        return Command.SINGLE_SUCCESS
    }

    private fun downloadUpdate(source: FabricClientCommandSource): Int {
        if (ModUpdateChecker.openDownload() != DownloadOpenResult.OPENED) {
            SkysoftChat.feedback(source, "No update download is ready yet. Checking now.")
            ModUpdateChecker.check(force = true)
        }
        return Command.SINGLE_SUCCESS
    }

    private const val NO_NEW_SETTINGS_MESSAGE = "No new Skysoft settings have been discovered yet."
}
