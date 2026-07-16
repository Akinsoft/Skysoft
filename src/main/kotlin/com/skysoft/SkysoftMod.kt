package com.skysoft

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.logging.LogUtils
import com.skysoft.config.SkysoftConfigGui
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
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.combat.BetterShurikens
import com.skysoft.features.chat.ChatHistoryPersistence
import com.skysoft.features.chat.ChatTabs
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
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.inventory.itemlist.ItemListNpcWaypoint
import com.skysoft.features.inventory.itemlist.ItemListSearchCommand
import com.skysoft.features.inventory.PriceTooltips
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.loot.RareLootSharing
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.features.misc.autosprint.AutoSprint
import com.skysoft.features.misc.blockoverlay.BlockOverlay
import com.skysoft.features.misc.actionbar.ActionBarBackground
import com.skysoft.features.misc.update.DownloadOpenResult
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.features.pets.ActivePetOverlay
import com.skysoft.features.pets.PetStorageService
import com.skysoft.features.pets.PetXpEstimator
import com.skysoft.features.pets.SkillExpGainApi
import com.skysoft.features.pets.VisiblePetPosition
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.tooltip.TooltipViewport
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftChat
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.commands.SkysoftCommandRegistry
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.literal
import com.skysoft.utils.commands.SkysoftCommandRegistry.Companion.stringArgument
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.render.item.SkysoftItemRenderSupport
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class SkysoftMod : ClientModInitializer {
    override fun onInitializeClient() {
        HypixelLocationState.register()
        HypixelPartyApi.register()
        SkysoftPartyShare.register()
        TabListApi.register()
        SkyBlockCookieBuffApi.register()
        SkyBlockProfileApi.register()
        EntityLifecycleEvents.register()
        ProfileStorageApi.register()
        AttributeShardCatalog.register()
        MayorPerkApi.register()
        SkyBlockEventScheduleApi.register()
        SkyBlockEventState.register()
        SkyBlockPriceData.register()
        SkyBlockDataRepository.register()
        EntityHighlightRenderer.register()
        WorldRenderDispatcher.register()
        SkysoftItemRenderSupport.register()
        GuiOverlayRegistry.register()
        ScreenAlertRenderer.register()
        LotumHelper.register()
        PriceTooltips.register()
        FullInventoryWarning.register()
        InventoryButtonManager.register()
        InventoryEquipment.register()
        ItemListController.register()
        ItemListNpcWaypoint.register()
        StorageOverlayController.register()
        SmoothSwapping.register()
        ChatHistoryPersistence.register()
        ChatTabs.register()
        ActionBarBackground.register()
        PlayerHeadSkinFix.register()
        AutoSprint.register()
        BlockOverlay.register()
        ActivePetTracker.register()
        SkillExpGainApi.register()
        PetXpEstimator.register()
        PetStorageService.register()
        ActivePetOverlay.register()
        VisiblePetPosition.register()
        BazaarTracker.register()
        BetterShurikens.register()
        FishingHotspotSharing.register()
        FishingHotspotRadar.register()
        RareLootSharing.register()
        DianaBurrowHelper.register()
        ModUpdateChecker.register()
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SkysoftConfigGui.config().saveNow()
            ProfileStorageApi.saveNow()
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> registerCommands(dispatcher) }
        ClientTickEvents.END_CLIENT_TICK.register {
            handlePositionEditorKeybind()
            TooltipViewport.updateKeyboardPan()
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
        private var positionEditorKeyWasDown = false

        private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            SkysoftCommandRegistry(dispatcher).apply {
                root { openMenu() }
                child("edit") { name -> literal(name).executes { openEditor() } }
                child { InventoryButtonImportCommand.command(::openButtonEditor) }
                child("invbuttons") { name -> literal(name).executes { openButtonEditor() } }
                child("helditem") { name -> literal(name).executes { openHeldItemEditor() } }
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
                child {
                    stringArgument("search").executes {
                        openMenu(StringArgumentType.getString(it, "search"))
                    }
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
