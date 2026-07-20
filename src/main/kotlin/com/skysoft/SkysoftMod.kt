package com.skysoft

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.logging.LogUtils
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.discovery.NewSettingsDiscovery
import com.skysoft.config.discovery.NewSettingsOpenResult
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.SkyBlockCookieBuffApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockEventScheduleApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.combat.BetterShurikens
import com.skysoft.features.chat.ChatHistoryPersistence
import com.skysoft.features.chat.ChatTabs
import com.skysoft.features.chat.ImageLinkPreview
import com.skysoft.features.event.diana.DianaBurrowHelper
import com.skysoft.features.fishing.FishingHotspotRadar
import com.skysoft.features.fishing.FishingHotspotSharing
import com.skysoft.features.helditem.HeldItemEditorScreen
import com.skysoft.features.hunting.LotumHelper
import com.skysoft.features.inventory.FullInventoryWarning
import com.skysoft.features.inventory.InventoryButtonEditorScreen
import com.skysoft.features.inventory.InventoryButtonImportCommand
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.MinisterCalendarTooltip
import com.skysoft.features.inventory.registerSlotBindingStorage
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.inventory.itemlist.ItemListNpcWaypoint
import com.skysoft.features.inventory.itemlist.ItemListSearchCommand
import com.skysoft.features.inventory.PriceTooltips
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.StorageCache
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.StoragePreviews
import com.skysoft.features.loot.RareLootSharing
import com.skysoft.features.misc.DayDisplay
import com.skysoft.features.misc.bettertab.BetterTab
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.features.misc.RealTimeDisplay
import com.skysoft.features.misc.ServerInfoDisplay
import com.skysoft.features.misc.ServerTpsProvider
import com.skysoft.features.misc.ScoreboardPositionEditor
import com.skysoft.features.misc.autosprint.AutoSprint
import com.skysoft.features.misc.blockoverlay.BlockOverlay
import com.skysoft.features.misc.actionbar.ActionBarBackground
import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.pets.ActivePetEntityTracker
import com.skysoft.features.pets.ActivePetOverlay
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.features.pets.PetAnimationLearner
import com.skysoft.features.pets.PetRepository
import com.skysoft.features.pets.PetStorageService
import com.skysoft.features.pets.PetXpEstimator
import com.skysoft.features.pets.SkillExpGainApi
import com.skysoft.features.pets.VisiblePetPosition
import com.skysoft.features.screenshot.ScreenshotCapturePreview
import com.skysoft.features.screenshot.ScreenshotManager
import com.skysoft.features.slayer.SlayerMinibossAlert
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.commands.SkysoftCommandRegistry
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.literal
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.render.item.SkysoftItemRenderSupport
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
        registerFeature("Hypixel Location State", HypixelLocationState::register)
        registerFeature("Hypixel Party API", HypixelPartyApi::register)
        registerFeature("Party Sharing", SkysoftPartyShare::register)
        registerFeature("Tab List API", TabListApi::register)
        registerFeature("Cookie Buff API", SkyBlockCookieBuffApi::register)
        registerFeature("SkyBlock Profile API", SkyBlockProfileApi::register)
        registerFeature("Entity Lifecycle Events", EntityLifecycleEvents::register)
        registerFeature("Profile Storage", ProfileStorageApi::register)
        registerFeature("Storage Cache", StorageCache::register)
        registerFeature("Attribute Shard Catalog", AttributeShardCatalog::register)
        registerFeature("Mayor Perk API", MayorPerkApi::register)
        registerFeature("SkyBlock Event Schedule", SkyBlockEventScheduleApi::register)
        registerFeature("SkyBlock Event State", SkyBlockEventState::register)
        registerFeature("Slayer Quest State", SlayerQuestState::register)
        registerFeature("SkyBlock Price Data", SkyBlockPriceData::register)
        registerFeature("SkyBlock Data Repository", SkyBlockDataRepository::register)
        registerFeature("Entity Highlight Renderer", EntityHighlightRenderer::register)
        registerFeature("World Render Dispatcher", WorldRenderDispatcher::register)
        registerFeature("Item Render Support", SkysoftItemRenderSupport::register)
        registerFeature("GUI Overlay Registry", GuiOverlayRegistry::register)
        registerFeature("Chat Image Preview", ImageLinkPreview::register)
        registerFeature("Screen Alert Renderer", ScreenAlertRenderer::register)
        registerFeature("Lotum Helper", LotumHelper::register)
        registerFeature("Price Tooltips", PriceTooltips::register)
        registerFeature("Minister in Calendar", MinisterCalendarTooltip::register)
        registerFeature("Storage Previews", StoragePreviews::register)
        registerFeature("Full Inventory Warning", FullInventoryWarning::register)
        registerFeature("Inventory Buttons", InventoryButtonManager::register)
        registerFeature("Inventory Equipment", InventoryEquipment::register)
        registerFeature("Slot Bindings", ::registerSlotBindingStorage)
        registerFeature("Slot Locking", SlotLockManager::register)
        registerFeature("Protect Item", ItemProtectionManager::register)
        registerFeature("Item List", ItemListController::register)
        registerFeature("Item List Waypoints", ItemListNpcWaypoint::register)
        registerFeature("Storage Overlay", StorageOverlayController::register)
        registerFeature("Smooth Swapping", SmoothSwapping::register)
        registerFeature("Chat History", ChatHistoryPersistence::register)
        registerFeature("Chat Tabs", ChatTabs::register)
        registerFeature("Action Bar Background", ActionBarBackground::register)
        registerFeature("Better TAB", BetterTab::register)
        registerFeature("Day Display", DayDisplay::register)
        registerFeature("Real Time Display", RealTimeDisplay::register)
        registerFeature("Server TPS Provider", ServerTpsProvider::register)
        registerFeature("Server Info Display", ServerInfoDisplay::register)
        registerFeature("Scoreboard Position Editor", ScoreboardPositionEditor::register)
        registerFeature("Player Head Skin Fix", PlayerHeadSkinFix::register)
        registerFeature("Auto Sprint", AutoSprint::register)
        registerFeature("Block Overlay", BlockOverlay::register)
        registerFeature("Screenshot Manager", ScreenshotManager::register)
        registerFeature("Screenshot Capture Preview", ScreenshotCapturePreview::register)
        registerFeature("Pet Repository", PetRepository::register)
        registerFeature("Active Pet Tracker", ActivePetTracker::register)
        registerFeature("Skill Experience API", SkillExpGainApi::register)
        registerFeature("Pet Experience Estimator", PetXpEstimator::register)
        registerFeature("Pet Storage", PetStorageService::register)
        registerFeature("Active Pet Overlay", ActivePetOverlay::register)
        registerFeature("Active Pet Entity Tracker", ActivePetEntityTracker::register)
        registerFeature("Pet Animation Learner", PetAnimationLearner::register)
        registerFeature("Visible Pet Position", VisiblePetPosition::register)
        registerFeature("Bazaar Tracker", BazaarTracker::register)
        registerFeature("Better Shurikens", BetterShurikens::register)
        registerFeature("Slayer Miniboss Alert", SlayerMinibossAlert::register)
        registerFeature("Fishing Hotspot Sharing", FishingHotspotSharing::register)
        registerFeature("Fishing Hotspot Radar", FishingHotspotRadar::register)
        registerFeature("Rare Loot Sharing", RareLootSharing::register)
        registerFeature("Diana Burrow Helper", DianaBurrowHelper::register)
        registerFeature("New Settings Discovery", NewSettingsDiscovery::register)
        registerFeature("Update Checker", ModUpdateChecker::register)
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
        SkysoftClientEvents.onEndTick("Pending Skysoft screens", ::hasPendingScreens) { openPendingScreens() }
        SkysoftClientEvents.onEndTick("Item List search opening", ItemListSearchCommand::hasPendingScreen) {
            ItemListSearchCommand.openPending()
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

        private var shouldOpenMenu = false
        private var pendingMenuSearch: String? = null
        private var shouldOpenEditor = false
        private var shouldOpenButtonEditor = false
        private var shouldOpenHeldItemEditor = false
        private var shouldOpenNewSettings = false
        private var positionEditorKeyWasDown = false

        private fun registerFeature(name: String, registration: () -> Unit) {
            SkysoftErrorBoundary.run("$name initialization", registration)
        }

        private fun openPendingScreens() {
            if (shouldOpenMenu) {
                shouldOpenMenu = false
                SkysoftConfigGui.open(pendingMenuSearch)
                pendingMenuSearch = null
            }
            if (shouldOpenEditor) {
                shouldOpenEditor = false
                SkysoftHudEditor.open()
            }
            if (shouldOpenButtonEditor) {
                shouldOpenButtonEditor = false
                InventoryButtonEditorScreen.open()
            }
            if (shouldOpenHeldItemEditor) {
                shouldOpenHeldItemEditor = false
                HeldItemEditorScreen.open()
            }
            if (shouldOpenNewSettings) {
                shouldOpenNewSettings = false
                if (NewSettingsDiscovery.openPresentedSettings() != NewSettingsOpenResult.OPENED) {
                    SkysoftChat.chat("No new Skysoft settings have been discovered yet.")
                }
            }
        }

        private fun hasPendingScreens(): Boolean =
            shouldOpenMenu || shouldOpenEditor || shouldOpenButtonEditor || shouldOpenHeldItemEditor ||
                shouldOpenNewSettings

        private fun hasPositionEditorKeybind(): Boolean =
            SkysoftConfigGui.config().gui.positionEditor.keybind != GLFW.GLFW_KEY_UNKNOWN || positionEditorKeyWasDown

        private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            SkysoftCommandRegistry(dispatcher).apply {
                root { openMenu() }
                child("edit") { name -> literal(name).executes { openEditor() } }
                child { InventoryButtonImportCommand.command(::openButtonEditor) }
                child("invbuttons") { name -> literal(name).executes { openButtonEditor() } }
                child("helditem") { name -> literal(name).executes { openHeldItemEditor() } }
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
        }

        private fun openMenu(search: String? = null): Int {
            pendingMenuSearch = search
            shouldOpenMenu = true
            return Command.SINGLE_SUCCESS
        }

        private fun openEditor(): Int {
            shouldOpenEditor = true
            return Command.SINGLE_SUCCESS
        }

        private fun openButtonEditor(): Int {
            shouldOpenButtonEditor = true
            return Command.SINGLE_SUCCESS
        }

        private fun openHeldItemEditor(): Int {
            shouldOpenHeldItemEditor = true
            return Command.SINGLE_SUCCESS
        }

        private fun openNewSettings(source: FabricClientCommandSource): Int {
            if (!NewSettingsDiscovery.hasPresentedSettings()) {
                SkysoftChat.feedback(source, "No new Skysoft settings have been discovered yet.")
                return Command.SINGLE_SUCCESS
            }
            shouldOpenNewSettings = true
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
                InputUtilities.isKeyDown(key)
            if (!keyDown) {
                positionEditorKeyWasDown = false
                return
            }
            if (positionEditorKeyWasDown) return
            positionEditorKeyWasDown = true

            val screen = MinecraftClient.screen(minecraft)
            if (screen is SkysoftHudEditor.EditorScreen) return
            if (screen != null && screen !is AbstractContainerScreen<*>) return
            shouldOpenEditor = true
        }
    }
}
