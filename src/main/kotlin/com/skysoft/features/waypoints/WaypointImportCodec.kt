package com.skysoft.features.waypoints

import com.skysoft.utils.serialization.ShareableConfigCodec
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.GZIPInputStream
import org.brotli.dec.BrotliInputStream

internal object WaypointImportCodec {
    fun decode(text: String): WaypointImport {
        require(text.length <= WaypointJson.MAX_JSON_BYTES) { "Import is too large (maximum 8 MB)." }
        val value = text.trim().removePrefix("\uFEFF").let {
            if (it.startsWith("```") && it.endsWith("```")) it.substringAfter('\n').removeSuffix("```").trim() else it
        }
        require(value.isNotBlank()) { "Paste a waypoint share or choose a file first." }
        return when {
            value.startsWith("SKYSOFT:") -> native(value)
            value.startsWith("WP:") -> WaypointerImport.decode(value)
            value.startsWith("WPL:") -> error("Only Waypointer V10 route shares are supported. Export a WP: share from Waypointer.")
            value.startsWith("WPC:") || value.startsWith("WPD:") ->
                error("Import an ordinary route; settings and dungeon rooms are not supported.")
            value.startsWith(SKYTILS) -> skytils(value)
            value.startsWith(SKYBLOCKER) -> json(utf8(gzip(base64(value.removePrefix(SKYBLOCKER)))), "Skyblocker")
            value.startsWith(FIRMAMENT) -> json(value.removePrefix(FIRMAMENT), "Firmament")
            value.startsWith('{') || value.startsWith('[') -> json(value)
            else -> binary(base64(value))
        }
    }

    fun export(groups: List<WaypointGroup>): String {
        val encoded = ShareableConfigCodec.encode("waypoints", WaypointJson.VERSION, exportJson(groups))
        try {
            ShareableConfigCodec.decode(encoded)
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException("This library is too large for a share code. Use Save JSON file.", failure)
        }
        return encoded
    }

    fun exportJson(groups: List<WaypointGroup>): String = WaypointJson.encodeLibrary(
        groups.map {
            it.copy(scope = WaypointScope.ISLAND, profile = null, visit = null)
        }
    )

    private fun native(value: String): WaypointImport {
        val envelope = ShareableConfigCodec.decode(value)
        require(envelope.type == "waypoints" && envelope.schemaVersion in 1..WaypointJson.VERSION) {
            "This Skysoft share does not contain a supported waypoint library."
        }
        return json(envelope.payload, "Skysoft")
    }

    private fun skytils(value: String): WaypointImport {
        val version = value.substringAfter(SKYTILS).substringBefore("):")
        require(value.contains("):")) { "Skytils share header is incomplete." }
        require(version == "2") { "Only Skytils V2 waypoint shares are supported. Export from the latest Skytils." }
        val bytes = base64(value.substringAfter("):"))
        return json(utf8(bounded(BrotliInputStream(ByteArrayInputStream(bytes)))), "Skytils V2")
    }

    private fun binary(bytes: ByteArray): WaypointImport = when {
        bytes.size >= 2 && bytes[0] == GZIP_FIRST && bytes[1] == GZIP_SECOND -> json(utf8(gzip(bytes)))
        bytes.firstOrNull() == SOOPY_VERSION -> WaypointSoopyImport.decode(bytes)
        else -> {
            val text = utf8(bytes)
            if (text.startsWith(FIRMAMENT)) json(text.removePrefix(FIRMAMENT), "Firmament") else json(text)
        }
    }

    private fun json(value: String, source: String? = null): WaypointImport =
        WaypointJsonImport.decode(WaypointJsonInput.parse(value), source)

    private fun base64(value: String): ByteArray = Base64.getDecoder().decode(value.trim().replace('-', '+').replace('_', '/'))

    private fun gzip(bytes: ByteArray): ByteArray = bounded(GZIPInputStream(ByteArrayInputStream(bytes)))

    fun bounded(input: InputStream): ByteArray = input.use {
        val bytes = it.readNBytes(WaypointJson.MAX_JSON_BYTES.toInt() + 1)
        require(bytes.size <= WaypointJson.MAX_JSON_BYTES) { "Decompressed import is too large (maximum 8 MB)." }
        bytes
    }

    fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private const val SKYTILS = "<Skytils-Waypoint-Data>(V"
    private const val SKYBLOCKER = "[Skyblocker-Waypoint-Data-V1]"
    private const val FIRMAMENT = "FIRM_WAYPOINTS/"
    private const val GZIP_FIRST: Byte = 0x1f
    private const val GZIP_SECOND: Byte = -117
    private const val SOOPY_VERSION: Byte = 1
}
