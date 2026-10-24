package com.skysoft.features.screenshot

import com.skysoft.utils.FileDialogFilter
import com.skysoft.utils.MinecraftFileDialogs
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal data class ScreenshotEntry(
    val path: Path,
    val fileName: String,
    val modifiedAtMillis: Long,
)

internal object ScreenshotRepository {
    fun list(directory: Path): List<ScreenshotEntry> {
        Files.createDirectories(directory)
        return Files.list(directory).use { paths ->
            paths.filter(::isScreenshot)
                .map(::entry)
                .sorted(ENTRY_ORDER)
                .toList()
        }
    }

    fun entry(path: Path): ScreenshotEntry = ScreenshotEntry(
        path = path,
        fileName = path.fileName.toString(),
        modifiedAtMillis = Files.getLastModifiedTime(path).toMillis(),
    )

    fun upsert(entries: List<ScreenshotEntry>, path: Path): List<ScreenshotEntry> {
        val normalizedPath = path.normalizedScreenshotPath()
        return (entries.filterNot { it.path.normalizedScreenshotPath() == normalizedPath } + entry(path))
            .sortedWith(ENTRY_ORDER)
    }

    fun chooseSaveDestination(entry: ScreenshotEntry): CompletableFuture<Path?> {
        val baseName = entry.fileName.substringBeforeLast('.')
        val defaultPath = entry.path.resolveSibling("$baseName-edited$PNG_EXTENSION").toString()
        return MinecraftFileDialogs.saveFile("Save Screenshot", defaultPath, pngFilter).thenApply { selectedPath ->
            selectedPath?.let {
                if (it.fileName.toString().endsWith(PNG_EXTENSION, ignoreCase = true)) it else Path.of("$it$PNG_EXTENSION")
            }
        }
    }

    fun delete(path: Path) {
        Files.delete(path)
        ScreenshotSharing.invalidate(path)
    }

    private fun isScreenshot(path: Path): Boolean =
        Files.isRegularFile(path) && path.fileName.toString().endsWith(PNG_EXTENSION, ignoreCase = true)

    private const val PNG_EXTENSION = ".png"
    private val pngFilter = FileDialogFilter("PNG image", listOf("png"))
    private val ENTRY_ORDER = compareByDescending<ScreenshotEntry> { it.modifiedAtMillis }
        .thenByDescending { it.fileName }
}
