package com.skysoft.features.screenshot

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import net.fabricmc.loader.api.FabricLoader

internal object ScreenshotUploadMetadataStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val uploadListType = object : TypeToken<List<StoredScreenshotUpload>>() {}.type
    private val metadataPath = FabricLoader.getInstance().configDir
        .resolve("skysoft")
        .resolve("screenshot-uploads.json")
    private var records: MutableMap<String, StoredScreenshotUpload>? = null

    @Synchronized
    fun uploadFor(path: Path): ScreenshotUpload? {
        val record = records().get(normalizedPath(path)) ?: return null
        if (record.expiresAtEpochSecond <= Instant.now().epochSecond) return null
        return record.upload()
    }

    @Synchronized
    fun remember(path: Path, upload: ScreenshotUpload) {
        records()[normalizedPath(path)] = StoredScreenshotUpload.from(path, upload)
        save()
    }

    @Synchronized
    fun screenshotForUrl(url: String): Path? {
        val now = Instant.now().epochSecond
        val record = records().values.firstOrNull {
            (it.imageUrl == url || it.pageUrl == url) && it.expiresAtEpochSecond > now
        } ?: return null
        return Path.of(record.screenshotPath).takeIf(Files::isRegularFile)
    }

    private fun records(): MutableMap<String, StoredScreenshotUpload> {
        records?.let { return it }
        val loaded = if (Files.isRegularFile(metadataPath)) {
            Files.newBufferedReader(metadataPath).use { reader ->
                gson.fromJson<List<StoredScreenshotUpload>>(reader, uploadListType).orEmpty()
            }
        } else {
            emptyList()
        }
        return loaded.associateByTo(linkedMapOf()) { it.screenshotPath }.also { records = it }
    }

    private fun save() {
        Files.createDirectories(metadataPath.parent)
        val temporaryPath = metadataPath.resolveSibling("${metadataPath.fileName}.tmp")
        Files.newBufferedWriter(temporaryPath).use { writer ->
            gson.toJson(records().values.toList(), uploadListType, writer)
        }
        Files.move(
            temporaryPath,
            metadataPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun normalizedPath(path: Path): String = path.toAbsolutePath().normalize().toString()
}

private data class StoredScreenshotUpload(
    val screenshotPath: String,
    val imageUrl: String,
    val pageUrl: String,
    val deleteUrl: String,
    val width: Int,
    val height: Int,
    val expiresAtEpochSecond: Long,
) {
    fun upload(): ScreenshotUpload = ScreenshotUpload(
        imageUrl = imageUrl,
        pageUrl = pageUrl,
        deleteUrl = deleteUrl,
        width = width,
        height = height,
        expiresAtEpochSecond = expiresAtEpochSecond,
    )

    companion object {
        fun from(path: Path, upload: ScreenshotUpload): StoredScreenshotUpload = StoredScreenshotUpload(
            screenshotPath = path.toAbsolutePath().normalize().toString(),
            imageUrl = upload.imageUrl,
            pageUrl = upload.pageUrl,
            deleteUrl = upload.deleteUrl,
            width = upload.width,
            height = upload.height,
            expiresAtEpochSecond = upload.expiresAtEpochSecond,
        )
    }
}
