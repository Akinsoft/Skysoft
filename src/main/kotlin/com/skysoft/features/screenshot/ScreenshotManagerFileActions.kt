package com.skysoft.features.screenshot

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Util

internal enum class ScreenshotAction(
    val progressMessage: String,
    val successMessage: String,
    val failureMessage: String,
) {
    COPY("Copying...", "Copied to clipboard.", "Couldn't copy screenshot."),
    SAVE_NEW("Saving...", "Saved screenshot.", "Couldn't save screenshot."),
    REPLACE("Replacing...", "Replaced original screenshot.", "Couldn't replace screenshot."),
    DELETE("Deleting...", "Deleted screenshot.", "Couldn't delete screenshot."),
}

internal data class ScreenshotActionRequest(
    val action: ScreenshotAction,
    val source: Path,
    val snapshot: ScreenshotEditSnapshot,
    val destination: Path? = null,
    val validationError: String? = null,
) {
    fun execute(): CompletableFuture<Void> = when (action) {
        ScreenshotAction.COPY ->
            CompletableFuture.supplyAsync({ ScreenshotEditedImage.render(source, snapshot) }, Util.ioPool())
                .thenCompose(ScreenshotClipboard::copyAsync)
        ScreenshotAction.SAVE_NEW, ScreenshotAction.REPLACE ->
            CompletableFuture.runAsync(
                { ScreenshotEditedImage.write(source, snapshot, requireNotNull(destination)) },
                Util.ioPool(),
            )
        ScreenshotAction.DELETE ->
            CompletableFuture.runAsync({ ScreenshotRepository.delete(source) }, Util.ioPool())
    }
}

internal object ScreenshotManagerFileActions {
    fun prepare(
        action: ScreenshotAction,
        entry: ScreenshotEntry,
        snapshot: ScreenshotEditSnapshot,
    ): ScreenshotActionRequest? = when (action) {
        ScreenshotAction.COPY, ScreenshotAction.DELETE ->
            ScreenshotActionRequest(action, entry.path, snapshot)
        ScreenshotAction.REPLACE ->
            ScreenshotActionRequest(action, entry.path, snapshot, entry.path)
        ScreenshotAction.SAVE_NEW -> prepareSaveNew(entry, snapshot)
    }

    private fun prepareSaveNew(
        entry: ScreenshotEntry,
        snapshot: ScreenshotEditSnapshot,
    ): ScreenshotActionRequest? {
        val destination = ScreenshotRepository.chooseSaveDestination(entry) ?: return null
        val validationError = "Use Replace to overwrite the original.".takeIf {
            entry.path.normalizedScreenshotPath() == destination.normalizedScreenshotPath()
        }
        return ScreenshotActionRequest(
            ScreenshotAction.SAVE_NEW,
            entry.path,
            snapshot,
            destination,
            validationError,
        )
    }
}
