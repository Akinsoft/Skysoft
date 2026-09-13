package com.skysoft.features.waypoints

import com.skysoft.SkysoftMod
import com.skysoft.config.SkysoftConfigFiles
import com.skysoft.data.BackgroundSave
import com.skysoft.utils.SkysoftClientEvents
import java.nio.file.Files

internal object WaypointLibrary {
    private val path = SkysoftConfigFiles.directory.resolve("waypoints.json")
    private var loaded = false
    private var content: List<WaypointGroup> = emptyList()
    private var encodedContent = WaypointJson.encodeLibrary(emptyList())
    var loadError: String? = null
        private set
    @Volatile
    var saveError: String? = null
        private set
    private val saves = BackgroundSave(
        name = "Waypoints",
        prepare = { encodedContent },
        write = ::write,
        canSave = { loadError == null },
    )

    val groups: List<WaypointGroup>
        get() {
            ensureLoaded()
            return content
        }

    fun register() {
        SkysoftClientEvents.onEndTick("Waypoint saves", { saves.hasUnsavedChanges && loadError == null }) {
            saves.saveIfDue()
        }
        SkysoftClientEvents.onDisconnect("Waypoint save on disconnect") { saves.flush() }
        SkysoftClientEvents.onClientStopping("Waypoint save on exit") { saves.flush() }
    }

    fun replace(groups: List<WaypointGroup>) {
        ensureLoaded()
        check(loadError == null) { loadError.orEmpty() }
        WaypointValidation.validate(groups)
        if (content == groups) return
        val encoded = WaypointJson.encodeLibrary(groups.filter { it.scope != WaypointScope.VISIT })
        require(encoded.toByteArray(Charsets.UTF_8).size <= WaypointJson.MAX_JSON_BYTES) {
            "The waypoint library would exceed 8 MB. Remove points or shorten names first."
        }
        content = groups.toList()
        encodedContent = encoded
        saves.markDirty()
        saves.saveInBackground()
    }

    fun save() = saves.saveInBackground()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.exists(path)) return
        try {
            require(Files.size(path) <= WaypointJson.MAX_JSON_BYTES) { "Waypoint file is too large." }
            val encoded = Files.readString(path)
            content = WaypointJson.decodeLibrary(encoded)
            encodedContent = encoded
        } catch (failure: Exception) {
            loadError = "Could not load waypoints.json: ${failure.message}. Your file has not been changed."
            SkysoftMod.LOGGER.error("Failed to load waypoints from $path", failure)
        }
    }

    private fun write(json: String) {
        try {
            SkysoftConfigFiles.writeStringSafely(path, json)
            saveError = null
        } catch (failure: Exception) {
            saveError = "Waypoints could not be saved: ${failure.message}"
            throw failure
        }
    }
}

internal object WaypointValidation {
    fun validate(groups: List<WaypointGroup>) {
        require(groups.size <= MAX_WAYPOINT_GROUPS) { "A library can contain at most $MAX_WAYPOINT_GROUPS presets." }
        require(groups.sumOf { it.points.size } <= MAX_WAYPOINT_POINTS) {
            "A library can contain at most $MAX_WAYPOINT_POINTS points."
        }
        require(groups.map { it.id }.distinct().size == groups.size) { "Preset IDs must be unique." }
        groups.forEach(::validateGroup)
    }

    private fun validateGroup(group: WaypointGroup) {
        require(group.id.isNotBlank()) { "Preset ID is missing." }
        require(group.name.isNotBlank() && group.name.length <= MAX_WAYPOINT_NAME) { "Preset name is invalid." }
        require(group.name.none { it < ' ' || it == '\u007f' || it == '§' }) {
            "Preset names cannot contain control characters or formatting."
        }
        require(group.color in 0..MAX_RGB) { "Preset color is invalid." }
        validateRadius(group.radius)
        require(group.points.map { it.id }.distinct().size == group.points.size) { "Point IDs must be unique." }
        require(group.scope != WaypointScope.PROFILE || !group.profile.isNullOrBlank()) { "Profile is missing." }
        require(group.scope != WaypointScope.VISIT || !group.visit.isNullOrBlank()) { "Visit is missing." }
        group.points.forEach { point ->
            require(point.id.isNotBlank() && point.name.length <= MAX_WAYPOINT_NAME) { "Point name or ID is invalid." }
            require(point.name.none { it < ' ' || it == '\u007f' || it == '§' }) {
                "Point names cannot contain control characters or formatting."
            }
            require(listOf(point.x, point.y, point.z).all { it.isFinite() && kotlin.math.abs(it) <= MAX_WAYPOINT_COORDINATE }) {
                "Waypoint coordinates must be finite and within world bounds."
            }
            require(point.color == null || point.color in 0..MAX_RGB) { "Point color is invalid." }
            require(point.chromaMillis == 0 || point.color != null && point.chromaMillis in MIN_CHROMA_MILLIS..MAX_CHROMA_MILLIS) {
                "Animated waypoint colors must cycle between 1 and 60 seconds."
            }
            point.radius?.let(::validateRadius)
        }
    }

    fun validateRadius(radius: Double?): Double {
        require(radius != null && radius.isFinite() && radius in MIN_WAYPOINT_RADIUS..MAX_WAYPOINT_RADIUS) {
            "Arrival distance must be between $MIN_WAYPOINT_RADIUS and $MAX_WAYPOINT_RADIUS blocks."
        }
        return radius
    }

    const val MAX_RGB = 0xFFFFFF
    private const val MIN_CHROMA_MILLIS = 1000
    private const val MAX_CHROMA_MILLIS = 60000
}
