package com.skysoft

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.logging.LogUtils
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.discovery.NewSettingsDiscovery
import com.skysoft.data.ProfileStorageApi
import com.skysoft.features.event.diana.DianaBurrowHelper
import com.skysoft.features.helditem.HeldItemEditorScreen
import com.skysoft.features.inventory.InventoryButtonEditorScreen
import com.skysoft.features.inventory.InventoryButtonImportCommand
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
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.commands.SkysoftCommandRegistry
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.literal
import com.skysoft.utils.input.InputUtilities
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class SkysoftMod : ClientModInitializer {
    override fun onInitializeClient() {
        SkysoftErrorBoundary.register()
        SkysoftFeatureRegistrations.registerAll()
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkysoftErrorBoundary.run("Config save") { SkysoftConfigGui.config().saveNow() }
            SkysoftErrorBoundary.run("Profile storage save") { ProfileStorageApi.saveNow() }
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            SkysoftErrorBoundary.run("Command registration") { registerCommands(dispatcher) }
        }
        SkysoftClientEvents.onEndTick("Position Editor keybind", ::hasPositionEditorKeybind) {
            handlePositionEditorKeybind()
        }
        SkysoftClientEvents.onEndTick("Tooltip keyboard navigation", TooltipViewport::needsKeyboardUpdate) {
            TooltipViewport.updateKeyboardPan()
        }
    }

    companion object {
        const val MOD_ID: String = "skysoft"

        val VERSION: String
            get() = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
        val LOGGER = LogUtils.getLogger()

        fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

        private var positionEditorKeyWasDown = false

        private fun hasPositionEditorKeybind(): Boolean =
            SkysoftConfigGui.config().gui.positionEditor.keybind != GLFW.GLFW_KEY_UNKNOWN || positionEditorKeyWasDown

        private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            SkysoftCommandRegistry(dispatcher).apply {
                root { openMenu() }
                child("edit") { name -> literal(name).executes { openEditor() } }
                child { InventoryButtonImportCommand.command(::openButtonEditor) }
                child("invbuttons") { name -> literal(name).executes { openButtonEditor() } }
                child("helditem") { name -> literal(name).executes { openHeldItemEditor() } }
                child("customtrackers") { name -> literal(name).executes { openCustomTrackers() } }
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

        private fun openMenu(search: String? = null): Int {
            DeferredScreenRequests.request("Config") { SkysoftConfigGui.open(search) }
            return Command.SINGLE_SUCCESS
        }

        private fun openEditor(): Int {
            DeferredScreenRequests.request("HUD editor", SkysoftHudEditor::open)
            return Command.SINGLE_SUCCESS
        }

        private fun openButtonEditor(): Int {
            DeferredScreenRequests.request("Inventory button editor", InventoryButtonEditorScreen::open)
            return Command.SINGLE_SUCCESS
        }

        private fun openHeldItemEditor(): Int {
            DeferredScreenRequests.request("Held item editor", HeldItemEditorScreen::open)
            return Command.SINGLE_SUCCESS
        }

        private fun openCustomTrackers(): Int {
            DeferredScreenRequests.request("Custom Profit Tracker editor") {
                CustomProfitTrackerConfigScreen.open()
            }
            return Command.SINGLE_SUCCESS
        }

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
                SkysoftChat.feedback(source, "No new Skysoft settings have been discovered yet.")
                return Command.SINGLE_SUCCESS
            }
            DeferredScreenRequests.request("New settings") {
                if (!NewSettingsDiscovery.didOpenPresentedSettings()) {
                    SkysoftChat.chat("No new Skysoft settings have been discovered yet.")
                }
            }
            return Command.SINGLE_SUCCESS
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

        private fun handlePositionEditorKeybind() {
            val key = SkysoftConfigGui.config().gui.positionEditor.keybind
            val minecraft = Minecraft.getInstance()
            val keyDown = key != GLFW.GLFW_KEY_UNKNOWN &&
                key != GLFW.GLFW_KEY_ENTER &&
                InputUtilities.isBindingDown(key)
            if (!keyDown) {
                positionEditorKeyWasDown = false
                return
            }
            if (positionEditorKeyWasDown) return
            positionEditorKeyWasDown = true

            val screen = MinecraftClient.screen(minecraft)
            if (screen is SkysoftHudEditor.EditorScreen) return
            if (screen != null && screen !is AbstractContainerScreen<*>) return
            DeferredScreenRequests.request("HUD editor", SkysoftHudEditor::open)
        }
    }
}
