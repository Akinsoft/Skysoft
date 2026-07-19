package com.skysoft.features.screenshot

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
                .map { path ->
                    ScreenshotEntry(
                        path = path,
                        fileName = path.fileName.toString(),
                        modifiedAtMillis = Files.getLastModifiedTime(path).toMillis(),
                    )
                }
                .sorted(
                    compareByDescending<ScreenshotEntry> { it.modifiedAtMillis }
                        .thenByDescending { it.fileName },
                )
                .toList()
        }
    }

    fun chooseSaveDestination(entry: ScreenshotEntry): Path? {
        val defaultPath = Path.of(System.getProperty("user.home"), entry.fileName).toString()
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

    fun saveAs(source: Path, destination: Path) {
        require(source.toAbsolutePath().normalize() != destination.toAbsolutePath().normalize()) {
            "Screenshot source and destination are the same"
        }
        destination.parent?.let(Files::createDirectories)
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }

    fun delete(path: Path) {
        Files.delete(path)
    }

    private fun isScreenshot(path: Path): Boolean =
        Files.isRegularFile(path) && path.fileName.toString().endsWith(PNG_EXTENSION, ignoreCase = true)

    private const val PNG_EXTENSION = ".png"
}
