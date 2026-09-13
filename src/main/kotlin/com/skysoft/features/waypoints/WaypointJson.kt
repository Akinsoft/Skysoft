package com.skysoft.features.waypoints

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skysoft.data.SkyBlockIsland
import java.util.UUID

internal object WaypointJson {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    const val MAX_JSON_BYTES = 8L * 1024 * 1024
    const val VERSION = 2

    fun encodeLibrary(groups: List<WaypointGroup>): String = gson.toJson(
        JsonObject().apply {
            addProperty("version", VERSION)
            add("groups", JsonArray().apply { groups.forEach { add(writeGroup(it)) } })
        }
    )

    fun decodeLibrary(json: String): List<WaypointGroup> {
        val root = WaypointJsonInput.parse(json).asJsonObject
        return decodeGroups(root, preserveIdentity = true)
    }

    fun decodeGroups(root: JsonObject, preserveIdentity: Boolean = false): List<WaypointGroup> {
        val version = root.requiredInt("version")
        require(version in 1..VERSION) { "Unsupported waypoint library version." }
        val array = root.requiredArray("groups")
        require(array.size() <= MAX_WAYPOINT_GROUPS) { "Too many waypoint presets." }
        require(array.sumOf { it.asJsonObject.requiredArray("points").size() } <= MAX_WAYPOINT_POINTS) { "Too many waypoints." }
        if (version == 1) array.forEach { element ->
            val group = element.asJsonObject
            migrateStyle(group, WaypointStyle.MARKER)
            group.requiredArray("points").forEach { migrateStyle(it.asJsonObject, null) }
        }
        return array.map { readGroup(it.asJsonObject, preserveIdentity) }.also(WaypointValidation::validate)
    }

    private fun migrateStyle(json: JsonObject, default: WaypointStyle?) {
        require(!json.has("styles")) { "Combined waypoint styles require version 2." }
        val style = json.string("style")?.let(WaypointStyle::valueOf) ?: default ?: return
        val styles = if (style == WaypointStyle.BEACON) setOf(WaypointStyle.MARKER, style) else setOf(style)
        json.add("styles", writeStyles(styles))
        json.remove("style")
    }

    private fun writeStyles(styles: Set<WaypointStyle>): JsonArray = JsonArray().apply {
        WaypointStyle.entries.filter { it in styles }.forEach { add(it.name) }
    }

    private fun readStyles(json: JsonObject): Set<WaypointStyle> {
        val styles = json.requiredArray("styles").map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Waypoint styles must be text." }
            WaypointStyle.valueOf(value.asString)
        }
        require(styles.distinct().size == styles.size) { "Waypoint styles must be unique." }
        return styles.toSet()
    }

    private fun writeGroup(group: WaypointGroup): JsonObject = JsonObject().apply {
        addProperty("id", group.id)
        addProperty("name", group.name)
        addProperty("island", group.island.name)
        addProperty("enabled", group.enabled)
        addProperty("route", group.route)
        addProperty("loop", group.loop)
        addProperty("skipAhead", group.skipAhead)
        addProperty("radius", group.radius)
        addProperty("color", group.color)
        add("styles", writeStyles(group.styles))
        addProperty("filled", group.filled)
        addProperty("lines", group.lines)
        addProperty("scope", group.scope.name)
        group.profile?.let { addProperty("profile", it) }
        group.visit?.let { addProperty("visit", it) }
        add("points", JsonArray().apply { group.points.forEach { add(writePoint(it)) } })
    }

    private fun writePoint(point: WaypointPoint): JsonObject = JsonObject().apply {
        addProperty("id", point.id)
        addProperty("name", point.name)
        addProperty("x", point.x)
        addProperty("y", point.y)
        addProperty("z", point.z)
        addProperty("enabled", point.enabled)
        point.color?.let { addProperty("color", it) }
        if (point.chromaMillis != 0) addProperty("chromaMillis", point.chromaMillis)
        point.styles?.let { add("styles", writeStyles(it)) }
        point.radius?.let { addProperty("radius", it) }
    }

    private fun readGroup(json: JsonObject, preserveIdentity: Boolean): WaypointGroup = WaypointGroup(
        id = if (preserveIdentity) json.requiredString("id") else UUID.randomUUID().toString(),
        name = json.requiredString("name"),
        island = requireNotNull(SkyBlockIsland.getByConditionValue(json.requiredString("island"))) { "Unknown waypoint island." },
        points = json.requiredArray("points").map { readPoint(it.asJsonObject, preserveIdentity) },
        enabled = json.isTrue("enabled", true),
        route = json.isTrue("route", false),
        loop = json.isTrue("loop", false),
        skipAhead = json.isTrue("skipAhead", false),
        radius = json.number("radius", DEFAULT_WAYPOINT_RADIUS),
        color = json.optionalInt("color") ?: DEFAULT_WAYPOINT_COLOR,
        styles = readStyles(json),
        filled = json.isTrue("filled", false),
        lines = json.isTrue("lines", true),
        scope = if (preserveIdentity) WaypointScope.valueOf(json.requiredString("scope")) else WaypointScope.ISLAND,
        profile = json.string("profile").takeIf { preserveIdentity },
        visit = json.string("visit").takeIf { preserveIdentity },
    )

    private fun readPoint(json: JsonObject, preserveIdentity: Boolean): WaypointPoint = WaypointPoint(
        id = if (preserveIdentity) json.requiredString("id") else UUID.randomUUID().toString(),
        name = json.requiredString("name"),
        x = json.requiredNumber("x"),
        y = json.requiredNumber("y"),
        z = json.requiredNumber("z"),
        enabled = json.isTrue("enabled", true),
        color = json.optionalInt("color"),
        chromaMillis = json.optionalInt("chromaMillis") ?: 0,
        styles = if (json.has("styles")) readStyles(json) else null,
        radius = if (json.has("radius")) json.requiredNumber("radius") else null,
    )
}

internal fun JsonObject.requiredArray(key: String): JsonArray {
    val value = get(key)
    require(value != null && value.isJsonArray) { "$key must be a list." }
    return value.asJsonArray
}

internal fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
    require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "$key must be text." }
    it.asString
}

internal fun JsonObject.requiredString(key: String): String = string(key)
    ?: throw IllegalArgumentException("Missing $key.")

internal fun JsonObject.requiredNumber(key: String): Double {
    val value = get(key) ?: throw IllegalArgumentException("Missing $key.")
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be a number." }
    return value.asDouble.also { require(it.isFinite()) { "$key must be finite." } }
}

internal fun JsonObject.number(key: String, default: Double): Double = if (has(key)) requiredNumber(key) else default

internal fun JsonObject.requiredInt(key: String): Int = requiredNumber(key).let { number ->
    require(number == number.toInt().toDouble()) { "$key must be a whole number." }
    number.toInt()
}

internal fun JsonObject.optionalInt(key: String): Int? = if (has(key)) requiredInt(key) else null

internal fun JsonObject.isTrue(key: String, default: Boolean): Boolean {
    val value = get(key) ?: return default
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key must be true or false." }
    return value.asBoolean
}
