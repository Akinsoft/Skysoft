package com.skysoft.features.waypoints

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object WaypointJsonImport {
    fun decode(root: JsonElement, source: String?): WaypointImport {
        require(root.isJsonObject || root.isJsonArray) { "Waypoint JSON must contain a preset or a list of points." }
        if (root.isJsonObject && root.asJsonObject.has("version") && root.asJsonObject.has("groups")) {
            val json = root.asJsonObject
            return WaypointImportContext("Skysoft").finish(WaypointJson.decodeGroups(json))
        }
        val detected = source ?: when {
            root.isJsonObject && root.asJsonObject.has("categories") -> "Skytils"
            root.isJsonObject && root.asJsonObject.has("isOrdered") -> "Firmament"
            root.isJsonArray && root.asJsonArray.any { it.isJsonObject && it.asJsonObject.has("options") } -> "SkyHanni / Coleweight"
            else -> "Waypoint JSON"
        }
        val context = WaypointImportContext(detected)
        val groups = if (root.isJsonArray) array(root.asJsonArray, context) else objectGroups(root.asJsonObject, context)
        if (detected != "Skysoft") context.warnings += "Mod-specific display settings are not imported."
        return context.finish(groups)
    }

    private fun array(array: JsonArray, context: WaypointImportContext): List<WaypointGroup> {
        require(array.size() > 0) { "The waypoint list is empty." }
        val first = array[0].asJsonObject
        return if (first.has("waypoints") || first.has("points")) array.map { group(it.asJsonObject, context) }
        else listOf(context.group("Imported route", null, points(array, context), ordered = true))
    }

    private fun objectGroups(json: JsonObject, context: WaypointImportContext): List<WaypointGroup> = when {
        json.has("categories") -> json.getAsJsonArray("categories").map { group(it.asJsonObject, context) }
        json.has("groups") -> json.getAsJsonArray("groups").map { group(it.asJsonObject, context) }
        json.has("waypoints") || json.has("points") -> listOf(group(json, context))
        json.entrySet().isNotEmpty() && json.entrySet().all { it.value.isJsonArray || it.value.isJsonObject } ->
            json.entrySet().flatMap { (name, value) ->
                if (value.isJsonArray) value.asJsonArray.map {
                    group(it.asJsonObject, context, zone = name)
                } else listOf(group(value.asJsonObject, context, defaultName = name))
            }
        else -> error("Unrecognized waypoint JSON. Export ordinary waypoint presets or an ordered route.")
    }

    private fun group(
        json: JsonObject,
        context: WaypointImportContext,
        zone: String? = null,
        defaultName: String = "Imported preset",
    ): WaypointGroup {
        require(!json.has("isRelativeTo") || json.get("isRelativeTo").isJsonNull) {
            "Relative Firmament waypoints require an origin. Export absolute waypoints instead."
        }
        val points = (json.get("waypoints") ?: json.get("points"))?.asJsonArray
            ?: error("A waypoint preset is missing its point list.")
        val ordered = json.isTrue("ordered", json.isTrue("isOrdered", context.source.contains("ordered")))
        return context.group(
            json.string("name") ?: json.string("label") ?: defaultName,
            json.string("island") ?: json.string("zone") ?: zone,
            points(points, context), ordered,
        ).copy(enabled = json.isTrue("enabled", true))
    }

    private fun points(array: JsonArray, context: WaypointImportContext): List<WaypointPoint> {
        require(array.size() <= MAX_WAYPOINT_POINTS) { "Too many waypoints in this preset." }
        val objects = array.map { it.asJsonObject }
        val hasNumberedOptions = objects.isNotEmpty() && objects.all { optionName(it)?.toIntOrNull() != null }
        val ordered = if (hasNumberedOptions) objects.sortedBy { optionName(it)!!.toInt() } else objects
        if (hasNumberedOptions) context.warnings += "Numbered option names determine route order."
        return ordered.map { point(it, context) }
    }

    private fun point(json: JsonObject, context: WaypointImportContext): WaypointPoint {
        val position = listOf("pos", "coords", "position", "location").firstNotNullOfOrNull { json.get(it) }
        val coordinates = when {
            position?.isJsonArray == true -> position.asJsonArray.also { require(it.size() == AXES.size) }.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Coordinates must be numbers." }
                it.asDouble
            }
            position?.isJsonObject == true -> AXES.map { position.asJsonObject.requiredNumber(it) }
            else -> AXES.map { json.requiredNumber(it) }
        }
        val name = optionName(json) ?: componentText(json.get("name") ?: json.get("label"))
        val radius = when {
            json.has("radius") -> context.radius(json.requiredNumber("radius"))
            json.has("r") && !json.has("g") && !json.has("b") && position == null -> context.radius(json.requiredNumber("r"))
            else -> null
        }
        return WaypointPoint(
            name = context.name(name), x = coordinates[0], y = coordinates[1], z = coordinates[2],
            enabled = json.isTrue("enabled", json.isTrue("shouldRender", true)), color = color(json), radius = radius
        )
    }

    private fun componentText(element: JsonElement?): String = when {
        element == null || element.isJsonNull -> ""
        element.isJsonPrimitive -> element.asString
        element.isJsonArray -> element.asJsonArray.joinToString("") { componentText(it) }
        else -> element.asJsonObject.let { it.string("text").orEmpty() + componentText(it.get("extra")) }
    }

    private fun optionName(json: JsonObject): String? = json.getAsJsonObject("options")?.get("name")?.let {
        require(it.isJsonPrimitive) { "Waypoint option name must be text or a number." }
        it.asString
    }

    private fun color(json: JsonObject): Int? {
        json.get("color")?.let { value ->
            require(value.isJsonPrimitive) { "Waypoint color must be an RGB value." }
            if (value.asJsonPrimitive.isNumber) {
                val number = json.requiredNumber("color")
                require(number in Int.MIN_VALUE.toDouble()..MAX_ARGB.toDouble() && number == number.toLong().toDouble()) {
                    "Waypoint color must be a whole RGB or ARGB value."
                }
                return number.toLong().toInt() and WaypointValidation.MAX_RGB
            }
            val text = value.asString.removePrefix("#").removePrefix("0x")
            val components = text.split(':')
            return if (components.size == SKYTILS_COMPONENTS) {
                rgb(components.takeLast(AXES.size).map { it.toInt(HEX) })
            } else {
                require((text.length == RGB_DIGITS || text.length == ARGB_DIGITS) && text.all { it.digitToIntOrNull(HEX) != null }) {
                    "Use an RGB or ARGB hex color."
                }
                text.toLong(HEX).toInt() and WaypointValidation.MAX_RGB
            }
        }
        json.getAsJsonArray("colorComponents")?.let { values ->
            require(values.size() >= AXES.size) { "RGB color requires three components." }
            return rgb(values.take(AXES.size).map { normalizedChannel(it.asDouble) })
        }
        if (listOf("r", "g", "b").all(json::has)) {
            val values = listOf("r", "g", "b").map { json.requiredNumber(it) }
            return rgb(
                if (json.has("options")) values.map(::normalizedChannel) else values.map {
                    require(it == it.toInt().toDouble()) { "RGB channels must be whole numbers." }
                    it.toInt()
                }
            )
        }
        return null
    }

    private fun normalizedChannel(value: Double): Int {
        require(value.isFinite() && value in 0.0..1.0) { "Normalized RGB channels must be between 0 and 1." }
        return kotlin.math.round(value * CHANNEL_MAX).toInt()
    }

    private fun rgb(channels: List<Int>): Int {
        require(channels.all { it in 0..CHANNEL_MAX }) { "RGB channels must be between 0 and 255." }
        return (channels[0] shl RED_SHIFT) or (channels[1] shl GREEN_SHIFT) or channels[2]
    }

    private val AXES = listOf("x", "y", "z")
    private const val MAX_ARGB = 0xFFFFFFFFL
    private const val RGB_DIGITS = 6
    private const val ARGB_DIGITS = 8
    private const val SKYTILS_COMPONENTS = 5
    private const val HEX = 16
    private const val CHANNEL_MAX = 255
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
