package com.skysoft.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.skysoft.SkysoftMod
import com.skysoft.config.core.repairLoadedConfigs
import com.skysoft.config.discovery.NewSettingsConfigBootstrap
import com.skysoft.config.features.pets.PetFeatureConfig
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import com.skysoft.data.hypixel.SkysoftGame.RAVENGARD
import com.skysoft.data.hypixel.SkysoftGame.SKYBLOCK
import com.skysoft.utils.ColorUtilities.RGB_MASK
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.LegacyStringChromaColourTypeAdapter
import io.github.notenoughupdates.moulconfig.Social
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.observer.PropertyTypeAdapterFactory
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import java.nio.file.Files
import java.nio.file.Path

open class SkysoftConfig(private val saveDisabledReason: String? = null) : Config() {
    private var saveDisabledWarningShown = false

    @JvmField
    @field:Expose
    var configMigrationVersion = SkysoftConfigMigrations.CURRENT_CONFIG_MIGRATION_VERSION

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "About", desc = "Information about Skysoft.")
    val about = AboutConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(RAVENGARD)
    @field:Category(name = "Ravengard", desc = "Ravengard settings.")
    val ravengard = RavengardFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "GUI", desc = "GUI and HUD editor settings.")
    val gui = GuiFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "Inventory", desc = "Inventory and item tooltip settings.")
    val inventory = InventoryFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Combat", desc = "Combat settings.")
    val combat = CombatFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Slayer", desc = "Slayer quest helpers.")
    val slayer = SlayerFeatureConfig()

    @JvmField
    @field:Expose
    @field:SerializedName(value = "profitTrackers", alternate = ["profitTracker"])
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Profit Trackers", desc = "Configure activity profit trackers.")
    val profitTrackers = ProfitTrackersConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "Chat", desc = "Chat history, compacting, and visual settings.")
    val chat = ChatFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Farming", desc = "Farming settings.")
    val farming = FarmingFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Mining", desc = "Mining settings.")
    val mining = MiningFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Foraging", desc = "Foraging settings.")
    val foraging = ForagingFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Hunting", desc = "Hunting settings.")
    val hunting = HuntingFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Safari", desc = "Critter Safari helpers.")
    val safari = SafariFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Fishing", desc = "Fishing settings.")
    val fishing = FishingFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Events", desc = "Event settings.")
    val events = EventFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK)
    @field:Category(name = "Pets", desc = "Pet display and storage settings.")
    val pets = PetFeatureConfig()

    val storage: ProfileStorage
        get() = ProfileStorageApi.allStorage

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "Misc", desc = "Miscellaneous settings.")
    val misc = MiscFeatureConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "Fixes", desc = "Fixes for Minecraft and SkyBlock issues.")
    val fixes = FixesConfig()

    @JvmField
    @field:Expose
    @field:ConfigGames(SKYBLOCK, RAVENGARD)
    @field:Category(name = "Settings", desc = "Global feature and config menu settings.")
    val settings = SettingsConfig()

    override fun getTitle(): StructuredText =
        StructuredText.of("Skysoft ${SkysoftMod.VERSION} by §cAkinsoft§r, config by §5Moulberry §rand §5nea89")

    override fun getSocials(): List<Social> = SkysoftSocialLink.headerLinks

    override fun formatCategoryName(category: ProcessedCategory, isSelected: Boolean): StructuredText {
        val color = when {
            isSelected -> settings.selectedCategoryColor
            category.parentCategoryId == null -> settings.categoryColor
            else -> settings.subcategoryColor
        }
        return category.displayName.copyShallow()
            .withColour(color.get().getEffectiveColourRGB() and RGB_MASK)
            .apply { if (isSelected) underlined() }
    }

    override fun saveNow() {
        try {
            if (saveDisabledReason != null) {
                if (!saveDisabledWarningShown) {
                    saveDisabledWarningShown = true
                    SkysoftMod.LOGGER.warn("Skipping Skysoft config save because $saveDisabledReason")
                }
            } else {
                repairLoadedValues()
                val json = GSON.toJson(this)
                SkysoftConfigFiles.writeStringSafely(CONFIG_PATH, json)
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to save Skysoft config", e)
        }
    }

    companion object {
        private val GSON: Gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapterFactory(PropertyTypeAdapterFactory())
            .registerTypeAdapter(ChromaColour::class.java, LegacyStringChromaColourTypeAdapter(true).nullSafe())
            .setPrettyPrinting()
            .create()
        private val CONFIG_PATH: Path = SkysoftConfigFiles.config
        private var configClass: Class<out SkysoftConfig> = SkysoftConfig::class.java
        private var configFactory: (String?) -> SkysoftConfig = ::SkysoftConfig

        internal fun useImplementation(
            implementationClass: Class<out SkysoftConfig>,
            factory: (String?) -> SkysoftConfig,
        ) {
            configClass = implementationClass
            configFactory = factory
        }

        fun load(): SkysoftConfig {
            if (SkysoftConfigFiles.migrateConfig() == MigrationResult.FAILED) {
                NewSettingsConfigBootstrap.captureUnavailableConfig("Legacy config migration failed")
                return createConfig(
                    "legacy ${SkysoftConfigFiles.legacyConfig} could not be copied to $CONFIG_PATH. " +
                        "Move it manually or fix file permissions to save changes.",
                )
            }
            if (!SkysoftConfigFiles.hasFileOrBackup(CONFIG_PATH)) {
                NewSettingsConfigBootstrap.captureFreshConfig()
                return createConfig()
            }

            return try {
                SkysoftConfigFiles.readWithBackup(CONFIG_PATH) { path ->
                    Files.newBufferedReader(path).use { reader ->
                        val json = JsonParser.parseReader(reader).asJsonObject
                        SkysoftConfigMigrations.apply(json, GSON)
                        NewSettingsConfigBootstrap.captureLoadedConfig(json)
                        (GSON.fromJson(json, configClass) ?: createConfig()).also {
                            it.repairLoadedValues()
                        }
                    }
                }
            } catch (e: Exception) {
                NewSettingsConfigBootstrap.captureUnavailableConfig("Config and backups failed to load")
                SkysoftMod.LOGGER.warn("Failed to load Skysoft config or backup, using defaults", e)
                createConfig("$CONFIG_PATH failed to load. Fix or delete the file to save changes.")
            }
        }

        private fun createConfig(saveDisabledReason: String? = null): SkysoftConfig =
            configFactory(saveDisabledReason)
    }

    fun repairLoadedValues() {
        migrateLoadedValues()
        repairLoadedConfigs(
            ravengard,
            gui,
            inventory,
            combat,
            chat,
            events,
            misc,
        )
        profitTrackers.custom.repairLoadedValues()
        repairLoadedConfigs(pets)
    }

    private fun migrateLoadedValues() {
        ProfileStorageApi.importLegacyStorage(pets.petDisplay.legacyStorage)
    }
}
