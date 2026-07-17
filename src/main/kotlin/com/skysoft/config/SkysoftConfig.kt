package com.skysoft.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.Expose
import com.skysoft.SkysoftMod
import com.skysoft.config.features.pets.PetFeatureConfig
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.ProfileStorage
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.LegacyStringChromaColourTypeAdapter
import io.github.notenoughupdates.moulconfig.Social
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.observer.PropertyTypeAdapterFactory
import java.nio.file.Files
import java.nio.file.Path

class SkysoftConfig(private val saveDisabledReason: String? = null) : Config() {
    @JvmField
    @field:Expose
    var configMigrationVersion = SkysoftConfigMigrations.CURRENT_CONFIG_MIGRATION_VERSION

    @JvmField
    @field:Expose
    @field:Category(name = "About", desc = "Information about Skysoft.")
    val about = AboutConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "GUI", desc = "GUI and HUD editor settings.")
    val gui = GuiFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Inventory", desc = "Inventory and item tooltip settings.")
    val inventory = InventoryFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Combat", desc = "Combat settings.")
    val combat = CombatFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Chat", desc = "Chat history, compacting, and visual settings.")
    val chat = ChatFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Hunting", desc = "Hunting settings.")
    val hunting = HuntingFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Fishing", desc = "Fishing settings.")
    val fishing = FishingFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Events", desc = "Event settings.")
    val events = EventFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Pets", desc = "Pet display and storage settings.")
    val pets = PetFeatureConfig()

    val storage: ProfileStorage
        get() = ProfileStorageApi.allStorage

    @JvmField
    @field:Expose
    @field:Category(name = "Misc", desc = "Miscellaneous settings.")
    val misc = MiscFeatureConfig()

    @JvmField
    @field:Expose
    @field:Category(name = "Fixes", desc = "Fixes for SkyBlock issues.")
    val fixes = FixesConfig()

    override fun getTitle(): StructuredText =
        StructuredText.of("Skysoft ${SkysoftMod.VERSION} by §cAkinsoft§r, config by §5Moulberry §rand §5nea89")

    override fun getSocials(): List<Social> = SkysoftSocialLink.headerLinks

    override fun saveNow() {
        try {
            if (saveDisabledReason != null) {
                SkysoftMod.LOGGER.warn("Skipping Skysoft config save because $saveDisabledReason")
            } else {
                repairLoadedValues()
                val json = GSON.toJson(this)
                SkysoftConfigFiles.writeStringSafely(CONFIG_PATH, json)
            }
        } catch (e: Exception) {
            SkysoftMod.LOGGER.error("Failed to save Skysoft config", e)
        } finally {
            ProfileStorageApi.saveNow()
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

        fun load(): SkysoftConfig {
            if (SkysoftConfigFiles.migrateConfig() == MigrationResult.FAILED) {
                return SkysoftConfig(
                    "legacy ${SkysoftConfigFiles.legacyConfig} could not be copied to $CONFIG_PATH. " +
                        "Move it manually or fix file permissions to save changes.",
                )
            }
            if (!SkysoftConfigFiles.hasFileOrBackup(CONFIG_PATH)) {
                return SkysoftConfig()
            }

            return try {
                SkysoftConfigFiles.readWithBackup(CONFIG_PATH) { path ->
                    Files.newBufferedReader(path).use { reader ->
                        val json = JsonParser.parseReader(reader).asJsonObject
                        SkysoftConfigMigrations.apply(json, GSON)
                        (GSON.fromJson(json, SkysoftConfig::class.java) ?: SkysoftConfig()).also {
                            it.repairLoadedValues()
                        }
                    }
                }
            } catch (e: Exception) {
                SkysoftMod.LOGGER.warn("Failed to load Skysoft config or backup, using defaults", e)
                SkysoftConfig("$CONFIG_PATH failed to load. Fix or delete the file to save changes.")
            }
        }
    }

    fun repairLoadedValues() {
        ProfileStorageApi.importLegacyStorage(pets.petDisplay.legacyStorage)
        gui.repairLoadedValues()
        inventory.repairLoadedValues()
        chat.repairLoadedValues()
        events.repairLoadedValues()
        ProfileStorageApi.allStorage.repairLoadedValues()
        pets.repairLoadedValues()
    }
}
