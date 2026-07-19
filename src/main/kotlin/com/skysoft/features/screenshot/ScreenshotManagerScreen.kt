package com.skysoft.features.screenshot

import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.input.InputHandlingResult
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

internal class ScreenshotManagerScreen(private val parent: Screen?) : Screen(Component.literal("Skysoft Screenshots")) {
    private val minecraftClient = Minecraft.getInstance()
    private val textures = ScreenshotTextureStore(minecraftClient)
    private val focusTransition = ScreenshotFocusTransition()
    private var entries: List<ScreenshotEntry> = emptyList()
    private var loadStatus = ScreenshotLoadStatus.LOADING
    private var selectedPath: Path? = null
    private var scrollOffset = 0
    private var galleryLayout: ScreenshotGalleryLayout? = null
    private var focusLayout: ScreenshotFocusLayout? = null
    private var pendingAction: ScreenshotAction? = null
    private var notice: ScreenshotNotice? = null
    private var isDeleteConfirmationVisible = false
    private var hasStartedLoading = false
    private var isDisposed = false

    override fun init() {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            loadScreenshots()
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val selectedEntry = selectedPath?.let { path -> entries.firstOrNull { it.path == path } }
        if (selectedEntry == null) {
            selectedPath = null
            isDeleteConfirmationVisible = false
            val layout = ScreenshotGalleryLayout.create(width, height, entries.size, scrollOffset)
            scrollOffset = layout.scrollOffset
            galleryLayout = layout
            focusLayout = null
            ScreenshotManagerRenderer.renderGallery(
                context,
                font,
                layout,
                entries,
                loadStatus,
                textures,
                mouseX,
                mouseY,
            )
        } else {
            val layout = ScreenshotFocusLayout.create(width, height)
            val visuals = focusTransition.visuals(layout.preview)
            val selectedIndex = entries.indexOf(selectedEntry)
            entries.getOrNull(selectedIndex - 1)?.let { textures.thumbnail(it.path) }
            entries.getOrNull(selectedIndex + 1)?.let { textures.thumbnail(it.path) }
            focusLayout = layout
            galleryLayout = null
            ScreenshotManagerRenderer.renderFocus(
                context,
                font,
                layout,
                selectedEntry,
                textures,
                visuals,
                notice?.takeIf { System.currentTimeMillis() <= it.expiresAtMillis },
                isDeleteConfirmationVisible,
                pendingAction == null && visuals.isInteractive,
                selectedIndex > 0,
                selectedIndex >= 0 && selectedIndex + 1 < entries.size,
                mouseX,
                mouseY,
            )
        }
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled)
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()
        val previousSelectionIndex = entries.indexOfFirst { it.path == selectedPath }
        val result = if (selectedPath == null) {
            activateGalleryAt(mouseX, mouseY)
        } else {
            activateFocusAt(mouseX, mouseY)
        }
        if (result == InputHandlingResult.IGNORED) return super.mouseClicked(click, doubled)
        val selectedIndex = entries.indexOfFirst { it.path == selectedPath }
        if (previousSelectionIndex >= 0 && selectedIndex >= 0 && previousSelectionIndex != selectedIndex) {
            SoundUtilities.playNavigationSound((selectedIndex - previousSelectionIndex).coerceIn(-1, 1))
        } else {
            SoundUtilities.playClickSound()
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = galleryLayout ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        if (scrollY == 0.0 || layout.maximumScroll == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        scrollOffset = (scrollOffset - scrollY * layout.rowStep).roundToInt().coerceIn(0, layout.maximumScroll)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (pendingAction != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (pendingAction != null && event.key() == GLFW.GLFW_KEY_BACKSPACE) return true
        if (
            selectedPath != null &&
            pendingAction == null &&
            !isDeleteConfirmationVisible &&
            focusTransition.isComplete() &&
            event.key() in listOf(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT)
        ) {
            val direction = if (event.key() == GLFW.GLFW_KEY_LEFT) -1 else 1
            if (navigateSelection(direction) == InputHandlingResult.CONSUMED) {
                SoundUtilities.playNavigationSound(direction)
            }
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && isDeleteConfirmationVisible) {
            isDeleteConfirmationVisible = false
            SoundUtilities.playClickSound()
            return true
        }
        if (event.key() in listOf(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE) && selectedPath != null) {
            returnToGallery()
            SoundUtilities.playClickSound()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        MinecraftClient.setScreen(parent)
    }

    override fun removed() {
        isDisposed = true
        textures.close()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    private fun loadScreenshots() {
        CompletableFuture.supplyAsync(
            { ScreenshotRepository.list(ScreenshotManager.screenshotsDirectory()) },
            Util.ioPool(),
        ).whenComplete { loadedEntries, failure ->
            minecraftClient.execute {
                if (isDisposed) return@execute
                if (failure == null) {
                    entries = loadedEntries
                    loadStatus = ScreenshotLoadStatus.READY
                } else {
                    loadStatus = ScreenshotLoadStatus.FAILED
                }
            }
        }
    }

    private fun activateGalleryAt(mouseX: Int, mouseY: Int): InputHandlingResult {
        val layout = galleryLayout ?: return InputHandlingResult.IGNORED
        if (layout.close.contains(mouseX, mouseY)) {
            onClose()
            return InputHandlingResult.CONSUMED
        }
        if (!layout.content.contains(mouseX, mouseY)) return InputHandlingResult.IGNORED
        val tile = layout.tiles.firstOrNull { it.bounds.contains(mouseX, mouseY) }
            ?: return InputHandlingResult.IGNORED
        selectedPath = entries.getOrNull(tile.index)?.path ?: return InputHandlingResult.IGNORED
        focusTransition.startExpansion(tile.image)
        isDeleteConfirmationVisible = false
        notice = null
        return InputHandlingResult.CONSUMED
    }

    private fun activateFocusAt(mouseX: Int, mouseY: Int): InputHandlingResult {
        val layout = focusLayout ?: return InputHandlingResult.IGNORED
        if (layout.close.contains(mouseX, mouseY)) {
            onClose()
            return InputHandlingResult.CONSUMED
        }
        if (pendingAction != null) return InputHandlingResult.IGNORED
        if (!focusTransition.isComplete()) return InputHandlingResult.CONSUMED
        if (layout.back.contains(mouseX, mouseY)) {
            returnToGallery()
            return InputHandlingResult.CONSUMED
        }
        if (isDeleteConfirmationVisible) return activateDeleteConfirmationAt(layout, mouseX, mouseY)
        return when {
            layout.previous.contains(mouseX, mouseY) -> navigateSelection(-1)
            layout.next.contains(mouseX, mouseY) -> navigateSelection(1)
            layout.copy.contains(mouseX, mouseY) -> startClipboardCopy()
            layout.saveAs.contains(mouseX, mouseY) -> startSaveAs()
            layout.delete.contains(mouseX, mouseY) -> {
                isDeleteConfirmationVisible = true
                notice = null
                InputHandlingResult.CONSUMED
            }
            else -> InputHandlingResult.IGNORED
        }
    }

    private fun activateDeleteConfirmationAt(
        layout: ScreenshotFocusLayout,
        mouseX: Int,
        mouseY: Int,
    ): InputHandlingResult {
        if (pendingAction != null) return InputHandlingResult.IGNORED
        if (layout.cancelDelete.contains(mouseX, mouseY)) {
            isDeleteConfirmationVisible = false
            return InputHandlingResult.CONSUMED
        }
        if (!layout.confirmDelete.contains(mouseX, mouseY)) return InputHandlingResult.IGNORED
        return deleteSelectedScreenshot()
    }

    private fun startClipboardCopy(): InputHandlingResult {
        val path = selectedPath ?: return InputHandlingResult.IGNORED
        pendingAction = ScreenshotAction.COPY
        notice = ScreenshotNotice("Copying...", false, Long.MAX_VALUE)
        CompletableFuture.runAsync({ ScreenshotClipboard.copy(path) }, Util.ioPool())
            .whenComplete { _, failure ->
                completeAction(
                    ScreenshotAction.COPY,
                    failure,
                    "Copied to clipboard.",
                    "Couldn't copy screenshot.",
                )
            }
        return InputHandlingResult.CONSUMED
    }

    private fun startSaveAs(): InputHandlingResult {
        val entry = selectedPath?.let { path -> entries.firstOrNull { it.path == path } }
            ?: return InputHandlingResult.IGNORED
        val destination = ScreenshotRepository.chooseSaveDestination(entry) ?: return InputHandlingResult.CONSUMED
        pendingAction = ScreenshotAction.SAVE
        notice = ScreenshotNotice("Saving...", false, Long.MAX_VALUE)
        CompletableFuture.runAsync({ ScreenshotRepository.saveAs(entry.path, destination) }, Util.ioPool())
            .whenComplete { _, failure ->
                completeAction(
                    ScreenshotAction.SAVE,
                    failure,
                    "Saved as ${destination.fileName}.",
                    "Couldn't save screenshot.",
                )
            }
        return InputHandlingResult.CONSUMED
    }

    private fun deleteSelectedScreenshot(): InputHandlingResult {
        val path = selectedPath ?: return InputHandlingResult.IGNORED
        pendingAction = ScreenshotAction.DELETE
        CompletableFuture.runAsync({ ScreenshotRepository.delete(path) }, Util.ioPool())
            .whenComplete { _, failure ->
                minecraftClient.execute {
                    if (isDisposed || pendingAction != ScreenshotAction.DELETE) return@execute
                    pendingAction = null
                    isDeleteConfirmationVisible = false
                    if (failure == null) {
                        textures.discard(path)
                        entries = entries.filterNot { it.path == path }
                        selectedPath = null
                        focusTransition.reset()
                        notice = null
                    } else {
                        notice = timedNotice("Couldn't delete screenshot.", true)
                    }
                }
            }
        return InputHandlingResult.CONSUMED
    }

    private fun navigateSelection(direction: Int): InputHandlingResult {
        val currentIndex = entries.indexOfFirst { it.path == selectedPath }
        val nextEntry = entries.getOrNull(currentIndex + direction) ?: return InputHandlingResult.IGNORED
        selectedPath = nextEntry.path
        isDeleteConfirmationVisible = false
        notice = null
        textures.thumbnail(nextEntry.path)
        focusTransition.startNavigation(direction)
        return InputHandlingResult.CONSUMED
    }

    private fun completeAction(
        action: ScreenshotAction,
        failure: Throwable?,
        successMessage: String,
        failureMessage: String,
    ) {
        minecraftClient.execute {
            if (isDisposed || pendingAction != action) return@execute
            pendingAction = null
            notice = if (failure == null) timedNotice(successMessage, false) else timedNotice(failureMessage, true)
        }
    }

    private fun returnToGallery() {
        selectedPath = null
        isDeleteConfirmationVisible = false
        notice = null
        pendingAction = null
        textures.clearSelectedPreview()
        focusTransition.reset()
    }

    private fun timedNotice(text: String, isError: Boolean): ScreenshotNotice = ScreenshotNotice(
        text,
        isError,
        System.currentTimeMillis() + if (isError) ERROR_NOTICE_MILLIS else SUCCESS_NOTICE_MILLIS,
    )

    private enum class ScreenshotAction {
        COPY,
        SAVE,
        DELETE,
    }

    private companion object {
        const val SUCCESS_NOTICE_MILLIS = 2500L
        const val ERROR_NOTICE_MILLIS = 3500L
    }
}
