package com.skysoft.features.helditem

import com.skysoft.config.HeldItemConfig
import com.skysoft.config.HeldItemSwingStyle
import com.skysoft.config.HeldItemTransformConfig
import com.skysoft.config.HeldItemTransformLimits
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ChangeResult
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.PixelButtonRenderer
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.Rect
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object HeldItemEditorScreen {
    private var preferredEditTarget = EditTarget.GLOBAL
    private val historyStore = HeldItemHistoryStore()

    fun open() {
        MinecraftClient.setScreen(EditorScreen(MinecraftClient.screen()))
    }

    internal fun previewTransform(itemStack: ItemStack): HeldItemTransformConfig? =
        if (HeldItemCustomization.isEligible(itemStack)) {
            (MinecraftClient.screen() as? EditorScreen)?.previewTransform(itemStack)
        } else {
            null
        }

    internal fun previewUsesVanillaTexture(itemStack: ItemStack): Boolean? =
        if (HeldItemCustomization.isEligible(itemStack)) {
            (MinecraftClient.screen() as? EditorScreen)?.previewUsesVanillaTexture(itemStack)
        } else {
            null
        }

    class EditorScreen(private val parent: Screen?) : Screen(Component.literal("Skysoft Held Item")) {
        private val config: HeldItemConfig
            get() = SkysoftConfigGui.config().gui.heldItem

        private val editorState by lazy {
            HeldItemEditorState(
                config = config,
                initialTarget = preferredEditTarget,
                targetSelection = { preferredEditTarget = it },
            )
        }
        private val layout = HeldItemEditorLayout()
        private val openingAnimation = HeldItemEditorOpeningAnimation()
        private val disabledOverlay = HeldItemDisabledOverlay()
        private val historyController by lazy {
            HeldItemHistoryController(
                config = config,
                store = historyStore,
                keyProvider = { editorState.historyKey() },
            )
        }
        private val controlActions by lazy {
            HeldItemEditorControlActions(config, editorState, layout, historyController, ::markChanged)
        }
        private var dragKind: DragKind? = null
        private var draggedField: TransformField? = null
        private var panelDragOffsetX = 0
        private var panelDragOffsetY = 0
        private var lastDragX = 0
        private var lastDragY = 0
        private var hasUnsavedChanges = false
        private var lastChangedAt = 0L

        override fun init() {
            layout.initialize(width, height, config.editorX, config.editorY)
            openingAnimation.start()
            disabledOverlay.initialize(config.enabled)
        }

        internal fun previewTransform(itemStack: ItemStack): HeldItemTransformConfig? =
            editorState.displayTransform().takeIf { itemStack === editorState.currentItem() }

        internal fun previewUsesVanillaTexture(itemStack: ItemStack): Boolean? =
            editorState.usesVanillaTexture().takeIf { itemStack === editorState.currentItem() }

        override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            editorState.ensureTargetAvailable()
            val isEditingBlocked = disabledOverlay.isEditingBlocked(config.enabled)
            openingAnimation.render(context, layout.panelBounds()) { isComplete ->
                val isInteractive = isComplete && !isEditingBlocked
                val renderMouseX = if (isInteractive) mouseX else EditorAnimation.HIDDEN_MOUSE_COORDINATE
                val renderMouseY = if (isInteractive) mouseY else EditorAnimation.HIDDEN_MOUSE_COORDINATE
                HeldItemEditorRenderer.render(
                    context,
                    font,
                    editorState,
                    layout,
                    historyController.canUndo(),
                    historyController.canRedo(),
                    renderMouseX,
                    renderMouseY,
                )
            }
            HeldItemDisabledOverlayRenderer.render(
                context,
                font,
                width,
                height,
                disabledOverlay.visuals(config.enabled),
            )
        }

        override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (click.button() !in EDITOR_MOUSE_BUTTONS) return super.mouseClicked(click, doubled)
            if (!openingAnimation.isComplete()) return true
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            if (disabledOverlay.isEditingBlocked(config.enabled)) {
                if (
                    !config.enabled &&
                    click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
                    HeldItemDisabledOverlayRenderer.toggleBounds(width, height).contains(mouseX, mouseY)
                ) {
                    activateEditorButton {
                        config.enabled = true
                        disabledOverlay.beginEnableTransition()
                        SkysoftConfigGui.config().saveNow()
                    }
                }
                return true
            }
            processClick(mouseX, mouseY, click.button())
            return true
        }

        override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
            if (disabledOverlay.isEditingBlocked(config.enabled)) {
                cancelDrag()
                return true
            }
            val activeDrag = dragKind ?: return super.mouseDragged(click, deltaX, deltaY)
            val expectedButton = when (activeDrag) {
                DragKind.PANEL, DragKind.MOVE_ITEM, DragKind.SLIDER -> GLFW.GLFW_MOUSE_BUTTON_LEFT
                DragKind.MOVE_DEPTH -> GLFW.GLFW_MOUSE_BUTTON_RIGHT
            }
            if (click.button() != expectedButton) return super.mouseDragged(click, deltaX, deltaY)
            val mouseX = click.x().toInt()
            val mouseY = click.y().toInt()
            when (activeDrag) {
                DragKind.PANEL -> {
                    layout.movePanel(mouseX - panelDragOffsetX, mouseY - panelDragOffsetY)
                    config.editorX = layout.panelX
                    config.editorY = layout.panelY
                    markChanged()
                }
                DragKind.SLIDER -> draggedField?.let { field ->
                    editorState.updateSlider(field, mouseX, layout.sliderTrackBounds(field))
                    markChanged()
                }
                DragKind.MOVE_ITEM -> {
                    val unitsPerPixel = HeldItemPositionMath.unitsPerPixel(
                        guiHeight = height,
                        depthOffset = editorState.displayTransform().z,
                    )
                    editorState.moveItem(mouseX - lastDragX, mouseY - lastDragY, unitsPerPixel)
                    markChanged()
                }
                DragKind.MOVE_DEPTH -> {
                    editorState.moveItemDepth(mouseX - lastDragX)
                    markChanged()
                }
            }
            lastDragX = mouseX
            lastDragY = mouseY
            return true
        }

        override fun mouseReleased(click: MouseButtonEvent): Boolean {
            if (disabledOverlay.isEditingBlocked(config.enabled)) {
                cancelDrag()
                return true
            }
            if (click.button() in EDITOR_MOUSE_BUTTONS && dragKind != null) {
                historyController.commitGesture()
                dragKind = null
                draggedField = null
                saveChanges()
                return true
            }
            return super.mouseReleased(click)
        }

        override fun mouseScrolled(
            mouseX: Double,
            mouseY: Double,
            scrollX: Double,
            scrollY: Double,
        ): Boolean {
            if (!openingAnimation.isComplete()) return true
            if (disabledOverlay.isEditingBlocked(config.enabled)) return true
            if (scrollY == 0.0) return false
            val hoveredField = layout.sliderFieldAt(mouseX.toInt(), mouseY.toInt())
            if (hoveredField != null) {
                markChanged(
                    historyController.mutateScroll(hoveredField) {
                        editorState.changeFieldBy(hoveredField, scrollY.toFloat() * hoveredField.step)
                    },
                )
                return true
            }
            if (layout.panelBounds().contains(mouseX.toInt(), mouseY.toInt())) return true
            markChanged(
                historyController.mutateScroll(TransformField.SCALE) {
                    editorState.changeFieldBy(TransformField.SCALE, scrollY.toFloat() * TransformField.SCALE.step)
                },
            )
            return true
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            if (disabledOverlay.isEditingBlocked(config.enabled)) return super.keyPressed(event)
            if (event.key() in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9) {
                historyController.flushPending()
                dragKind = null
                draggedField = null
                Minecraft.getInstance().player?.inventory?.setSelectedSlot(event.key() - GLFW.GLFW_KEY_1)
                return true
            }
            return super.keyPressed(event)
        }

        override fun tick() {
            historyController.commitIdleScroll()
            if (hasUnsavedChanges && System.currentTimeMillis() - lastChangedAt >= EditorInput.SAVE_DELAY_MILLIS) {
                saveChanges()
            }
        }

        override fun onClose() {
            historyController.flushPending()
            saveChanges()
            MinecraftClient.setScreen(parent)
        }

        override fun isPauseScreen(): Boolean = false

        private fun processClick(mouseX: Int, mouseY: Int, button: Int) {
            val isLeftClick = button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            val headerAction = headerActionAt(layout, isLeftClick, mouseX, mouseY, ::onClose)
            val historyAction = if (isLeftClick) historyActionAt(layout, historyController, mouseX, mouseY) else null
            when {
                headerAction != null -> activateEditorButton(headerAction)
                historyAction != null -> activateEditorButton { markChanged(historyAction()) }
                isLeftClick && layout.textureToggleBounds().contains(mouseX, mouseY) && editorState.canToggleTexture() -> {
                    activateEditorButton {
                        markChanged(historyController.mutate { editorState.toggleTexture() })
                    }
                }
                isLeftClick && controlActions.activateTargetAt(mouseX, mouseY) == EditorInputHandlingResult.HANDLED -> Unit
                isLeftClick &&
                    controlActions.activateAdvancedControlAt(mouseX, mouseY) == EditorInputHandlingResult.HANDLED -> Unit
                isLeftClick && layout.sliderFieldAt(mouseX, mouseY) != null -> startSliderDrag(mouseX, mouseY)
                isLeftClick && layout.resetBounds().contains(mouseX, mouseY) && editorState.canResetCurrentTarget() -> {
                    activateEditorButton {
                        markChanged(historyController.mutate { editorState.resetCurrentTarget() })
                    }
                }
                isLeftClick && layout.doneBounds().contains(mouseX, mouseY) -> activateEditorButton(::onClose)
                isLeftClick && layout.titleDragBounds().contains(mouseX, mouseY) -> startPanelDrag(mouseX, mouseY)
                layout.panelBounds().contains(mouseX, mouseY) -> Unit
                isLeftClick -> startItemDrag(DragKind.MOVE_ITEM, mouseX, mouseY)
                else -> startItemDrag(DragKind.MOVE_DEPTH, mouseX, mouseY)
            }
        }

        private fun startSliderDrag(mouseX: Int, mouseY: Int) {
            val field = layout.sliderFieldAt(mouseX, mouseY) ?: return
            historyController.beginGesture()
            draggedField = field
            dragKind = DragKind.SLIDER
            editorState.updateSlider(field, mouseX, layout.sliderTrackBounds(field))
            markChanged()
        }

        private fun startPanelDrag(mouseX: Int, mouseY: Int) {
            dragKind = DragKind.PANEL
            panelDragOffsetX = mouseX - layout.panelX
            panelDragOffsetY = mouseY - layout.panelY
        }

        private fun startItemDrag(kind: DragKind, mouseX: Int, mouseY: Int) {
            historyController.beginGesture()
            dragKind = kind
            lastDragX = mouseX
            lastDragY = mouseY
        }

        private fun cancelDrag() {
            dragKind = null
            draggedField = null
        }

        private fun markChanged(changeResult: ChangeResult = ChangeResult.CHANGED) {
            if (changeResult == ChangeResult.UNCHANGED) return
            hasUnsavedChanges = true
            lastChangedAt = System.currentTimeMillis()
        }

        private fun saveChanges() {
            if (!hasUnsavedChanges) return
            SkysoftConfigGui.config().saveNow()
            hasUnsavedChanges = false
        }
    }
}

private class HeldItemEditorControlActions(
    private val config: HeldItemConfig,
    private val state: HeldItemEditorState,
    private val layout: HeldItemEditorLayout,
    private val historyController: HeldItemHistoryController,
    private val markChanged: (ChangeResult) -> Unit,
) {
    fun activateTargetAt(mouseX: Int, mouseY: Int): EditorInputHandlingResult {
        val target = when {
            layout.targetBounds(EditTarget.GLOBAL).contains(mouseX, mouseY) -> EditTarget.GLOBAL
            layout.targetBounds(EditTarget.ITEM).contains(mouseX, mouseY) && state.currentItemId() != null -> {
                EditTarget.ITEM
            }
            else -> return EditorInputHandlingResult.UNHANDLED
        }
        activateEditorButton {
            historyController.flushPending()
            state.selectTarget(target)
        }
        return EditorInputHandlingResult.HANDLED
    }

    fun activateAdvancedControlAt(mouseX: Int, mouseY: Int): EditorInputHandlingResult {
        if (layout.advancedToggleBounds().contains(mouseX, mouseY)) {
            activateEditorButton {
                val previousX = layout.panelX
                val previousY = layout.panelY
                layout.toggleAdvanced()
                if (layout.panelX != previousX || layout.panelY != previousY) {
                    config.editorX = layout.panelX
                    config.editorY = layout.panelY
                    markChanged(ChangeResult.CHANGED)
                }
            }
            return EditorInputHandlingResult.HANDLED
        }
        val style = layout.swingStyleAt(mouseX, mouseY) ?: return EditorInputHandlingResult.UNHANDLED
        activateEditorButton {
            markChanged(historyController.mutate { state.selectSwingStyle(style) })
        }
        return EditorInputHandlingResult.HANDLED
    }
}

private fun activateEditorButton(action: () -> Unit) {
    SoundUtilities.playClickSound()
    action()
}

private fun headerActionAt(
    layout: HeldItemEditorLayout,
    isLeftClick: Boolean,
    mouseX: Int,
    mouseY: Int,
    close: () -> Unit,
): (() -> Unit)? {
    if (!isLeftClick) return null
    return when {
        previewButtonBounds(layout).contains(mouseX, mouseY) -> HeldItemSwingPreview::play
        layout.closeBounds().contains(mouseX, mouseY) -> close
        else -> null
    }
}

private fun historyActionAt(
    layout: HeldItemEditorLayout,
    historyController: HeldItemHistoryController,
    mouseX: Int,
    mouseY: Int,
): (() -> ChangeResult)? = when {
    layout.undoBounds().contains(mouseX, mouseY) && historyController.canUndo() -> historyController::undo
    layout.redoBounds().contains(mouseX, mouseY) && historyController.canRedo() -> historyController::redo
    else -> null
}

private class HeldItemEditorOpeningAnimation {
    private var startedAtNanos = 0L

    fun start() {
        if (startedAtNanos == 0L) startedAtNanos = System.nanoTime()
    }

    fun isComplete(): Boolean = progress() >= 1f

    fun render(context: GuiGraphicsExtractor, bounds: Rect, drawPanel: (Boolean) -> Unit) {
        val progress = progress()
        if (progress >= 1f) {
            drawPanel(true)
            return
        }
        if (progress < EditorAnimation.LINE_PHASE_END) {
            val lineProgress = easeOutCubic(progress / EditorAnimation.LINE_PHASE_END)
            drawLine(context, bounds, lineProgress, 1f)
            return
        }

        val unfoldProgress = easeOutCubic(
            (progress - EditorAnimation.LINE_PHASE_END) / (1f - EditorAnimation.LINE_PHASE_END),
        )
        val visibleHeight = (bounds.height * unfoldProgress).roundToInt().coerceAtLeast(1)
        val visibleTop = bounds.y + (bounds.height - visibleHeight) / 2
        context.enableScissor(bounds.x, visibleTop, bounds.x + bounds.width, visibleTop + visibleHeight)
        try {
            drawPanel(false)
        } finally {
            context.disableScissor()
        }
        drawLine(context, bounds, 1f, 1f - unfoldProgress)
    }

    private fun progress(): Float {
        check(startedAtNanos != 0L) { "Held item editor animation was not started" }
        return ((System.nanoTime() - startedAtNanos) / EditorAnimation.DURATION_NANOS.toFloat()).coerceIn(0f, 1f)
    }

    private fun drawLine(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        widthProgress: Float,
        opacity: Float,
    ) {
        val lineWidth = (bounds.width * widthProgress).roundToInt().coerceAtLeast(1)
        val lineX = bounds.x + (bounds.width - lineWidth) / 2
        val lineY = bounds.y + bounds.height / 2
        val alpha = (EditorAnimation.MAX_ALPHA * opacity).roundToInt()
        val color = (alpha shl EditorAnimation.ALPHA_SHIFT) or EditorAnimation.LINE_RGB
        context.fill(lineX, lineY, lineX + lineWidth, lineY + EditorAnimation.LINE_HEIGHT, color)
    }

    private fun easeOutCubic(progress: Float): Float {
        val remaining = 1f - progress.coerceIn(0f, 1f)
        return 1f - remaining * remaining * remaining
    }
}

internal class HeldItemEditorState(
    private val config: HeldItemConfig,
    initialTarget: EditTarget = EditTarget.GLOBAL,
    private val targetSelection: (EditTarget) -> Unit = {},
    private val currentItemIdProvider: () -> String? = {
        HeldItemTransforms.itemId(HeldItemTransforms.currentItem())
    },
) {
    private var preferredTarget = initialTarget
    var target = initialTarget
        private set

    fun ensureTargetAvailable() {
        target = if (preferredTarget == EditTarget.ITEM && currentItemId() == null) {
            EditTarget.GLOBAL
        } else {
            preferredTarget
        }
    }

    fun selectTarget(selectedTarget: EditTarget) {
        if (selectedTarget == EditTarget.ITEM && currentItemId() == null) return
        preferredTarget = selectedTarget
        target = selectedTarget
        targetSelection(selectedTarget)
    }

    fun currentItem(): ItemStack = HeldItemTransforms.currentItem()

    fun currentItemId(): String? = currentItemIdProvider()

    fun displayTransform(): HeldItemTransformConfig =
        if (target == EditTarget.ITEM) config.transformFor(currentItemId()) else config.global

    fun canResetCurrentTarget(): Boolean = when (target) {
        EditTarget.GLOBAL -> config.hasGlobalCustomization()
        EditTarget.ITEM -> config.hasItemCustomization(currentItemId())
    }

    fun isTextureToggleVisible(): Boolean = HeldItemTextureOverrides.hasPackTexture(currentItem())

    fun canToggleTexture(): Boolean =
        HeldItemTextureOverrides.canUseVanillaTexture(currentItem()) &&
            (target == EditTarget.GLOBAL || currentItemId() != null)

    fun usesVanillaTexture(): Boolean = when (target) {
        EditTarget.GLOBAL -> config.usesVanillaTexture(null)
        EditTarget.ITEM -> config.usesVanillaTexture(currentItemId())
    }

    fun previewItem(): ItemStack = HeldItemTextureOverrides.previewStack(currentItem())

    fun toggleTexture(): ChangeResult {
        if (!canToggleTexture()) return ChangeResult.UNCHANGED
        return when (target) {
            EditTarget.GLOBAL -> config.toggleGlobalTexture()
            EditTarget.ITEM -> currentItemId()?.let(config::toggleItemTexture) ?: ChangeResult.UNCHANGED
        }
    }

    fun moveItem(deltaX: Int, deltaY: Int, unitsPerPixel: Float) {
        val transform = editableTransform() ?: return
        setFieldValue(transform, TransformField.X, transform.x + deltaX * unitsPerPixel)
        setFieldValue(transform, TransformField.Y, transform.y - deltaY * unitsPerPixel)
    }

    fun moveItemDepth(deltaX: Int) {
        val transform = editableTransform() ?: return
        setFieldValue(transform, TransformField.Z, transform.z + deltaX * EditorInput.DEPTH_PER_PIXEL)
    }

    fun updateSlider(field: TransformField, mouseX: Int, track: Rect) {
        val progress = ((mouseX - track.x) / track.width.toFloat()).coerceIn(0f, 1f)
        setField(field, field.min + (field.max - field.min) * progress)
    }

    fun changeFieldBy(field: TransformField, amount: Float) {
        setField(field, field.value(displayTransform()) + amount)
    }

    fun selectSwingStyle(style: HeldItemSwingStyle) {
        editableTransform()?.swingStyle = style
    }

    fun resetCurrentTarget(): ChangeResult {
        return if (target == EditTarget.GLOBAL) {
            config.resetGlobalCustomization()
        } else {
            currentItemId()?.let(config::removeItemCustomization) ?: ChangeResult.UNCHANGED
        }
    }

    private fun setField(field: TransformField, value: Float) {
        editableTransform()?.let { setFieldValue(it, field, value) }
    }

    private fun setFieldValue(transform: HeldItemTransformConfig, field: TransformField, value: Float) {
        field.setValue(transform, value.coerceIn(field.min, field.max))
    }

    private fun editableTransform(): HeldItemTransformConfig? = when (target) {
        EditTarget.GLOBAL -> config.global
        EditTarget.ITEM -> currentItemId()?.let(config::customize)
    }
}

private fun HeldItemEditorState.textureTooltip(): String = when {
    HeldItemTextureOverrides.isPaper(currentItem()) -> "Unavailable for paper items"
    target == EditTarget.ITEM && currentItemId() == null -> "Requires a SkyBlock ID"
    target == EditTarget.GLOBAL && usesVanillaTexture() -> "Restore pack textures globally"
    target == EditTarget.GLOBAL -> "Use vanilla textures globally"
    usesVanillaTexture() -> "Restore pack texture for this item"
    else -> "Use vanilla texture for this item"
}

private fun HeldItemEditorState.historyKey(): HeldItemHistoryKey? = when (target) {
    EditTarget.GLOBAL -> HeldItemHistoryKey.GLOBAL
    EditTarget.ITEM -> currentItemId()?.let(HeldItemHistoryKey::item)
}

private class HeldItemEditorLayout {
    var panelX = 0
        private set
    var panelY = 0
        private set
    private var screenWidth = 0
    private var screenHeight = 0
    var isAdvancedExpanded = false
        private set

    fun initialize(width: Int, height: Int, configuredX: Int, configuredY: Int) {
        screenWidth = width
        screenHeight = height
        panelX = configuredX.takeUnless { it == HeldItemConfig.AUTO_EDITOR_POSITION } ?: EditorPanel.MARGIN
        panelY = configuredY.takeUnless { it == HeldItemConfig.AUTO_EDITOR_POSITION }
            ?: ((height - EditorPanel.BASE_HEIGHT) / 2)
        constrainPanel()
    }

    fun movePanel(x: Int, y: Int) {
        panelX = x
        panelY = y
        constrainPanel()
    }

    fun panelBounds(): Rect = Rect(panelX, panelY, panelWidth(), panelHeight())

    fun titleDragBounds(): Rect = Rect(panelX, panelY, panelWidth(), EditorHeader.HEIGHT)

    fun closeBounds(): Rect = Rect(
        panelX + panelWidth() - EditorHeader.CLOSE_RIGHT_INSET,
        panelY + EditorHeader.BUTTON_Y,
        EditorHeader.CLOSE_SIZE,
        EditorHeader.BUTTON_HEIGHT,
    )

    fun undoBounds(): Rect = actionBounds().undo

    fun textureToggleBounds(): Rect = actionBounds().texture

    fun redoBounds(): Rect = actionBounds().redo

    fun targetBounds(target: EditTarget): Rect {
        val width = contentWidth() / EditTarget.entries.size
        return Rect(panelX + EditorPanel.INSET + target.ordinal * width, panelY + EditorTabs.TARGET_Y, width, EditorTabs.HEIGHT)
    }

    fun toggleAdvanced() {
        isAdvancedExpanded = !isAdvancedExpanded
        constrainPanel()
    }

    fun sliderRowBounds(field: TransformField): Rect {
        val basicIndex = EditorSliderFields.BASIC.indexOf(field)
        val rowY = if (basicIndex >= 0) {
            EditorSliders.START_Y + basicIndex * EditorSliders.ROW_HEIGHT
        } else {
            val rotationIndex = EditorSliderFields.ROTATION.indexOf(field)
            require(rotationIndex >= 0) { "Unknown held item slider field $field" }
            EditorAdvanced.ROTATION_START_Y + rotationIndex * EditorAdvanced.ROTATION_ROW_HEIGHT
        }
        val rowHeight = if (basicIndex >= 0) EditorSliders.ROW_HEIGHT else EditorAdvanced.ROTATION_ROW_HEIGHT
        return Rect(
            panelX + EditorPanel.INSET,
            panelY + rowY,
            contentWidth(),
            rowHeight,
        )
    }

    fun sliderTrackBounds(field: TransformField): Rect {
        val row = sliderRowBounds(field)
        val isRotation = field in EditorSliderFields.ROTATION
        val labelWidth = if (isRotation) EditorAdvanced.ROTATION_LABEL_WIDTH else EditorSliders.LABEL_WIDTH
        val reservedWidth = if (isRotation) EditorAdvanced.ROTATION_RESERVED_WIDTH else EditorSliders.RESERVED_WIDTH
        return Rect(
            row.x + labelWidth,
            row.y + EditorSliders.TRACK_Y,
            row.width - reservedWidth,
            EditorSliders.TRACK_HEIGHT,
        )
    }

    fun sliderFieldAt(mouseX: Int, mouseY: Int): TransformField? =
        visibleSliderFields().firstOrNull { sliderRowBounds(it).contains(mouseX, mouseY) }

    fun resetBounds(): Rect = actionBounds().reset

    fun doneBounds(): Rect = actionBounds().done

    private fun contentWidth(): Int = panelWidth() - EditorPanel.INSET * 2

    private fun panelWidth(): Int = min(
        EditorPanel.WIDTH,
        max(EditorPanel.MIN_WIDTH, screenWidth - EditorPanel.MARGIN * 2),
    )

    private fun actionBounds(): EditorActionBounds {
        val isCompact = contentWidth() < EditorActions.COMPACT_CONTENT_WIDTH
        val resetWidth = if (isCompact) EditorActions.COMPACT_RESET_WIDTH else EditorActions.RESET_WIDTH
        val doneWidth = if (isCompact) EditorActions.COMPACT_DONE_WIDTH else EditorActions.DONE_WIDTH
        val actionY = panelY + EditorActions.y(isAdvancedExpanded)
        val reset = Rect(panelX + EditorPanel.INSET, actionY, resetWidth, EditorActions.HEIGHT)
        val done = Rect(
            panelX + panelWidth() - EditorPanel.INSET - doneWidth,
            actionY,
            doneWidth,
            EditorActions.HEIGHT,
        )
        val availableWidth = done.x - (reset.x + reset.width)
        val groupGap = ((availableWidth - EditorActions.GROUP_BASE_WIDTH) / 2)
            .coerceIn(0, EditorActions.GROUP_GAP)
        val groupWidth = EditorActions.GROUP_BASE_WIDTH + groupGap * 2
        val groupX = reset.x + reset.width + (availableWidth - groupWidth) / 2
        val undo = Rect(groupX, actionY, EditorActions.HISTORY_SIZE, EditorActions.HEIGHT)
        val texture = Rect(
            undo.x + undo.width + groupGap,
            actionY,
            EditorActions.TEXTURE_WIDTH,
            EditorActions.HEIGHT,
        )
        val redo = Rect(
            texture.x + texture.width + groupGap,
            actionY,
            EditorActions.HISTORY_SIZE,
            EditorActions.HEIGHT,
        )
        return EditorActionBounds(reset, undo, texture, redo, done)
    }

    private fun constrainPanel() {
        panelX = panelX.coerceIn(
            EditorPanel.MARGIN,
            max(EditorPanel.MARGIN, screenWidth - panelWidth() - EditorPanel.MARGIN),
        )
        panelY = panelY.coerceIn(
            EditorPanel.MARGIN,
            max(EditorPanel.MARGIN, screenHeight - panelHeight() - EditorPanel.MARGIN),
        )
    }

    private fun panelHeight(): Int = if (isAdvancedExpanded) {
        EditorPanel.BASE_HEIGHT + EditorAdvanced.EXPANDED_HEIGHT
    } else {
        EditorPanel.BASE_HEIGHT
    }
}

private fun HeldItemEditorLayout.advancedToggleBounds(): Rect {
    val panel = panelBounds()
    return Rect(
        panel.x + (panel.width - EditorAdvanced.TOGGLE_WIDTH) / 2,
        panel.y + EditorAdvanced.TOGGLE_Y,
        EditorAdvanced.TOGGLE_WIDTH,
        EditorAdvanced.TOGGLE_HEIGHT,
    )
}

private fun HeldItemEditorLayout.swingStyleBounds(style: HeldItemSwingStyle): Rect {
    val row = swingStyleRowBounds()
    val buttonsX = row.x + EditorAdvanced.STYLE_LABEL_WIDTH
    val buttonsWidth = row.width - EditorAdvanced.STYLE_LABEL_WIDTH
    val buttonWidth = (buttonsWidth - EditorAdvanced.STYLE_BUTTON_GAP) / HeldItemSwingStyle.entries.size
    return Rect(
        buttonsX + style.ordinal * (buttonWidth + EditorAdvanced.STYLE_BUTTON_GAP),
        row.y + EditorAdvanced.STYLE_BUTTON_Y,
        buttonWidth,
        EditorAdvanced.STYLE_BUTTON_HEIGHT,
    )
}

private fun HeldItemEditorLayout.swingStyleAt(mouseX: Int, mouseY: Int): HeldItemSwingStyle? =
    HeldItemSwingStyle.entries.takeIf { isAdvancedExpanded }?.firstOrNull {
        swingStyleBounds(it).contains(mouseX, mouseY)
    }

private fun HeldItemEditorLayout.swingStyleRowBounds(): Rect {
    val panel = panelBounds()
    return Rect(
        panel.x + EditorPanel.INSET,
        panel.y + EditorAdvanced.CONTENT_Y,
        panel.width - EditorPanel.INSET * 2,
        EditorAdvanced.STYLE_ROW_HEIGHT,
    )
}

private fun HeldItemEditorLayout.visibleSliderFields(): List<TransformField> = if (isAdvancedExpanded) {
    EditorSliderFields.ALL
} else {
    EditorSliderFields.BASIC
}

private data class EditorActionBounds(
    val reset: Rect,
    val undo: Rect,
    val texture: Rect,
    val redo: Rect,
    val done: Rect,
)

private fun previewButtonBounds(layout: HeldItemEditorLayout): Rect = Rect(
    layout.panelX + EditorHeader.PREVIEW_LEFT_INSET,
    layout.panelY + EditorHeader.BUTTON_Y,
    EditorHeader.PREVIEW_SIZE,
    EditorHeader.BUTTON_HEIGHT,
)

private object HeldItemEditorRenderer {
    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        state: HeldItemEditorState,
        layout: HeldItemEditorLayout,
        canUndo: Boolean,
        canRedo: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val bounds = layout.panelBounds()
        OverlayPanelStyle.draw(context, bounds.x, bounds.y, bounds.width, bounds.height)
        context.fill(
            bounds.x + EditorPanel.BORDER,
            bounds.y + EditorPanel.BORDER,
            bounds.x + bounds.width - EditorPanel.BORDER,
            bounds.y + EditorHeader.HEIGHT,
            EditorColors.HEADER,
        )
        val item = state.previewItem()
        val itemName = if (item.isEmpty) Component.literal("Empty hand") else item.hoverName
        val hasItem = !item.isEmpty
        val itemTextWidth = EditorHeader.contentWidth(bounds.width) - if (hasItem) {
            EditorHeader.ITEM_SIZE + EditorHeader.ITEM_TEXT_GAP
        } else {
            0
        }
        val itemText = fitText(font, itemName, itemTextWidth)
        val itemGroupWidth = font.width(itemText) + if (hasItem) {
            EditorHeader.ITEM_SIZE + EditorHeader.ITEM_TEXT_GAP
        } else {
            0
        }
        val itemGroupX = bounds.x + (bounds.width - itemGroupWidth) / 2
        val itemTextX = itemGroupX + if (hasItem) EditorHeader.ITEM_SIZE + EditorHeader.ITEM_TEXT_GAP else 0
        if (hasItem) context.item(item, itemGroupX, bounds.y + EditorHeader.ITEM_Y)
        context.text(
            font,
            itemText,
            itemTextX,
            bounds.y + EditorHeader.TEXT_Y,
            EditorColors.WHITE_TEXT,
            false,
        )
        if (state.isTextureToggleVisible()) {
            val usesVanillaTexture = state.usesVanillaTexture()
            drawTextureToggle(
                context,
                font,
                layout.textureToggleBounds(),
                usesVanillaTexture,
                state.canToggleTexture(),
                mouseX,
                mouseY,
            )
            if (layout.textureToggleBounds().contains(mouseX, mouseY)) {
                SkysoftNativeTooltip.setForNextFrame(context, listOf(state.textureTooltip()), mouseX, mouseY)
            }
        }
        drawPreviewButton(context, font, previewButtonBounds(layout), mouseX, mouseY)
        drawButton(
            context,
            font,
            layout.closeBounds(),
            "X",
            false,
            mouseX,
            mouseY,
            tone = PixelButtonTone.DANGER,
        )
        val itemId = state.currentItemId()

        drawTargetTabs(context, font, state, layout, mouseX, mouseY, itemId != null)
        layout.visibleSliderFields().forEach { drawSlider(context, font, state, layout, it, mouseX, mouseY) }
        drawAdvancedToggle(context, font, layout, mouseX, mouseY)
        if (layout.isAdvancedExpanded) {
            drawSwingStyle(context, font, state, layout, mouseX, mouseY)
        }
        drawButton(
            context,
            font,
            layout.resetBounds(),
            if (state.target == EditTarget.GLOBAL) "Reset" else "Use Global",
            false,
            mouseX,
            mouseY,
            enabled = state.canResetCurrentTarget(),
            tone = PixelButtonTone.DANGER,
        )
        drawHistoryButton(context, font, layout.undoBounds(), UNDO_ICON, canUndo, mouseX, mouseY)
        drawHistoryButton(context, font, layout.redoBounds(), REDO_ICON, canRedo, mouseX, mouseY)
        if (layout.undoBounds().contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("Undo"), mouseX, mouseY)
        } else if (layout.redoBounds().contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("Redo"), mouseX, mouseY)
        }
        drawButton(
            context,
            font,
            layout.doneBounds(),
            "Done",
            false,
            mouseX,
            mouseY,
            tone = PixelButtonTone.CONFIRM,
        )
    }

    private fun drawPreviewButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        mouseX: Int,
        mouseY: Int,
    ) {
        PixelButtonRenderer.draw(context, font, bounds, "", false, bounds.contains(mouseX, mouseY), true)
        drawPixelIcon(context, bounds, PREVIEW_ICON, EditorHeader.PREVIEW_ICON_SCALE, true)
        if (bounds.contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("Preview swing"), mouseX, mouseY)
        }
    }

    private fun drawAdvancedToggle(
        context: GuiGraphicsExtractor,
        font: Font,
        layout: HeldItemEditorLayout,
        mouseX: Int,
        mouseY: Int,
    ) {
        val bounds = layout.advancedToggleBounds()
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            "",
            layout.isAdvancedExpanded,
            bounds.contains(mouseX, mouseY),
            true,
        )
        drawPixelIcon(
            context,
            bounds,
            if (layout.isAdvancedExpanded) COLLAPSE_ICON else EXPAND_ICON,
            EditorAdvanced.TOGGLE_ICON_SCALE,
            true,
        )
        if (bounds.contains(mouseX, mouseY)) {
            val tooltip = if (layout.isAdvancedExpanded) "Fewer options" else "More options"
            SkysoftNativeTooltip.setForNextFrame(context, listOf(tooltip), mouseX, mouseY)
        }
    }

    private fun drawSwingStyle(
        context: GuiGraphicsExtractor,
        font: Font,
        state: HeldItemEditorState,
        layout: HeldItemEditorLayout,
        mouseX: Int,
        mouseY: Int,
    ) {
        val labelBounds = layout.swingStyleRowBounds()
        context.text(
            font,
            "Swing Style",
            labelBounds.x,
            layout.swingStyleBounds(HeldItemSwingStyle.VANILLA).y + EditorAdvanced.STYLE_TEXT_Y,
            EditorColors.MUTED_TEXT,
            false,
        )
        HeldItemSwingStyle.entries.forEach { style ->
            drawButton(
                context,
                font,
                layout.swingStyleBounds(style),
                style.label,
                state.displayTransform().swingStyle == style,
                mouseX,
                mouseY,
            )
        }
    }

    private fun drawTextureToggle(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        usesVanillaTexture: Boolean,
        enabled: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = enabled && bounds.contains(mouseX, mouseY)
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            "",
            false,
            hovered,
            enabled,
            if (usesVanillaTexture) PixelButtonTone.CONFIRM else PixelButtonTone.DANGER,
        )
        val icon = if (usesVanillaTexture) TEXTURE_RESTORE_ICON else TEXTURE_REMOVE_ICON
        drawPixelIcon(context, bounds, icon, EditorActions.TEXTURE_ICON_SCALE, enabled)
    }

    private fun drawHistoryButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        icon: List<String>,
        enabled: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        PixelButtonRenderer.draw(
            context,
            font,
            bounds,
            "",
            false,
            enabled && bounds.contains(mouseX, mouseY),
            enabled,
        )
        drawPixelIcon(context, bounds, icon, EditorActions.HISTORY_ICON_SCALE, enabled)
    }

    private fun drawPixelIcon(
        context: GuiGraphicsExtractor,
        bounds: Rect,
        icon: List<String>,
        scale: Int,
        enabled: Boolean,
    ) {
        val iconWidth = icon.maxOf(String::length) * scale
        val iconHeight = icon.size * scale
        val startX = bounds.x + (bounds.width - iconWidth) / 2
        val startY = bounds.y + (bounds.height - iconHeight) / 2
        for ((row, pixels) in icon.withIndex()) {
            for ((column, pixel) in pixels.withIndex()) {
                if (pixel != ICON_PIXEL) continue
                val x = startX + column * scale
                val y = startY + row * scale
                context.fill(
                    x,
                    y,
                    x + scale,
                    y + scale,
                    if (enabled) EditorColors.WHITE_TEXT else EditorColors.DISABLED_TEXT,
                )
            }
        }
    }

    private const val ICON_PIXEL = 'X'
    private val TEXTURE_REMOVE_ICON = listOf("X...X", ".X.X.", "..X..", ".X.X.", "X...X")
    private val TEXTURE_RESTORE_ICON = listOf("..X....", ".XX....", "XXXXXXX", ".XX....", "..X....")
    private val UNDO_ICON = listOf("..X..", ".XX..", "XXXXX", ".XX..", "..X..")
    private val REDO_ICON = listOf("..X..", "..XX.", "XXXXX", "..XX.", "..X..")
    private val PREVIEW_ICON = listOf("X....", "XXX..", "XXXXX", "XXX..", "X....")
    private val EXPAND_ICON = listOf("X...X", ".X.X.", "..X..")
    private val COLLAPSE_ICON = listOf("..X..", ".X.X.", "X...X")

    private fun drawTargetTabs(
        context: GuiGraphicsExtractor,
        font: Font,
        state: HeldItemEditorState,
        layout: HeldItemEditorLayout,
        mouseX: Int,
        mouseY: Int,
        hasItemId: Boolean,
    ) {
        drawButton(
            context,
            font,
            layout.targetBounds(EditTarget.GLOBAL),
            "Global",
            state.target == EditTarget.GLOBAL,
            mouseX,
            mouseY,
        )
        val itemBounds = layout.targetBounds(EditTarget.ITEM)
        drawButton(
            context,
            font,
            itemBounds,
            "This Item",
            state.target == EditTarget.ITEM,
            mouseX,
            mouseY,
            hasItemId,
        )
        if (!hasItemId && itemBounds.contains(mouseX, mouseY)) {
            SkysoftNativeTooltip.setForNextFrame(context, listOf("Requires a SkyBlock ID"), mouseX, mouseY)
        }
    }

    private fun drawSlider(
        context: GuiGraphicsExtractor,
        font: Font,
        state: HeldItemEditorState,
        layout: HeldItemEditorLayout,
        field: TransformField,
        mouseX: Int,
        mouseY: Int,
    ) {
        val row = layout.sliderRowBounds(field)
        val track = layout.sliderTrackBounds(field)
        val value = field.value(state.displayTransform())
        val progress = ((value - field.min) / (field.max - field.min)).coerceIn(0f, 1f)
        val fillWidth = (track.width * progress).roundToInt()
        context.text(font, field.label, row.x, row.y + EditorSliders.TEXT_Y, EditorColors.MUTED_TEXT, false)
        context.fill(track.x, track.y, track.x + track.width, track.y + track.height, EditorColors.SLIDER_TRACK)
        context.fill(track.x, track.y, track.x + fillWidth, track.y + track.height, EditorColors.ACCENT)
        val knobX = (track.x + fillWidth).coerceIn(track.x, track.x + track.width)
        context.fill(
            knobX - EditorSliders.KNOB_HALF_WIDTH,
            track.y - EditorSliders.KNOB_OVERHANG,
            knobX + EditorSliders.KNOB_HALF_WIDTH,
            track.y + track.height + EditorSliders.KNOB_OVERHANG,
            if (row.contains(mouseX, mouseY)) EditorColors.WHITE_TEXT else EditorColors.SLIDER_KNOB,
        )
        val valueText = field.formattedValue(value)
        context.text(
            font,
            valueText,
            row.x + row.width - font.width(valueText),
            row.y + EditorSliders.TEXT_Y,
            EditorColors.WHITE_TEXT,
            false,
        )
    }

    private fun drawButton(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        label: String,
        selected: Boolean,
        mouseX: Int,
        mouseY: Int,
        enabled: Boolean = true,
        tone: PixelButtonTone = PixelButtonTone.NORMAL,
    ) {
        val hovered = enabled && bounds.contains(mouseX, mouseY)
        PixelButtonRenderer.draw(context, font, bounds, label, selected, hovered, enabled, tone)
    }

    private fun fitText(font: Font, text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        val suffix = "..."
        return font.plainSubstrByWidth(text, maxWidth - font.width(suffix)) + suffix
    }

    private fun fitText(font: Font, text: Component, maxWidth: Int): FormattedCharSequence {
        if (font.width(text) <= maxWidth) return text.visualOrderText
        val suffix = FormattedText.of("...", text.style)
        val head = font.substrByWidth(text, (maxWidth - font.width(suffix)).coerceAtLeast(0))
        return Language.getInstance().getVisualOrder(FormattedText.composite(head, suffix))
    }
}

internal enum class EditTarget {
    GLOBAL,
    ITEM,
}

private enum class DragKind {
    PANEL,
    MOVE_ITEM,
    MOVE_DEPTH,
    SLIDER,
}

private enum class EditorInputHandlingResult {
    HANDLED,
    UNHANDLED,
}

private val EDITOR_MOUSE_BUTTONS = setOf(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT)

internal enum class TransformField(
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
) {
    X("X", HeldItemTransformLimits.MIN_X, HeldItemTransformLimits.MAX_X, EditorInput.POSITION_SCROLL_STEP),
    Y("Y", HeldItemTransformLimits.MIN_Y, HeldItemTransformLimits.MAX_Y, EditorInput.POSITION_SCROLL_STEP),
    Z("Z", HeldItemTransformLimits.MIN_Z, HeldItemTransformLimits.MAX_Z, EditorInput.DEPTH_SCROLL_STEP),
    SCALE("Scale", HeldItemTransformLimits.MIN_SCALE, HeldItemTransformLimits.MAX_SCALE, EditorInput.SCALE_SCROLL_STEP),
    SWING(
        "Swing",
        HeldItemTransformLimits.MIN_SWING_SPEED,
        HeldItemTransformLimits.MAX_SWING_SPEED,
        EditorInput.SWING_SCROLL_STEP,
    ),
    ROTATION_X(
        "Rotate X",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        EditorInput.ROTATION_SCROLL_STEP,
    ),
    ROTATION_Y(
        "Rotate Y",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        EditorInput.ROTATION_SCROLL_STEP,
    ),
    ROTATION_Z(
        "Rotate Z",
        HeldItemTransformLimits.MIN_ROTATION,
        HeldItemTransformLimits.MAX_ROTATION,
        EditorInput.ROTATION_SCROLL_STEP,
    ),
    ;

    fun value(transform: HeldItemTransformConfig): Float = when (this) {
        X -> transform.x
        Y -> transform.y
        Z -> transform.z
        SCALE -> transform.scale
        SWING -> transform.swingSpeed
        ROTATION_X -> transform.rotationX
        ROTATION_Y -> transform.rotationY
        ROTATION_Z -> transform.rotationZ
    }

    fun setValue(transform: HeldItemTransformConfig, value: Float) {
        when (this) {
            X -> transform.x = value
            Y -> transform.y = value
            Z -> transform.z = value
            SCALE -> transform.scale = value
            SWING -> transform.swingSpeed = value
            ROTATION_X -> transform.rotationX = value
            ROTATION_Y -> transform.rotationY = value
            ROTATION_Z -> transform.rotationZ = value
        }
    }

    fun formattedValue(value: Float): String {
        if (this in EditorSliderFields.ROTATION) return String.format(Locale.US, "%.0f°", value)
        val text = String.format(Locale.US, "%.2f", value)
        return if (this == SCALE || this == SWING) "${text}x" else text
    }
}

private object EditorSliderFields {
    val BASIC = listOf(
        TransformField.X,
        TransformField.Y,
        TransformField.Z,
        TransformField.SCALE,
        TransformField.SWING,
    )
    val ROTATION = listOf(
        TransformField.ROTATION_X,
        TransformField.ROTATION_Y,
        TransformField.ROTATION_Z,
    )
    val ALL = BASIC + ROTATION
}

private val HeldItemSwingStyle.label: String
    get() = when (this) {
        HeldItemSwingStyle.VANILLA -> "Vanilla"
        HeldItemSwingStyle.ITEM_ONLY -> "Item Only"
    }

private object EditorPanel {
    const val WIDTH = 224
    const val MIN_WIDTH = 184
    const val BASE_HEIGHT = 190
    const val MARGIN = 8
    const val INSET = 9
    const val BORDER = 1
}

private object EditorHeader {
    const val HEIGHT = 24
    const val ITEM_SIZE = 16
    const val ITEM_Y = (HEIGHT - ITEM_SIZE) / 2
    const val ITEM_TEXT_GAP = 4
    const val TEXT_HEIGHT = 9
    const val TEXT_BASELINE_OFFSET = 2
    const val TEXT_Y = (HEIGHT - TEXT_HEIGHT + TEXT_BASELINE_OFFSET) / 2
    const val CONTENT_INSET = 22
    const val BUTTON_HEIGHT = 14
    const val BUTTON_Y = (HEIGHT - BUTTON_HEIGHT) / 2
    const val CLOSE_RIGHT_INSET = 18
    const val CLOSE_SIZE = 14
    const val PREVIEW_LEFT_INSET = 4
    const val PREVIEW_SIZE = 14
    const val PREVIEW_ICON_SCALE = 1

    fun contentWidth(headerWidth: Int): Int = headerWidth - CONTENT_INSET * 2
}

private object EditorTabs {
    const val HEADER_GAP = 6
    const val TARGET_Y = EditorHeader.HEIGHT + HEADER_GAP
    const val HEIGHT = 17
}

private object EditorSliders {
    const val START_Y = 48
    const val ROW_HEIGHT = 22
    const val TEXT_Y = 6
    const val TRACK_Y = 9
    const val TRACK_HEIGHT = 3
    const val LABEL_WIDTH = 42
    const val RESERVED_WIDTH = 84
    const val KNOB_HALF_WIDTH = 2
    const val KNOB_OVERHANG = 2
}

private object EditorAdvanced {
    const val TOGGLE_Y = 154
    const val TOGGLE_WIDTH = 18
    const val TOGGLE_HEIGHT = 10
    const val TOGGLE_ICON_SCALE = 1
    const val CONTENT_Y = 164
    const val STYLE_ROW_HEIGHT = 16
    const val STYLE_LABEL_WIDTH = 66
    const val STYLE_BUTTON_GAP = 4
    const val STYLE_BUTTON_Y = 0
    const val STYLE_BUTTON_HEIGHT = 16
    const val STYLE_TEXT_Y = 4
    const val ROTATION_START_Y = CONTENT_Y + STYLE_ROW_HEIGHT
    const val ROTATION_ROW_HEIGHT = 16
    const val ROTATION_LABEL_WIDTH = 50
    const val ROTATION_RESERVED_WIDTH = 92
    const val EXPANDED_HEIGHT = STYLE_ROW_HEIGHT + ROTATION_ROW_HEIGHT * 3
}

private object EditorActions {
    private const val BASE_Y = 166
    const val HEIGHT = 16
    const val RESET_WIDTH = 70
    const val COMPACT_RESET_WIDTH = 58
    const val DONE_WIDTH = 48
    const val COMPACT_DONE_WIDTH = 34
    const val COMPACT_CONTENT_WIDTH = 190
    const val HISTORY_SIZE = 16
    const val TEXTURE_WIDTH = 32
    const val TEXTURE_ICON_SCALE = 2
    const val HISTORY_ICON_SCALE = 2
    const val GROUP_GAP = 4
    const val GROUP_BASE_WIDTH = HISTORY_SIZE * 2 + TEXTURE_WIDTH

    fun y(isAdvancedExpanded: Boolean): Int = if (isAdvancedExpanded) {
        BASE_Y + EditorAdvanced.EXPANDED_HEIGHT
    } else {
        BASE_Y
    }
}

private object EditorInput {
    const val SAVE_DELAY_MILLIS = 400L
    const val DEPTH_PER_PIXEL = 0.004f
    const val POSITION_SCROLL_STEP = 0.05f
    const val DEPTH_SCROLL_STEP = 0.05f
    const val SCALE_SCROLL_STEP = 0.05f
    const val SWING_SCROLL_STEP = 0.05f
    const val ROTATION_SCROLL_STEP = 5f
}

private object EditorAnimation {
    const val DURATION_NANOS = 220_000_000L
    const val LINE_PHASE_END = 0.2f
    const val LINE_HEIGHT = 1
    const val LINE_RGB = 0x005D6872
    const val MAX_ALPHA = 255
    const val ALPHA_SHIFT = 24
    const val HIDDEN_MOUSE_COORDINATE = Int.MIN_VALUE
}

private object EditorColors {
    val HEADER = 0x801B2530.toInt()
    val WHITE_TEXT = 0xFFFFFFFF.toInt()
    val MUTED_TEXT = 0xFF9AA4AE.toInt()
    val DISABLED_TEXT = 0xFF606870.toInt()
    val BUTTON_OUTLINE = 0x805A626A.toInt()
    val SLIDER_TRACK = 0xFF30363B.toInt()
    val SLIDER_KNOB = 0xFFB9C2CA.toInt()
    val ACCENT = 0xFF45A3FF.toInt()
}
