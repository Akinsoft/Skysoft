package com.skysoft.features.screenshot

import java.nio.file.Files
import java.nio.file.Path
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs

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

    fun chooseSaveDestination(entry: ScreenshotEntry): Path? {
        val baseName = entry.fileName.substringBeforeLast('.')
        val defaultPath = entry.path.resolveSibling("$baseName-edited$PNG_EXTENSION").toString()
        val selection = MemoryStack.stackPush().use { stack ->
            val filters = stack.mallocPointer(1)
            filters.put(stack.UTF8("*.png"))
            filters.flip()
            TinyFileDialogs.tinyfd_saveFileDialog("Save Screenshot", defaultPath, filters, "PNG image")
        } ?: return null
        val selectedPath = Path.of(selection)
        return if (selectedPath.fileName.toString().endsWith(PNG_EXTENSION, ignoreCase = true)) {
            selectedPath
        } else {
            Path.of("$selectedPath$PNG_EXTENSION")
        }
    }

    fun delete(path: Path) {
        Files.delete(path)
        ScreenshotSharing.invalidate(path)
    }

    private fun isScreenshot(path: Path): Boolean =
        Files.isRegularFile(path) && path.fileName.toString().endsWith(PNG_EXTENSION, ignoreCase = true)

    private const val PNG_EXTENSION = ".png"
    private val ENTRY_ORDER = compareByDescending<ScreenshotEntry> { it.modifiedAtMillis }
        .thenByDescending { it.fileName }
}
