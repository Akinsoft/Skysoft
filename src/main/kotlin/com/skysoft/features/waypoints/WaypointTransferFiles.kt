package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigFiles
import java.nio.file.Files
import java.nio.file.Path
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs

internal object WaypointTransferFiles {
    fun read(): String? {
        val selected = MemoryStack.stackPush().use { stack ->
            val filters = stack.mallocPointer(2).put(stack.UTF8("*.json")).put(stack.UTF8("*.txt")).flip()
            TinyFileDialogs.tinyfd_openFileDialog("Import Waypoints", "", filters, "Waypoint JSON or share text", false)
        } ?: return null
        val path = Path.of(selected)
        return WaypointImportCodec.utf8(WaypointImportCodec.bounded(Files.newInputStream(path)))
    }

    fun save(json: String): Path? {
        require(json.toByteArray(Charsets.UTF_8).size <= WaypointJson.MAX_JSON_BYTES) { "Waypoint export exceeds 8 MB." }
        val selected = MemoryStack.stackPush().use { stack ->
            val filters = stack.mallocPointer(1).put(stack.UTF8("*.json")).flip()
            TinyFileDialogs.tinyfd_saveFileDialog("Export Waypoints", "Skysoft-waypoints.json", filters, "Waypoint JSON")
        } ?: return null
        val path = Path.of(selected)
        SkysoftConfigFiles.writeStringSafely(path, json)
        return path
    }
}
