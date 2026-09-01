package com.skysoft

import com.skysoft.config.discovery.NewSettingsDiscovery
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.MinecraftProfileLookup
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.HypixelPartyApi
import com.skysoft.data.hypixel.SkyBlockCookieBuffApi
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.MayorPerkApi
import com.skysoft.data.skyblock.SafariZoneState
import com.skysoft.data.skyblock.SkyBlockAreaState
import com.skysoft.data.skyblock.SkyBlockCurrencyChanges
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockDroppedItems
import com.skysoft.data.skyblock.SkyBlockEventScheduleApi
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockInventoryChanges
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.data.skyblock.SkillExpGainApi
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockSackChanges
import com.skysoft.data.skyblock.SkyBlockSackContents
import com.skysoft.data.skyblock.SkyBlockSackTransfers
import com.skysoft.data.skyblock.SlayerQuestState
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.data.skyblock.pets.PetRepository
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.features.bazaar.BazaarTracker
import com.skysoft.features.chat.ChatHistoryPersistence
import com.skysoft.features.chat.ChatTabs
import com.skysoft.features.chat.ImageLinkPreview
import com.skysoft.features.chat.PlayerBadges
import com.skysoft.features.combat.BetterShurikens
import com.skysoft.features.combat.CocoonTracker
import com.skysoft.features.combat.HealingPoolLine
import com.skysoft.features.combat.SkyBlockMobTracker
import com.skysoft.features.event.diana.DianaBurrowHelper
import com.skysoft.features.event.diana.DianaBurrowInteractions
import com.skysoft.features.event.diana.DianaBurrowStorage
import com.skysoft.features.event.diana.DianaLobbyCompromisedWatcher
import com.skysoft.features.event.diana.DianaParticleQuality
import com.skysoft.features.event.diana.DianaRareMobSharing
import com.skysoft.features.event.diana.MythologicalRitualTracker
import com.skysoft.features.farming.NoCropRotation
import com.skysoft.features.fishing.FishingHotspotRadar
import com.skysoft.features.fishing.FishingHotspotSharing
import com.skysoft.features.fishing.SeaCreatureCatchMessages
import com.skysoft.features.foraging.FloorDropHighlighter
import com.skysoft.features.foraging.HoneyhiveHelper
import com.skysoft.features.foraging.QueenAntWarning
import com.skysoft.features.foraging.ThrowingAxeHelper
import com.skysoft.features.foraging.ThrowingAxeParticleHider
import com.skysoft.features.hunting.LotumHelper
import com.skysoft.features.inventory.AnimatedDyeArmorCache
import com.skysoft.features.inventory.DuplicateEnchantmentTooltipFix
import com.skysoft.features.inventory.FullInventoryWarning
import com.skysoft.features.inventory.InventoryButtonManager
import com.skysoft.features.inventory.InventoryEquipment
import com.skysoft.features.inventory.InventoryEquipmentCache
import com.skysoft.features.inventory.InventoryHud
import com.skysoft.features.inventory.ItemChangeLog
import com.skysoft.features.inventory.ItemProtectionManager
import com.skysoft.features.inventory.MaxEnchantChroma
import com.skysoft.features.inventory.MinisterCalendarTooltip
import com.skysoft.features.inventory.PriceTooltipRawCraftCosts
import com.skysoft.features.inventory.PriceTooltips
import com.skysoft.features.inventory.SlotLockManager
import com.skysoft.features.inventory.sacks.SackDisplay
import com.skysoft.features.inventory.sacks.SackHud
import com.skysoft.features.inventory.SmoothSwapping
import com.skysoft.features.inventory.StorageCache
import com.skysoft.features.inventory.StorageOverlayController
import com.skysoft.features.inventory.StoragePreviews
import com.skysoft.features.inventory.itemlist.ItemListController
import com.skysoft.features.inventory.itemlist.ItemListNpcWaypoint
import com.skysoft.features.inventory.itemlist.ItemListState
import com.skysoft.features.inventory.registerSlotBindingStorage
import com.skysoft.features.loot.RareLootChatFeatures
import com.skysoft.features.mining.MiningAbilityCooldownDisplay
import com.skysoft.features.misc.DayDisplay
import com.skysoft.features.misc.ForIntrests
import com.skysoft.features.misc.KeepTerrainLoaded
import com.skysoft.features.misc.MouseLock
import com.skysoft.features.misc.PartyDisplay
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.features.misc.RealTimeDisplay
import com.skysoft.features.misc.ScoreboardPositionEditor
import com.skysoft.features.misc.ServerInfoDisplay
import com.skysoft.features.misc.ServerTpsProvider
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.features.misc.Zoom
import com.skysoft.features.misc.actionbar.ActionBarCustomizer
import com.skysoft.features.misc.actionbar.SkillExpDisplay
import com.skysoft.features.misc.autosprint.AutoSprint
import com.skysoft.features.misc.bettertab.BetterTab
import com.skysoft.features.misc.blockoverlay.BlockOverlay
import com.skysoft.features.misc.custombars.CustomBars
import com.skysoft.features.misc.selecteditem.SelectedItemName
import com.skysoft.features.misc.update.ModUpdateChecker
import com.skysoft.features.pets.ActivePetEntityTracker
import com.skysoft.features.pets.ActivePetOverlay
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.features.pets.PetAnimationLearner
import com.skysoft.features.pets.PetStorageService
import com.skysoft.features.pets.PetXpEstimator
import com.skysoft.features.pets.VisiblePetPosition
import com.skysoft.features.profit.ProfitTracker
import com.skysoft.features.ravengard.RavengardItemComparisonTooltip
import com.skysoft.features.ravengard.RavengardLootBagCheckmarks
import com.skysoft.features.ravengard.RavengardWeaponDpsTooltip
import com.skysoft.features.safari.CapsuleHelper
import com.skysoft.features.safari.HighlightCritters
import com.skysoft.features.safari.HoneybugHelper
import com.skysoft.features.screenshot.ScreenshotCapturePreview
import com.skysoft.features.screenshot.ScreenshotManager
import com.skysoft.features.slayer.BlazeAttunementHighlighting
import com.skysoft.features.slayer.SlayerBossAlerts
import com.skysoft.features.slayer.SlayerMinibossAlert
import com.skysoft.features.slayer.SlayerTargetHighlighting
import com.skysoft.features.slayer.SlayerTimeToKill
import com.skysoft.features.spotify.SpotifyAuthentication
import com.skysoft.features.spotify.SpotifyDisplay
import com.skysoft.gui.DeferredScreenRequests
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.utils.SidebarScoreboardState
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.chat.SkysoftPartyShare
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.ScreenAlertRenderer
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.render.item.SkysoftItemRenderSupport

internal object SkysoftFeatureRegistrations {
    fun registerAll() {
        registerDataAndInfrastructure()
        registerInventoryFeatures()
        registerInterfaceFeatures()
        registerPetFeatures()
        registerGameplayFeatures()
    }

    private fun registerDataAndInfrastructure() {
        register("Client Entity Snapshot", ClientEntitySnapshot::register)
        register("Minecraft Profile Lookup", MinecraftProfileLookup::register)
        register("Hypixel Location State", HypixelLocationState::register)
        register("Hypixel Party API", HypixelPartyApi::register)
        register("Party Sharing", SkysoftPartyShare::register)
        register("Tab List API", TabListApi::register)
        register("Cookie Buff API", SkyBlockCookieBuffApi::register)
        register("SkyBlock Profile API", SkyBlockProfileApi::register)
        register("Sidebar Scoreboard State", SidebarScoreboardState::register)
        registerSkyBlockTrackingApis()
        register("SkyBlock Mob Tracker", SkyBlockMobTracker::register)
        register("Entity Lifecycle Events", EntityLifecycleEvents::register)
        register("Profile Storage", ProfileStorageApi::register)
        register("SkyBlock Sack Contents", SkyBlockSackContents::register)
        register("Storage Cache", StorageCache::register)
        register("Attribute Shard Catalog", AttributeShardCatalog::register)
        register("Mayor Perk API", MayorPerkApi::register)
        register("SkyBlock Event Schedule", SkyBlockEventScheduleApi::register)
        register("SkyBlock Event State", SkyBlockEventState::register)
        register("Slayer Quest State", SlayerQuestState::register)
        register("SkyBlock Price Data", SkyBlockPriceData::register)
        register("SkyBlock Data Repository", SkyBlockDataRepository::register)
        register("Entity Highlight Renderer", EntityHighlightRenderer::register)
        register("World Render Dispatcher", WorldRenderDispatcher::register)
        register("Item Render Support", SkysoftItemRenderSupport::register)
        register("GUI Overlay Registry", GuiOverlayRegistry::register)
        register("Deferred Screen Requests", DeferredScreenRequests::register)
        register("Chat Image Preview", ImageLinkPreview::register)
        register("Screen Alert Renderer", ScreenAlertRenderer::register)
        register("Lotum Helper", LotumHelper::register)
    }

    private fun registerSkyBlockTrackingApis() {
        register("SkyBlock Area State", SkyBlockAreaState::register)
        register("Safari Zone State", SafariZoneState::register)
        register("SkyBlock Inventory Changes", SkyBlockInventoryChanges::register)
        register("SkyBlock Dropped Items", SkyBlockDroppedItems::register)
        register("SkyBlock Sack Transfers", SkyBlockSackTransfers::register)
        register("SkyBlock Open Inventory API", SkyBlockOpenInventoryApi::register)
        register("SkyBlock Currency Changes", SkyBlockCurrencyChanges::register)
        register("SkyBlock Sacks Changes", SkyBlockSackChanges::register)
        register("SkyBlock Item Changes", SkyBlockItemChanges::register)
    }

    private fun registerInventoryFeatures() {
        register("Animated Dye Armor Cache", AnimatedDyeArmorCache::register)
        register("Price Tooltip Raw Craft Costs", PriceTooltipRawCraftCosts::register)
        register("Price Tooltips", PriceTooltips::register)
        register("Sack Display", SackDisplay::register)
        register("Sacks Tracker", SackHud::register)
        register("Item Change Log", ItemChangeLog::register)
        register("Duplicate Enchantment Tooltip Fix", DuplicateEnchantmentTooltipFix::register)
        register("Max Enchant Chroma", MaxEnchantChroma::register)
        register("Minister in Calendar", MinisterCalendarTooltip::register)
        register("Ravengard Weapon DPS", RavengardWeaponDpsTooltip::register)
        register("Ravengard Item Comparison", RavengardItemComparisonTooltip::register)
        register("Storage Previews", StoragePreviews::register)
        register("Full Inventory Warning", FullInventoryWarning::register)
        register("Inventory Buttons", InventoryButtonManager::register)
        register("Inventory Equipment Cache", InventoryEquipmentCache::register)
        register("Inventory Equipment", InventoryEquipment::register)
        register("Slot Bindings", ::registerSlotBindingStorage)
        register("Slot Locking", SlotLockManager::register)
        register("Protect Item", ItemProtectionManager::register)
        register("Item List State", ItemListState::register)
        register("Item List", ItemListController::register)
        register("Item List Waypoints", ItemListNpcWaypoint::register)
        register("Storage Overlay", StorageOverlayController::register)
        register("Smooth Swapping", SmoothSwapping::register)
    }

    private fun registerInterfaceFeatures() {
        register("Profit Tracker", ProfitTracker::register)
        register("Chat History", ChatHistoryPersistence::register)
        register("Chat Tabs", ChatTabs::register)
        register("Player Badges", PlayerBadges::register)
        register("Selected Item Name", SelectedItemName::register)
        register("Action Bar Customizer", ActionBarCustomizer::register)
        register("Skill EXP Display", SkillExpDisplay::register)
        register("SkyBlock Level Bar", SkyBlockLevelBar::register)
        register("Mining Ability Cooldown", MiningAbilityCooldownDisplay::register)
        register("Custom Bars", CustomBars::register)
        register("Inventory HUD", InventoryHud::register)
        register("Better TAB", BetterTab::register)
        register("Party Display", PartyDisplay::register)
        register("Day Display", DayDisplay::register)
        register("Real Time Display", RealTimeDisplay::register)
        register("Server TPS Provider", ServerTpsProvider::register)
        register("Server Info Display", ServerInfoDisplay::register)
        register("Spotify Authentication", SpotifyAuthentication::register)
        register("Spotify Display", SpotifyDisplay::register)
        register("Mouse Lock", MouseLock::register)
        register("Zoom", Zoom::register)
        register("Scoreboard Position Editor", ScoreboardPositionEditor::register)
        register("Player Head Skin Fix", PlayerHeadSkinFix::register)
        register("Auto Sprint", AutoSprint::register)
        register("Block Overlay", BlockOverlay::register)
        register("Screenshot Manager", ScreenshotManager::register)
        register("Screenshot Capture Preview", ScreenshotCapturePreview::register)
    }

    private fun registerPetFeatures() {
        register("Pet Repository", PetRepository::register)
        register("Active Pet Tracker", ActivePetTracker::register)
        register("Skill Experience API", SkillExpGainApi::register)
        register("Pet Experience Estimator", PetXpEstimator::register)
        register("Pet Storage", PetStorageService::register)
        register("Active Pet Overlay", ActivePetOverlay::register)
        register("Active Pet Entity Tracker", ActivePetEntityTracker::register)
        register("Pet Animation Learner", PetAnimationLearner::register)
        register("Visible Pet Position", VisiblePetPosition::register)
    }

    private fun registerGameplayFeatures() {
        register("No Crop Rotation", NoCropRotation::register)
        register("Bazaar Tracker", BazaarTracker::register)
        register("Cocoon Tracker", CocoonTracker::register)
        register("Healing Pool Line", HealingPoolLine::register)
        register("Better Shurikens", BetterShurikens::register)
        register("Ravengard Loot Bag Checkmarks", RavengardLootBagCheckmarks::register)
        register("Slayer Boss Alerts", SlayerBossAlerts::register)
        register("Slayer Miniboss Alert", SlayerMinibossAlert::register)
        register("Slayer Target Highlighting", SlayerTargetHighlighting::register)
        register("Blaze Attunement Highlighting", BlazeAttunementHighlighting::register)
        register("Slayer Time to Kill", SlayerTimeToKill::register)
        register("Sea Creature Catch Messages", SeaCreatureCatchMessages::register)
        register("Fishing Hotspot Sharing", FishingHotspotSharing::register)
        register("Fishing Hotspot Radar", FishingHotspotRadar::register)
        register("Floor Drop Highlighter", FloorDropHighlighter::register)
        register("Honeyhive Helper", HoneyhiveHelper::register)
        register("Queen Ant Warning", QueenAntWarning::register)
        register("Throwing Axe Helper", ThrowingAxeHelper::register)
        register("Hide Axe Particles", ThrowingAxeParticleHider::register)
        register("Capsule Helper", CapsuleHelper::register)
        register("Highlight Critters", HighlightCritters::register)
        register("Honeybug Helper", HoneybugHelper::register)
        register("Rare Loot Features", RareLootChatFeatures::register)
        register("Keep Terrain Loaded", KeepTerrainLoaded::register)
        register("Diana Burrow Storage", DianaBurrowStorage::register)
        register("Diana Particle Quality", DianaParticleQuality::register)
        register("Diana Burrow Helper", DianaBurrowHelper::register)
        register("Diana Burrow Interactions", DianaBurrowInteractions::register)
        register("Diana Lobby Compromised Watcher", DianaLobbyCompromisedWatcher::register)
        register("Diana Rare Mob Sharing", DianaRareMobSharing::register)
        register("Mythological Ritual Tracker", MythologicalRitualTracker::register)
        register("For Intrests", ForIntrests::register)
        register("New Settings Discovery", NewSettingsDiscovery::register)
        register("Update Checker", ModUpdateChecker::register)
    }

    private fun register(name: String, registration: () -> Unit) {
        SkysoftErrorBoundary.run("$name initialization", registration)
    }
}
