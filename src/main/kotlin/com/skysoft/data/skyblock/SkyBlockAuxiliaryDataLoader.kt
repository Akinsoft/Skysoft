package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.CatalogJson.MAXIMUM_TEXT_LENGTH
import com.skysoft.data.skyblock.CatalogJson.array
import com.skysoft.data.skyblock.CatalogJson.catalogIdPattern
import com.skysoft.data.skyblock.CatalogJson.double
import com.skysoft.data.skyblock.CatalogJson.doubleOrNull
import com.skysoft.data.skyblock.CatalogJson.enumOrNull
import com.skysoft.data.skyblock.CatalogJson.hasLinkedSources
import com.skysoft.data.skyblock.CatalogJson.obj
import com.skysoft.data.skyblock.CatalogJson.string
import com.skysoft.utils.WorldVec
import java.io.StringReader

internal object SkyBlockAuxiliaryDataLoader {
    private val gson = Gson()

    fun readPets(json: String): Map<String, SkyBlockPetInfo> = StringReader(json).use { reader ->
        gson.fromJson<Map<String, SkyBlockPetInfo>>(reader, petMapType).orEmpty()
    }

    fun readSupplemental(json: String): SupplementalCatalog {
        val root = JsonParser.parseString(json).asJsonObject
        val progressionRequirements = readProgressionRequirements(root)
        val petMaxLevels = root.obj("petMaxLevels")?.entrySet().orEmpty().associate { (id, value) ->
            require(id.matches(catalogIdPattern) && value.asInt in SupplementalLimits.PET_LEVEL_RANGE) {
                "Item List supplemental pet has invalid leveling data"
            }
            id to value.asInt
        }
        val warps = readWarps(root)
        require(progressionRequirements.size >= SupplementalLimits.MINIMUM_PROGRESSION_REQUIREMENT_COUNT) {
            "Item List supplemental data contains only ${progressionRequirements.size} progression requirements"
        }
        require(warps.size >= SupplementalLimits.MINIMUM_WARP_COUNT) {
            "Item List supplemental data contains only ${warps.size} warps"
        }
        return SupplementalCatalog(progressionRequirements, petMaxLevels, warps)
    }

    fun readEntityContexts(json: String): Map<String, List<String>> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.string("source") == EntityContextSchema.SOURCE) { "Item List entity context source is invalid" }
        val entities = requireNotNull(root.obj("entities")) { "Item List entity context data has no entities" }
        return readEntityDetails(entities).also {
            require(it.size >= EntityContextSchema.MINIMUM_COUNT) {
                "Item List entity context data contains only ${it.size} entities"
            }
        }
    }

    fun readEntityContextExceptions(json: String): Map<String, List<String>> =
        readEntityDetails(JsonParser.parseString(json).asJsonObject)

    private fun readEntityDetails(root: JsonObject): Map<String, List<String>> =
        root.entrySet().associate { (id, value) ->
            require(id.matches(catalogIdPattern)) { "Item List entity context has an invalid ID" }
            val details = value.asJsonArray.map { it.asString }
            require(
                details.isNotEmpty() && details.all {
                    it.isNotBlank() && it.length <= MAXIMUM_TEXT_LENGTH
                },
            ) {
                "Item List entity context $id has invalid details"
            }
            id to details
        }

    fun readNpcAvailability(json: String): Map<String, SkyBlockNpcAvailability> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == NpcAvailabilitySchema.VERSION) {
            "Item List NPC availability has an unsupported schema"
        }
        require(root.hasLinkedSources(NpcAvailabilitySchema.MINIMUM_SOURCE_COUNT)) {
            "Item List NPC availability has invalid source metadata"
        }
        val entities = requireNotNull(root.obj("entities")) { "Item List NPC availability has no entities" }
        return entities.entrySet().associate { (id, element) ->
            require(id.matches(catalogIdPattern)) { "Item List NPC availability has an invalid ID" }
            val value = element.asJsonObject
            val event = value.enumOrNull<SkyBlockEvent>("event")
                ?: error("Item List NPC availability $id references unknown event ${value.string("event")}")
            val source = value.enumOrNull<SkyBlockNpcAvailabilitySource>("source")
                ?: error("Item List NPC availability $id has unknown source ${value.string("source")}")
            val availability = SkyBlockNpcAvailability(
                event = event,
                startsBeforeMinutes = value.get("startsBeforeMinutes")?.asInt ?: -1,
                durationMinutes = value.get("durationMinutes")?.asInt ?: 0,
                page = value.string("page"),
                revision = value.get("revision")?.asLong ?: -1L,
                source = source,
            )
            val isWikiSource = source == SkyBlockNpcAvailabilitySource.INDEPENDENT_WIKI
            val hasWikiProvenance = availability.page.isNotBlank() && availability.revision > 0L
            require(
                availability.startsBeforeMinutes in NpcAvailabilitySchema.MINUTE_RANGE &&
                    availability.durationMinutes in NpcAvailabilitySchema.MINUTE_RANGE &&
                    availability.page.length <= MAXIMUM_TEXT_LENGTH &&
                    availability.revision >= 0L &&
                    isWikiSource == hasWikiProvenance,
            ) { "Item List NPC availability $id has invalid schedule data" }
            id to availability
        }
            .also {
                require(it.size >= NpcAvailabilitySchema.MINIMUM_COUNT) {
                    "Item List NPC availability contains only ${it.size} entities"
                }
            }
    }

    fun readNpcLocations(json: String): Map<String, JsonObject> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == NpcLocationSchema.VERSION) {
            "Item List NPC locations have an unsupported schema"
        }
        require(root.hasLinkedSources(NpcLocationSchema.MINIMUM_SOURCE_COUNT)) {
            "Item List NPC locations have invalid source metadata"
        }
        val entities = requireNotNull(root.obj("entities")) { "Item List NPC locations have no entities" }
        return entities.entrySet().associate { (id, element) ->
            val value = element.asJsonObject
            val location = value.string("location")
            val position = value.obj("position")
            val island = value.get("island")?.asString
            require(
                id.matches(catalogIdPattern) &&
                    location.isNotBlank() && location.length <= MAXIMUM_TEXT_LENGTH &&
                    (position == null || listOf("x", "y", "z").all { position.doubleOrNull(it)?.isFinite() == true }) &&
                    (island == null || island.matches(lowercaseIdPattern)),
            ) { "Item List NPC location $id is invalid" }
            id to value
        }
    }

    private fun readProgressionRequirements(root: JsonObject): Map<String, SkyBlockProgressionRequirement> {
        val entries = requireNotNull(root.array("requirements")) {
            "Item List supplemental data has no progression requirements"
        }
        return buildMap {
            entries.forEach { element ->
                val value = element.asJsonObject
                val itemId = value.string("item")
                val kind = value.enumOrNull<SkyBlockProgressionKind>("kind")
                    ?: error("Item List supplemental requirement $itemId has an invalid kind")
                val name = value.string("name")
                val tier = value.get("tier")?.asInt ?: 0
                val iconKind = value.enumOrNull<SkyBlockProgressionIconKind>("iconKind")
                    ?: error("Item List supplemental requirement $itemId has an invalid icon kind")
                val iconId = value.string("icon")
                val source = value.enumOrNull<SkyBlockProgressionSource>("source")
                    ?: error("Item List supplemental requirement $itemId has an invalid source")
                require(itemId.matches(catalogIdPattern) && iconId.matches(catalogIdPattern)) {
                    "Item List supplemental requirement has an invalid ID"
                }
                require(
                    name.isNotBlank() && name.length <= MAXIMUM_TEXT_LENGTH &&
                        tier in SupplementalLimits.PROGRESSION_TIER_RANGE &&
                        (kind != SkyBlockProgressionKind.SLAYER || name.endsWith(" Slayer")),
                ) {
                    "Item List supplemental requirement $itemId has invalid display data"
                }
                require(
                    put(itemId, SkyBlockProgressionRequirement(kind, name, tier, iconKind, iconId, source)) == null,
                ) {
                    "Item List supplemental data repeats progression requirement $itemId"
                }
            }
        }
    }

    private fun readWarps(root: JsonObject): List<SkyBlockWarpPoint> {
        val entries = requireNotNull(root.array("warps")) { "Item List supplemental data has no warps" }
        val commands = mutableSetOf<String>()
        return buildList {
            entries.forEach { element ->
                val value = element.asJsonObject
                val command = value.string("command")
                val islandId = value.string("island")
                val island = requireNotNull(SkyBlockIsland.getByLocation(islandId, null)) {
                    "Item List supplemental warp has unknown island $islandId"
                }
                val position = WorldVec(value.double("x"), value.double("y"), value.double("z"))
                require(command.matches(lowercaseIdPattern) && commands.add(command) && position.isFinite()) {
                    "Item List supplemental warp has invalid data"
                }
                add(SkyBlockWarpPoint(command, island, position))
            }
        }
    }

    private object SupplementalLimits {
        const val MINIMUM_PROGRESSION_REQUIREMENT_COUNT = 900
        const val MINIMUM_WARP_COUNT = 30
        val PET_LEVEL_RANGE = 2..500
        val PROGRESSION_TIER_RANGE = 1..100
    }

    private object NpcAvailabilitySchema {
        const val VERSION = 1
        const val MINIMUM_COUNT = 10
        const val MINIMUM_SOURCE_COUNT = 2
        val MINUTE_RANGE = 0..24 * 60
    }

    private object NpcLocationSchema {
        const val VERSION = 1
        const val MINIMUM_SOURCE_COUNT = 1
    }

    private object EntityContextSchema {
        const val MINIMUM_COUNT = 150
        const val SOURCE =
            "NotEnoughUpdates/NotEnoughUpdates-REPO constants/bestiary.json + constants/rift_guide.json"
    }
    private val lowercaseIdPattern = Regex("[a-z0-9_]+")
    private val petMapType = object : TypeToken<Map<String, SkyBlockPetInfo>>() {}.type
}

internal data class SupplementalCatalog(
    val progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    val petMaxLevels: Map<String, Int>,
    val warps: List<SkyBlockWarpPoint>,
)
