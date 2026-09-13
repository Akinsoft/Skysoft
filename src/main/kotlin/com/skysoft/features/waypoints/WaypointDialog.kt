package com.skysoft.features.waypoints

import com.skysoft.gui.SkysoftEditorScreen
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.PixelButtonTone
import com.skysoft.utils.gui.PixelControlColors
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

internal abstract class WaypointDialog(
    title: String,
    val parentScreen: Screen?,
    private val primaryLabel: String? = "Save",
) : SkysoftEditorScreen(Component.literal(title), parentScreen) {
    protected val form = WaypointForm()
    private val transition = PanelFadeTransition().also { it.show() }
    private var controls = emptyList<WaypointPanelControl>()
    private var viewport = Rect(0, 0, 0, 0)
    private var bounds = Rect(0, 0, 0, 0)
    private var dismissAll = false
    private var dismissClick: MouseButtonEvent? = null
    protected var scroll = 0
    protected var error = ""
    protected var busy = false
        private set
    protected val opacity: Double get() = transition.opacity()
    protected val interactive: Boolean get() = transition.isInteractive && !busy
    protected var bodyWidth = DEFAULT_WIDTH - INSET * 2
        private set
    protected abstract val contentHeight: Int
    protected open val headerHeight = 24
    open val preferredWidth: Int = DEFAULT_WIDTH
    private val chromeHeight: Int get() = headerHeight + FOOTER_HEIGHT + if (error.isEmpty()) 0 else ERROR_HEIGHT

    protected abstract fun drawBody(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, x: Int, y: Int, width: Int)

    protected abstract fun confirm()

    protected open fun isPrimaryEnabled(): Boolean = !busy

    protected fun isBodyRowVisible(y: Int, rowHeight: Int): Boolean =
        y + rowHeight > viewport.y && y < viewport.y + viewport.height

    protected open fun drawHeader(painter: WaypointPanelPainter, bounds: Rect) {
        painter.text(bounds, OverlayTextStyle.title(title.string), PixelControlColors.TEXT)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        bodyWidth = (minOf(preferredWidth, width - WaypointPanelLayout.MARGIN * 2) - INSET * 2).coerceAtLeast(1)
        val contentHeight = contentHeight
        bounds = WaypointPanelLayout.create(width, height).beside(preferredWidth, contentHeight + chromeHeight, width, height)
        val panelWidth = bounds.width
        val panelHeight = bounds.height
        val x = bounds.x
        val y = bounds.y
        val alpha = opacity
        if (!transition.isVisible) return
        OverlayPanelStyle.draw(context, x, y, panelWidth, panelHeight, alpha)
        val headerPainter = WaypointPanelPainter(context, mouseX, mouseY, alpha, interactive)
        drawHeader(headerPainter, Rect(x + INSET, y + INSET, bodyWidth, headerHeight - INSET))
        viewport = Rect(x + INSET, y + headerHeight + GAP, bodyWidth, (panelHeight - chromeHeight).coerceAtLeast(0))
        scroll = scroll.coerceIn(0, (contentHeight - viewport.height).coerceAtLeast(0))
        form.beginFrame()
        val bodyPainter = WaypointPanelPainter(
            context,
            if (interactive && viewport.contains(mouseX, mouseY)) mouseX else -1,
            if (interactive && viewport.contains(mouseX, mouseY)) mouseY else -1,
            alpha, interactive,
        )
        context.enableScissor(viewport.x, viewport.y, viewport.x + viewport.width, viewport.y + viewport.height)
        try {
            drawBody(context, bodyPainter, viewport.x, viewport.y - scroll, viewport.width)
        } finally {
            context.disableScissor()
        }
        val footerPainter = WaypointPanelPainter(context, mouseX, mouseY, alpha, interactive)
        val footerY = y + panelHeight - FOOTER_INSET
        if (error.isNotEmpty()) footerPainter.info(Rect(x + INSET, footerY - ERROR_HEIGHT, bodyWidth, TEXT_HEIGHT), error)
        drawFooter(context, footerPainter, Rect(x + INSET, footerY, bodyWidth, FIELD_HEIGHT))
        controls = headerPainter.controls + bodyPainter.controls.mapNotNull { control ->
            val clipped = control.bounds.intersection(viewport) ?: return@mapNotNull null
            control.copy(bounds = clipped)
        } + footerPainter.controls
        if (interactive) controls.lastOrNull { it.bounds.contains(mouseX, mouseY) && it.tooltip.isNotEmpty() }?.let {
            SkysoftNativeTooltip.setForNextFrame(context, it.tooltip, mouseX, mouseY, scrollable = false)
        }
    }

    protected open fun drawFooter(context: GuiGraphicsExtractor, painter: WaypointPanelPainter, bounds: Rect) {
        painter.action(Rect(bounds.x, bounds.y, BUTTON_WIDTH, bounds.height), "Back") { onClose() }
        primaryLabel?.let { label ->
            painter.action(
                Rect(bounds.x + bounds.width - PRIMARY_WIDTH, bounds.y, PRIMARY_WIDTH, bounds.height), label,
                tone = PixelButtonTone.CONFIRM, enabled = isPrimaryEnabled()
            ) { confirm() }
        }
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) = Unit

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        transition.hide()
    }

    override fun tick() {
        super.tick()
        transition.opacity()
        if (transition.isVisible) return
        if (dismissAll) {
            MinecraftClient.setScreen(WaypointPanel.menuParent())
            dismissClick?.let { click ->
                val x = click.x().toInt()
                val y = click.y().toInt()
                if (WaypointPanel.containsPoint(x, y)) WaypointPanelInput.didClick(click, x, y)
            }
        } else super.onClose()
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!transition.isInteractive) return true
        val x = click.x().toInt()
        val y = click.y().toInt()
        if (!bounds.contains(x, y)) {
            dismissAll = true
            dismissClick = click
            onClose()
            return true
        }
        if (!interactive || click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && form.didClick(x, y, viewport)) return true
        val control = controls.lastOrNull { it.bounds.contains(x, y) && it.enabled } ?: return true
        val action = when (click.button()) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> control.action
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> control.rightClick
            else -> null
        } ?: return true
        SoundUtilities.playClickSound()
        perform(action)
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        interactive && WaypointPanelInput.didDrag(click)

    override fun mouseReleased(click: MouseButtonEvent): Boolean = WaypointPanelInput.didRelease(click)

    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        if (!interactive) return true
        if (!bounds.contains(x.toInt(), y.toInt())) return WaypointPanelInput.didScroll(x, y, vertical)
        if (!viewport.contains(x.toInt(), y.toInt()) || vertical == 0.0) return false
        scroll = (scroll - (vertical * SCROLL_STEP).toInt()).coerceIn(0, (contentHeight - viewport.height).coerceAtLeast(0))
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(event)
        if (!interactive) return true
        if (form.focused == null && event.hasControlDownWithQuirk()) {
            val action = when (event.key()) {
                GLFW.GLFW_KEY_Z -> Waypoints::undo
                GLFW.GLFW_KEY_Y -> Waypoints::redo
                else -> null
            }
            if (action != null) {
                perform(action)
                return true
            }
        }
        if (event.key() == GLFW.GLFW_KEY_TAB && !form.isEmpty) {
            scroll += form.focusNext(event.hasShiftDown(), viewport)
            return true
        }
        if (event.key() in ENTER_KEYS && primaryLabel != null) {
            if (isPrimaryEnabled()) perform { confirm() }
            return true
        }
        if (form.focused?.keyPressed(event) == InputHandlingResult.CONSUMED) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!interactive) return true
        val focused = form.focused ?: return super.charTyped(event)
        if (!event.isAllowedChatCharacter) return false
        focused.charTyped(event)
        return true
    }

    protected fun <T> transfer(work: () -> T, onFailure: () -> Unit = {}, done: (T) -> Unit) {
        check(!busy) { "Wait for the current transfer to finish." }
        busy = true
        error = ""
        CompletableFuture.supplyAsync(work, Util.ioPool()).whenCompleteAsync({ result, failure ->
            busy = false
            if (MinecraftClient.screen() !== this || !transition.isInteractive) return@whenCompleteAsync
            if (failure != null) {
                error = failure.cause?.message ?: failure.message.orEmpty()
                onFailure()
            } else perform { done(result) }
        }, Minecraft.getInstance())
    }

    protected fun perform(action: () -> Unit) {
        try {
            error = ""
            action()
        } catch (failure: IllegalArgumentException) {
            error = failure.message ?: "Invalid waypoint data."
        } catch (failure: IllegalStateException) {
            error = failure.message ?: "This action is unavailable."
        }
    }

    override fun beforeEditorClose() = Unit

    protected companion object {
        const val FIELD_HEIGHT = WaypointForm.FIELD_HEIGHT
        const val FIELD_LABEL_HEIGHT = WaypointForm.LABEL_HEIGHT
        const val FIELD_ROW = 34
        const val TEXT_HEIGHT = 10
        const val BUTTON_ROW = 22
        const val GAP = 4
        private const val INSET = 8
        private const val FOOTER_HEIGHT = 32
        private const val FOOTER_INSET = 24
        private const val ERROR_HEIGHT = 14
        private const val BUTTON_WIDTH = 60
        private const val PRIMARY_WIDTH = 90
        private const val DEFAULT_WIDTH = 260
        private const val SCROLL_STEP = 24
        private val ENTER_KEYS = setOf(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER)
    }
}
