package com.skysoft.data.skyblock

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.skysoft.data.SkyBlockIsland
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
            require(id.matches(entityIdPattern) && value.asInt in SupplementalLimits.PET_LEVEL_RANGE) {
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
            require(id.matches(entityIdPattern)) { "Item List entity context has an invalid ID" }
            val details = value.asJsonArray.map { it.asString }
            require(
                details.isNotEmpty() && details.all {
                    it.isNotBlank() && it.length <= SharedLimits.MAXIMUM_TEXT_LENGTH
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
        val sources = root.array("sources")?.toList().orEmpty()
        require(
            sources.size >= NpcAvailabilitySchema.MINIMUM_SOURCE_COUNT && sources.all { element ->
                val value = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
                value.string("name").isNotBlank() && value.string("url").startsWith("https://")
            },
        ) { "Item List NPC availability has invalid source metadata" }
        val entities = requireNotNull(root.obj("entities")) { "Item List NPC availability has no entities" }
        return entities.entrySet().associate { (id, element) ->
            require(id.matches(entityIdPattern)) { "Item List NPC availability has an invalid ID" }
            val value = element.asJsonObject
            val eventId = value.string("event")
            val event = runCatching { SkyBlockEvent.valueOf(eventId) }.getOrNull()
                ?: error("Item List NPC availability $id references unknown event $eventId")
            val sourceId = value.string("source")
            val source = runCatching { SkyBlockNpcAvailabilitySource.valueOf(sourceId) }.getOrNull()
                ?: error("Item List NPC availability $id has unknown source $sourceId")
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
                    availability.page.length <= SharedLimits.MAXIMUM_TEXT_LENGTH &&
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

    fun readObtainSources(json: String): Map<String, SkyBlockObtainInfo> {
        require(json.length in ObtainSchema.SIZE_RANGE) {
            "Item List obtain data has an invalid size"
        }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == ObtainSchema.VERSION) {
            "Item List obtain data has an unsupported schema"
        }
        val sources = root.array("sources")?.toList().orEmpty()
        require(
            sources.size >= ObtainSchema.MINIMUM_SOURCE_COUNT && sources.all { element ->
                val source = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
                source.string("name").isNotBlank() && source.string("url").startsWith("https://") &&
                    source.string("license").isNotBlank()
            },
        ) {
            "Item List obtain data has invalid attribution"
        }
        val items = requireNotNull(root.obj("items")) { "Item List obtain data has no items" }
        require(items.size() >= ObtainSchema.MINIMUM_COUNT) {
            "Item List obtain data contains only ${items.size()} entries"
        }
        return items.entrySet().associate { (id, element) ->
            require(id.matches(entityIdPattern) && element.isJsonObject) {
                "Item List obtain data has an invalid item ID"
            }
            val value = element.asJsonObject
            val status = runCatching { SkyBlockObtainStatus.valueOf(value.string("status")) }.getOrNull()
                ?: error("Item List obtain source $id has an invalid status")
            val sourceKind = runCatching { SkyBlockObtainSource.valueOf(value.string("source")) }.getOrNull()
                ?: error("Item List obtain source $id has an invalid source")
            val summary = value.string("summary")
            val page = value.string("page")
            val revision = value.get("revision")?.asLong ?: -1L
            val sourceItemId = value.string("sourceItem").takeIf(String::isNotBlank)
            require(
                summary.isNotBlank() && summary.length <= ObtainSchema.MAXIMUM_SUMMARY_LENGTH &&
                    !ObtainSchema.RAW_WIKI_MARKUP.containsMatchIn(summary),
            ) {
                "Item List obtain source $id has an invalid summary"
            }
            require(page.length <= SharedLimits.MAXIMUM_TEXT_LENGTH && revision >= 0L) {
                "Item List obtain source $id has invalid provenance"
            }
            require(sourceItemId == null || sourceItemId.matches(entityIdPattern)) {
                "Item List obtain source $id has an invalid source item"
            }
            require((status == SkyBlockObtainStatus.UNKNOWN) == (sourceKind == SkyBlockObtainSource.UNKNOWN)) {
                "Item List obtain source $id has inconsistent unknown state"
            }
            val context = value.obj("context")?.let { contextValue ->
                val contextSource = runCatching {
                    SkyBlockObtainSource.valueOf(contextValue.string("source"))
                }.getOrNull() ?: error("Item List obtain source $id has an invalid context source")
                SkyBlockObtainContext(
                    label = contextValue.string("label"),
                    page = contextValue.string("page"),
                    revision = contextValue.get("revision")?.asLong ?: -1L,
                    source = contextSource,
                    url = contextValue.string("url"),
                ).also { context ->
                    require(
                        context.label.isNotBlank() && context.label.length <= SharedLimits.MAXIMUM_TEXT_LENGTH &&
                            context.page.isNotBlank() && context.page.length <= SharedLimits.MAXIMUM_TEXT_LENGTH &&
                            context.revision > 0L &&
                            context.source in contextSources &&
                            context.url.startsWith(context.source.wikiBaseUrl()),
                    ) { "Item List obtain source $id has invalid context provenance" }
                }
            }
            id to SkyBlockObtainInfo(status, summary, page, revision, sourceKind, sourceItemId, context)
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
                val kind = runCatching { SkyBlockProgressionKind.valueOf(value.string("kind")) }.getOrNull()
                    ?: error("Item List supplemental requirement $itemId has an invalid kind")
                val name = value.string("name")
                val tier = value.get("tier")?.asInt ?: 0
                val iconKind = runCatching {
                    SkyBlockProgressionIconKind.valueOf(value.string("iconKind"))
                }.getOrNull() ?: error("Item List supplemental requirement $itemId has an invalid icon kind")
                val iconId = value.string("icon")
                val source = runCatching {
                    SkyBlockProgressionSource.valueOf(value.string("source"))
                }.getOrNull() ?: error("Item List supplemental requirement $itemId has an invalid source")
                require(itemId.matches(entityIdPattern) && iconId.matches(entityIdPattern)) {
                    "Item List supplemental requirement has an invalid ID"
                }
                require(
                    name.isNotBlank() && name.length <= SharedLimits.MAXIMUM_TEXT_LENGTH &&
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
                val position = WorldVec(
                    value.coordinate("x"),
                    value.coordinate("y"),
                    value.coordinate("z"),
                )
                require(command.matches(commandPattern) && commands.add(command) && position.isFinite()) {
                    "Item List supplemental warp has invalid data"
                }
                add(SkyBlockWarpPoint(command, island, position))
            }
        }
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.coordinate(name: String): Double = get(name)?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0

    private object SupplementalLimits {
        const val MINIMUM_PROGRESSION_REQUIREMENT_COUNT = 900
        const val MINIMUM_WARP_COUNT = 30
        val PET_LEVEL_RANGE = 2..500
        val PROGRESSION_TIER_RANGE = 1..100
    }

    private object SharedLimits {
        const val MAXIMUM_TEXT_LENGTH = 128
    }

    private object NpcAvailabilitySchema {
        const val VERSION = 1
        const val MINIMUM_COUNT = 10
        const val MINIMUM_SOURCE_COUNT = 2
        val MINUTE_RANGE = 0..24 * 60
    }

    private object ObtainSchema {
        const val VERSION = 2
        const val MINIMUM_COUNT = 5_000
        const val MINIMUM_SOURCE_COUNT = 3
        const val MAXIMUM_SUMMARY_LENGTH = 600
        val SIZE_RANGE = 500_000..4_000_000
        val RAW_WIKI_MARKUP = Regex(
            """(?:\{\||\|\}|\{\{|\[\[|\]\]|wikitable|tabber|^\s*\|[a-z][\w.-]*\s*=|""" +
                """(?:^|\s)(?:class|rowspan|colspan|style)\s*=|={2,}\s*[^=]+\s*={2,}|\|-\|)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
    }

    private object EntityContextSchema {
        const val MINIMUM_COUNT = 150
        const val SOURCE =
            "NotEnoughUpdates/NotEnoughUpdates-REPO constants/bestiary.json + constants/rift_guide.json"
    }
    private val entityIdPattern = Regex("[A-Z0-9_;.\\-]+")
    private val commandPattern = Regex("[a-z0-9_]+")
    private val contextSources = setOf(SkyBlockObtainSource.INDEPENDENT_WIKI)
    private val petMapType = object : TypeToken<Map<String, SkyBlockPetInfo>>() {}.type
}

internal data class SupplementalCatalog(
    val progressionRequirements: Map<String, SkyBlockProgressionRequirement>,
    val petMaxLevels: Map<String, Int>,
    val warps: List<SkyBlockWarpPoint>,
)

private fun SkyBlockObtainSource.wikiBaseUrl(): String = when (this) {
    SkyBlockObtainSource.INDEPENDENT_WIKI -> SKYBLOCK_WIKI_PAGE_URL
    else -> ""
}
