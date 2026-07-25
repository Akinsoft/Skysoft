package com.skysoft.utils.serialization

import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class ShareableConfigEnvelope(
    val type: String,
    val schemaVersion: Int,
    val payload: String,
)

internal object ShareableConfigCodec {
    fun encode(type: String, schemaVersion: Int, payload: String): String {
        require(type.matches(TYPE_PATTERN)) { "Invalid shared configuration type." }
        require(schemaVersion > 0) { "Invalid shared configuration version." }
        val json = GSON.toJson(SerializedEnvelope(FORMAT_VERSION, type, schemaVersion, payload))
        val compressed = ByteArrayOutputStream().use { bytes ->
            GZIPOutputStream(bytes).use { gzip -> gzip.write(json.toByteArray(StandardCharsets.UTF_8)) }
            bytes.toByteArray()
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decode(value: String): ShareableConfigEnvelope {
        val encoded = value.trim()
        require(encoded.startsWith(PREFIX)) { "Clipboard does not contain a Skysoft configuration." }
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Shared configuration is too large." }
        val compressed = decodeBase64(encoded.substring(PREFIX.length))
        val json = decompressConfiguration(compressed)
        val envelope = deserializeEnvelope(json)
        require(envelope.formatVersion == FORMAT_VERSION) { "This shared configuration format is not supported." }
        require(envelope.type.matches(TYPE_PATTERN)) { "Shared configuration type is invalid." }
        require(envelope.schemaVersion > 0) { "Shared configuration version is invalid." }
        return ShareableConfigEnvelope(envelope.type, envelope.schemaVersion, envelope.payload)
    }

    private fun decodeBase64(encoded: String): ByteArray = runCatching {
        Base64.getUrlDecoder().decode(encoded)
    }.getOrElse {
        throw IllegalArgumentException("Shared configuration is not valid.", it)
    }

    private fun decompressConfiguration(compressed: ByteArray): String = runCatching {
        decompress(compressed)
    }.getOrElse {
        throw IllegalArgumentException("Shared configuration is not valid.", it)
    }

    private fun deserializeEnvelope(json: String): SerializedEnvelope = runCatching {
        GSON.fromJson(json, SerializedEnvelope::class.java)
    }.getOrElse {
        throw IllegalArgumentException("Shared configuration is not valid.", it)
    } ?: throw IllegalArgumentException("Shared configuration is not valid.")

    private fun decompress(compressed: ByteArray): String {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DECOMPRESSED_LENGTH) { "Shared configuration is too large." }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(StandardCharsets.UTF_8)
    }

    private data class SerializedEnvelope(
        val formatVersion: Int,
        val type: String,
        val schemaVersion: Int,
        val payload: String,
    )

    private val GSON = GsonBuilder().disableHtmlEscaping().create()
    private val TYPE_PATTERN = Regex("[a-z][a-z0-9_.-]{0,63}")
    private const val PREFIX = "SKYSOFT:"
    private const val FORMAT_VERSION = 1
    private const val BUFFER_SIZE = 8_192
    private const val MAX_ENCODED_LENGTH = 1_000_000
    private const val MAX_DECOMPRESSED_LENGTH = 2_000_000
}
