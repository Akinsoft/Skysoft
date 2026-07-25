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

internal class ScreenshotManagerScreen(
    private val parent: Screen?,
    initialSelectedPath: Path? = null,
) : Screen(Component.literal("Skysoft Screenshots")) {
    private val minecraftClient = Minecraft.getInstance()
    private val textures = ScreenshotTextureStore(minecraftClient)
    private val focusTransition = ScreenshotFocusTransition()
    private val editor = ScreenshotEditorController()
    private val initialSelectedPath = initialSelectedPath?.toAbsolutePath()?.normalize()
    private var entries: List<ScreenshotEntry> = emptyList()
    private var loadStatus = ScreenshotLoadStatus.LOADING
    private var selectedPath: Path? = null
    private var scrollOffset = 0
    private var galleryLayout: ScreenshotGalleryLayout? = null
    private var focusLayout: ScreenshotFocusLayout? = null
    private var pendingAction: ScreenshotAction? = null
    private var notice: ScreenshotNotice? = null
    private var confirmation: ScreenshotConfirmation? = null
    private var isEditing = false
    private var shouldCloseAfterDiscard = false
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
            confirmation = null
            val layout = ScreenshotGalleryLayout.create(width, height, entries.size, scrollOffset)
            scrollOffset = layout.scrollOffset
            galleryLayout = layout
            focusLayout = null
            editor.clearPresentation()
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
            val layout = ScreenshotFocusLayout.create(width, height, isEditing)
            val visuals = focusTransition.visuals(layout.preview)
            val selectedIndex = entries.indexOf(selectedEntry)
            entries.getOrNull(selectedIndex - 1)?.let { textures.thumbnail(it.path) }
            entries.getOrNull(selectedIndex + 1)?.let { textures.thumbnail(it.path) }
            val texture = textures.preview(selectedEntry.path) ?: textures.thumbnail(selectedEntry.path)
            val editorPresentation = if (isEditing) {
                editor.prepare(selectedEntry.path, layout.editorViewport(), texture)
            } else {
                editor.clearPresentation()
                ScreenshotEditorPresentation(editor.session(selectedEntry.path), null)
            }
            focusLayout = layout
            galleryLayout = null
            ScreenshotManagerRenderer.renderFocus(
                context,
                font,
                layout,
                selectedEntry,
                texture,
                textures.isSelectedPreviewFailed(selectedEntry.path) && textures.isThumbnailFailed(selectedEntry.path),
                editorPresentation.session,
                editorPresentation.geometry,
                isEditing,
                visuals,
                notice?.takeIf { System.currentTimeMillis() <= it.expiresAtMillis },
                confirmation,
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
            SoundUtilities.playRandomNavigationSound()
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val handled = galleryLayout?.let { gallery ->
            if (gallery.maximumScroll == 0) {
                false
            } else {
                scrollOffset = (scrollOffset - scrollY * gallery.rowStep)
                    .roundToInt()
                    .coerceIn(0, gallery.maximumScroll)
                true
            }
        } ?: focusLayout?.let { focus ->
            val path = selectedPath
            path != null &&
                isEditing &&
                pendingAction == null &&
                confirmation == null &&
                focusTransition.isComplete() &&
                focus.preview.contains(mouseX.toInt(), mouseY.toInt()) &&
                editor.processScroll(path, mouseX, mouseY, scrollY) == InputHandlingResult.CONSUMED
        } ?: false
        return if (handled) true else super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, dragX, dragY)
        if (!isEditing) return super.mouseDragged(click, dragX, dragY)
        val path = selectedPath ?: return super.mouseDragged(click, dragX, dragY)
        return if (editor.processDrag(path, click.x(), click.y()) == InputHandlingResult.CONSUMED) {
            true
        } else {
            super.mouseDragged(click, dragX, dragY)
        }
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(click)
        if (!isEditing) return super.mouseReleased(click)
        val path = selectedPath ?: return super.mouseReleased(click)
        return if (editor.processRelease(path) == InputHandlingResult.CONSUMED) true else super.mouseReleased(click)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (pendingAction != null && event.key() in listOf(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE)) {
            return true
        }
        if (
            selectedPath != null &&
            isEditing &&
            confirmation == null &&
            event.hasControlDownWithQuirk() &&
            event.key() in listOf(GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y)
        ) {
            val session = selectedPath?.let(editor::session) ?: return true
            if (event.key() == GLFW.GLFW_KEY_Y || Minecraft.getInstance().hasShiftDown()) session.redo() else session.undo()
            return true
        }
        if (
            selectedPath != null &&
            pendingAction == null &&
            confirmation == null &&
            focusTransition.isComplete() &&
            event.key() in listOf(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT)
        ) {
            val direction = if (event.key() == GLFW.GLFW_KEY_LEFT) -1 else 1
            if (navigateSelection(direction) == InputHandlingResult.CONSUMED) {
                SoundUtilities.playNavigationSound(direction)
            }
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && confirmation != null) {
            confirmation = null
            shouldCloseAfterDiscard = false
            SoundUtilities.playRandomNavigationSound()
            return true
        }
        if (event.key() in listOf(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE) && selectedPath != null) {
            returnToGallery()
            SoundUtilities.playRandomNavigationSound()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        val unsavedPath = editor.firstUnsavedPath()
        if (unsavedPath != null) {
            selectedPath = unsavedPath
            isEditing = true
            confirmation = ScreenshotConfirmation.DISCARD
            shouldCloseAfterDiscard = true
            return
        }
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
                    selectedPath = entries.firstOrNull {
                        it.path.toAbsolutePath().normalize() == initialSelectedPath
                    }?.path
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
        isEditing = false
        focusTransition.startExpansion(tile.image)
        confirmation = null
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
        if (confirmation != null) return activateConfirmationAt(layout, mouseX, mouseY)
        val path = selectedPath ?: return InputHandlingResult.IGNORED
        if (isEditing && editor.processClick(layout, path, mouseX, mouseY) == InputHandlingResult.CONSUMED) {
            return InputHandlingResult.CONSUMED
        }
        return when {
            layout.previous.contains(mouseX, mouseY) -> navigateSelection(-1)
            layout.next.contains(mouseX, mouseY) -> navigateSelection(1)
            layout.share.contains(mouseX, mouseY) -> {
                val path = selectedPath ?: return InputHandlingResult.IGNORED
                if (ScreenshotSharing.status(path).state == ScreenshotShareState.UPLOADED) {
                    ScreenshotSharing.share(path)
                } else {
                    confirmation = ScreenshotConfirmation.SHARE
                    notice = null
                }
                InputHandlingResult.CONSUMED
            }
            layout.copy.contains(mouseX, mouseY) -> startAction(ScreenshotAction.COPY)
            layout.edit.contains(mouseX, mouseY) -> {
                isEditing = !isEditing
                editor.clearPresentation()
                notice = null
                InputHandlingResult.CONSUMED
            }
            layout.save.contains(mouseX, mouseY) -> {
                if (!isEditing || !editor.session(path).hasEdits) return InputHandlingResult.CONSUMED
                confirmation = ScreenshotConfirmation.SAVE
                notice = null
                InputHandlingResult.CONSUMED
            }
            layout.delete.contains(mouseX, mouseY) -> {
                confirmation = ScreenshotConfirmation.DELETE
                notice = null
                InputHandlingResult.CONSUMED
            }
            else -> InputHandlingResult.IGNORED
        }
    }

    private fun activateConfirmationAt(
        layout: ScreenshotFocusLayout,
        mouseX: Int,
        mouseY: Int,
    ): InputHandlingResult {
        if (pendingAction != null) return InputHandlingResult.IGNORED
        if (confirmation == ScreenshotConfirmation.SAVE) {
            return activateSaveChoiceAt(layout, mouseX, mouseY)
        }
        if (layout.confirmationButtons.cancel.contains(mouseX, mouseY)) {
            confirmation = null
            shouldCloseAfterDiscard = false
            return InputHandlingResult.CONSUMED
        }
        if (!layout.confirmationButtons.confirm.contains(mouseX, mouseY)) return InputHandlingResult.IGNORED
        return when (confirmation) {
            ScreenshotConfirmation.SHARE -> {
                val path = selectedPath ?: return InputHandlingResult.IGNORED
                confirmation = null
                ScreenshotSharing.share(path)
                InputHandlingResult.CONSUMED
            }
            ScreenshotConfirmation.DELETE -> startAction(ScreenshotAction.DELETE)
            ScreenshotConfirmation.DISCARD -> {
                editor.clear()
                isEditing = false
                confirmation = null
                if (shouldCloseAfterDiscard) MinecraftClient.setScreen(parent)
                shouldCloseAfterDiscard = false
                InputHandlingResult.CONSUMED
            }
            ScreenshotConfirmation.SAVE, null -> InputHandlingResult.IGNORED
        }
    }

    private fun activateSaveChoiceAt(
        layout: ScreenshotFocusLayout,
        mouseX: Int,
        mouseY: Int,
    ): InputHandlingResult = when {
        layout.saveButtons.saveNew.contains(mouseX, mouseY) -> startAction(ScreenshotAction.SAVE_NEW)
        layout.saveButtons.replace.contains(mouseX, mouseY) -> startAction(ScreenshotAction.REPLACE)
        layout.saveButtons.cancel.contains(mouseX, mouseY) -> {
            confirmation = null
            InputHandlingResult.CONSUMED
        }
        else -> InputHandlingResult.IGNORED
    }

    private fun startAction(action: ScreenshotAction): InputHandlingResult {
        val entry = selectedPath?.let { path -> entries.firstOrNull { it.path == path } }
            ?: return InputHandlingResult.IGNORED
        val snapshot = if (isEditing) editor.session(entry.path).snapshot else ScreenshotEditSnapshot()
        val request = ScreenshotManagerFileActions.prepare(action, entry, snapshot)
        if (request == null) {
            confirmation = null
            return InputHandlingResult.CONSUMED
        }
        confirmation = null
        if (request.validationError != null) {
            notice = screenshotTimedNotice(request.validationError, true)
            return InputHandlingResult.CONSUMED
        }
        pendingAction = action
        notice = ScreenshotNotice(action.progressMessage, false, Long.MAX_VALUE)
        request.execute().whenComplete { _, failure -> finishAction(request, failure) }
        return InputHandlingResult.CONSUMED
    }

    private fun finishAction(request: ScreenshotActionRequest, failure: Throwable?) {
        minecraftClient.execute {
            if (isDisposed || pendingAction != request.action) return@execute
            pendingAction = null
            if (failure != null) {
                notice = screenshotTimedNotice(request.action.failureMessage, true)
                return@execute
            }
            when (request.action) {
                ScreenshotAction.COPY -> notice = screenshotTimedNotice(request.action.successMessage, false)
                ScreenshotAction.SAVE_NEW -> {
                    editor.remove(request.source)
                    isEditing = false
                    val savedPath = requireNotNull(request.destination).toAbsolutePath().normalize()
                    val screenshotsDirectory = ScreenshotManager.screenshotsDirectory().toAbsolutePath().normalize()
                    if (savedPath.parent == screenshotsDirectory) {
                        editor.remove(savedPath)
                        entries = ScreenshotRepository.upsert(entries, savedPath)
                        textures.refresh(savedPath)
                        ScreenshotSharing.invalidate(savedPath)
                    }
                    notice = screenshotTimedNotice("Saved as ${savedPath.fileName}.", false)
                }
                ScreenshotAction.REPLACE -> {
                    editor.remove(request.source)
                    isEditing = false
                    textures.refresh(request.source)
                    ScreenshotSharing.invalidate(request.source)
                    entries = ScreenshotRepository.upsert(entries, request.source)
                    notice = screenshotTimedNotice(request.action.successMessage, false)
                }
                ScreenshotAction.DELETE -> {
                    textures.discard(request.source)
                    editor.remove(request.source)
                    isEditing = false
                    entries = entries.filterNot { it.path == request.source }
                    selectedPath = null
                    focusTransition.reset()
                    notice = null
                }
            }
        }
    }

    private fun navigateSelection(direction: Int): InputHandlingResult {
        val currentIndex = entries.indexOfFirst { it.path == selectedPath }
        val nextEntry = entries.getOrNull(currentIndex + direction) ?: return InputHandlingResult.IGNORED
        selectedPath = nextEntry.path
        isEditing = false
        editor.clearPresentation()
        confirmation = null
        notice = null
        textures.thumbnail(nextEntry.path)
        focusTransition.startNavigation(direction)
        return InputHandlingResult.CONSUMED
    }

    private fun returnToGallery() {
        selectedPath = null
        isEditing = false
        editor.clearPresentation()
        confirmation = null
        notice = null
        pendingAction = null
        textures.clearSelectedPreview()
        focusTransition.reset()
    }

}

internal enum class ScreenshotConfirmation(val confirmLabel: String) {
    SHARE("Upload"),
    DELETE("Delete"),
    SAVE("Save"),
    DISCARD("Discard"),
}

private fun screenshotTimedNotice(text: String, isError: Boolean): ScreenshotNotice = ScreenshotNotice(
    text,
    isError,
    System.currentTimeMillis() + if (isError) ERROR_NOTICE_MILLIS else SUCCESS_NOTICE_MILLIS,
)

private const val SUCCESS_NOTICE_MILLIS = 2500L
private const val ERROR_NOTICE_MILLIS = 3500L
