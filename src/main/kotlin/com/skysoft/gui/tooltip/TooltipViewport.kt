package com.skysoft.gui.tooltip

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.TooltipScrollConfig
import com.skysoft.mixin.ClientTextTooltipAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.input.InputUtilities
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.util.FormattedCharSequence
import org.joml.Vector2i
import org.joml.Vector2ic
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface TooltipScrollExcludedScreen

object TooltipViewport {
    private val minecraft = Minecraft.getInstance()
    private var session: PanSession? = null
    private var wasResetKeyPressedLastTick = false

    @JvmStatic
    fun decorate(
        font: Font,
        components: List<ClientTooltipComponent>,
        anchorX: Int,
        anchorY: Int,
        original: ClientTooltipPositioner,
    ): ClientTooltipPositioner {
        val settings = config()
        if (
            original is TooltipViewportExcludedPositioner ||
            !settings.enabled ||
            !isEnabledForCurrentScreen(settings) ||
            components.isEmpty()
        ) return original
        return OffsetPositioner(original, tooltipIdentity(font, components), anchorX, anchorY)
    }

    @JvmStatic
    fun didHandleMouseScroll(horizontal: Double, vertical: Double): Boolean =
        didHandleMouseScroll(horizontal, vertical, GLFW.GLFW_KEY_UNKNOWN)

    @JvmStatic
    fun didHandleStorageMouseScroll(horizontal: Double, vertical: Double): Boolean =
        didHandleMouseScroll(horizontal, vertical, config().settings.storageOverlayTooltipKey)

    @JvmStatic
    fun isStorageOverlayScrollKeyDown(): Boolean =
        isKeyDown(config().settings.storageOverlayTooltipKey)

    @JvmStatic
    fun updateKeyboardPan() {
        val settings = config()
        if (!settings.enabled || !isEnabledForCurrentScreen(settings)) {
            clear()
            return
        }
        if (!hasVisibleSession()) {
            wasResetKeyPressedLastTick = false
            if (settings.details.resetPositionWhenNotHovered) session?.center()
            return
        }

        val activeSession = checkNotNull(session)
        val isResetPressed = isKeyDown(settings.settings.resetTooltipKey)
        if (isResetPressed && !wasResetKeyPressedLastTick) activeSession.center()
        wasResetKeyPressedLastTick = isResetPressed

        val speed = settings.settings.keyboardScrollingSpeed
        var x = 0.0
        var y = 0.0
        if (settings.settings.enableWASD) {
            if (isKeyDown(GLFW.GLFW_KEY_A)) x -= speed
            if (isKeyDown(GLFW.GLFW_KEY_D)) x += speed
            if (isKeyDown(GLFW.GLFW_KEY_W)) y -= speed
            if (isKeyDown(GLFW.GLFW_KEY_S)) y += speed
        }

        val isHorizontal = isHorizontalModifierDown(settings)
        if (isKeyDown(settings.settings.moveUpKey)) {
            if (isHorizontal) x -= speed else y -= speed
        }
        if (isKeyDown(settings.settings.moveDownKey)) {
            if (isHorizontal) x += speed else y += speed
        }
        if (x != 0.0 || y != 0.0) activeSession.panBy(x, y)
    }

    fun needsKeyboardUpdate(): Boolean = config().enabled || session != null

    @JvmStatic
    fun clear() {
        session = null
        wasResetKeyPressedLastTick = false
    }

    private fun didHandleMouseScroll(horizontal: Double, vertical: Double, ignoredHorizontalKey: Int): Boolean {
        val settings = config()
        if (
            !settings.enabled ||
            !settings.settings.enableScrollWheel ||
            !isEnabledForCurrentScreen(settings) ||
            !hasVisibleSession()
        ) return false

        val pansHorizontally = horizontal != 0.0 || isHorizontalModifierDown(settings, ignoredHorizontalKey)
        var x = horizontal * settings.settings.mouseScrollingSpeed
        var y = 0.0
        if (vertical != 0.0) {
            if (pansHorizontally) x += vertical * settings.settings.mouseScrollingSpeed
            else y = vertical * settings.settings.mouseScrollingSpeed
        }
        if (settings.details.invertHorizontalMovement) x = -x
        if (settings.details.invertVerticalMovement) y = -y
        if (x == 0.0 && y == 0.0) return false

        checkNotNull(session).panBy(x, y)
        return true
    }

    private fun place(
        original: ClientTooltipPositioner,
        identity: Int,
        anchorX: Int,
        anchorY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        x: Int,
        y: Int,
        tooltipWidth: Int,
        tooltipHeight: Int,
    ): Vector2ic {
        val base = original.positionTooltip(viewportWidth, viewportHeight, x, y, tooltipWidth, tooltipHeight)
        val screen = MinecraftClient.screen(minecraft)
        val frame = TooltipFrame(base.x(), base.y(), tooltipWidth, tooltipHeight, viewportWidth, viewportHeight)
        val now = System.nanoTime()
        val isExpired = !hasVisibleSession(now)
        val activeSession = session
        val hasChangedTarget = activeSession == null || activeSession.screen !== screen ||
            activeSession.isDifferentTarget(identity, anchorX, anchorY)

        if (activeSession == null || activeSession.screen !== screen) {
            session = PanSession(screen, identity, anchorX, anchorY, frame, now)
        } else {
            activeSession.observe(identity, anchorX, anchorY, frame, now)
            if (config().details.resetPositionWhenNotHovered && (isExpired || hasChangedTarget)) {
                activeSession.center()
                activeSession.alignTallTooltipToTop()
            }
        }

        val currentSession = checkNotNull(session)
        currentSession.advance(config().details.scrollSmoothness / PERCENT_SCALE)
        return Vector2i(base.x() + currentSession.roundedX(), base.y() + currentSession.roundedY())
    }

    private fun hasVisibleSession(): Boolean = hasVisibleSession(System.nanoTime())

    private fun hasVisibleSession(now: Long): Boolean {
        val activeSession = session ?: return false
        return activeSession.screen === MinecraftClient.screen(minecraft) &&
            now - activeSession.lastObservedNanos <= VISIBILITY_GRACE_NANOS
    }

    private fun isEnabledForCurrentScreen(settings: TooltipScrollConfig): Boolean =
        isTooltipScrollEnabledForScreen(MinecraftClient.screen(minecraft), settings.settings.isEnabledInChat)

    private fun isHorizontalModifierDown(
        settings: TooltipScrollConfig,
        ignoredKey: Int = GLFW.GLFW_KEY_UNKNOWN,
    ): Boolean {
        val usesLeftShift = settings.details.useLeftShift && ignoredKey != GLFW.GLFW_KEY_LEFT_SHIFT &&
            isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)
        val usesConfiguredKey = settings.settings.horizontalMovementKey != ignoredKey &&
            isKeyDown(settings.settings.horizontalMovementKey)
        return usesLeftShift || usesConfiguredKey
    }

    private fun isKeyDown(key: Int): Boolean = InputUtilities.isBindingDown(key)

    private fun tooltipIdentity(font: Font, components: List<ClientTooltipComponent>): Int {
        var result = 1
        for (component in components) {
            result = HASH_MULTIPLIER * result + component.javaClass.hashCode()
            result = HASH_MULTIPLIER * result + component.getWidth(font)
            result = HASH_MULTIPLIER * result + component.getHeight(font)
            if (component is ClientTextTooltipAccessor) {
                result = HASH_MULTIPLIER * result + textIdentity(component.skysoftGetText())
            }
        }
        return result
    }

    private fun textIdentity(text: FormattedCharSequence): Int {
        var result = 1
        text.accept { _, style, codePoint ->
            result = HASH_MULTIPLIER * result + codePoint
            result = HASH_MULTIPLIER * result + style.hashCode()
            true
        }
        return result
    }

    private fun config(): TooltipScrollConfig = SkysoftConfigGui.config().inventory.tooltipScroll

    private class PanSession(
        val screen: Screen?,
        identity: Int,
        anchorX: Int,
        anchorY: Int,
        frame: TooltipFrame,
        observedAt: Long,
    ) {
        private var identity = identity
        private var anchorX = anchorX
        private var anchorY = anchorY
        private var frame = frame
        var lastObservedNanos = observedAt
            private set
        private var targetX = 0.0
        private var targetY = 0.0
        private var displayedX = 0.0
        private var displayedY = 0.0

        init {
            clampMotion()
            alignTallTooltipToTop()
        }

        fun isDifferentTarget(nextIdentity: Int, nextAnchorX: Int, nextAnchorY: Int): Boolean =
            identity != nextIdentity || abs(anchorX - nextAnchorX) > ANCHOR_TOLERANCE ||
                abs(anchorY - nextAnchorY) > ANCHOR_TOLERANCE

        fun observe(
            nextIdentity: Int,
            nextAnchorX: Int,
            nextAnchorY: Int,
            nextFrame: TooltipFrame,
            observedAt: Long,
        ) {
            identity = nextIdentity
            anchorX = nextAnchorX
            anchorY = nextAnchorY
            frame = nextFrame
            lastObservedNanos = observedAt
            clampMotion()
        }

        fun panBy(x: Double, y: Double) {
            targetX += x
            targetY += y
            clampMotion()
        }

        fun center() {
            targetX = 0.0
            targetY = 0.0
            displayedX = 0.0
            displayedY = 0.0
        }

        fun alignTallTooltipToTop() {
            if (
                !config().details.startOnTop ||
                frame.height <= frame.viewportHeight - EDGE_GAP * 2 ||
                frame.y >= EDGE_GAP
            ) return
            targetY = EDGE_GAP - frame.y.toDouble()
            displayedY = targetY
            clampMotion()
        }

        fun advance(amount: Double) {
            if (amount >= 1.0) {
                displayedX = targetX
                displayedY = targetY
                return
            }
            displayedX = settle(displayedX + (targetX - displayedX) * amount, targetX)
            displayedY = settle(displayedY + (targetY - displayedY) * amount, targetY)
        }

        private fun clampMotion() {
            val bounds = frame.bounds()
            targetX = bounds.clampX(targetX)
            targetY = bounds.clampY(targetY)
            displayedX = bounds.clampX(displayedX)
            displayedY = bounds.clampY(displayedY)
        }

        fun roundedX(): Int = Math.round(displayedX).toInt()

        fun roundedY(): Int = Math.round(displayedY).toInt()

        private fun settle(value: Double, target: Double): Double =
            if (abs(target - value) < SETTLE_TOLERANCE) target else value
    }

    private data class TooltipFrame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val viewportWidth: Int,
        val viewportHeight: Int,
    ) {
        fun bounds() = PanBounds(
            EDGE_GAP - width - x,
            viewportWidth - EDGE_GAP - x,
            EDGE_GAP - height - y,
            viewportHeight - EDGE_GAP - y,
        )
    }

    private data class PanBounds(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
        fun clampX(value: Double): Double = clamp(value, minX, maxX)

        fun clampY(value: Double): Double = clamp(value, minY, maxY)

        private fun clamp(value: Double, minimum: Int, maximum: Int): Double {
            if (minimum > maximum) return 0.0
            return max(minimum.toDouble(), min(value, maximum.toDouble()))
        }
    }

    private data class OffsetPositioner(
        val original: ClientTooltipPositioner,
        val identity: Int,
        val anchorX: Int,
        val anchorY: Int,
    ) : ClientTooltipPositioner {
        override fun positionTooltip(
            screenWidth: Int,
            screenHeight: Int,
            x: Int,
            y: Int,
            tooltipWidth: Int,
            tooltipHeight: Int,
        ): Vector2ic = place(
            original,
            identity,
            anchorX,
            anchorY,
            screenWidth,
            screenHeight,
            x,
            y,
            tooltipWidth,
            tooltipHeight,
        )
    }

    private const val VISIBILITY_GRACE_NANOS = 250_000_000L
    private const val EDGE_GAP = 4
    private const val ANCHOR_TOLERANCE = 12
    private const val HASH_MULTIPLIER = 31
    private const val PERCENT_SCALE = 100.0
    private const val SETTLE_TOLERANCE = 0.05
}

internal fun isTooltipScrollEnabledForScreen(screen: Screen?, isEnabledInChat: Boolean): Boolean =
    screen !is TooltipScrollExcludedScreen && (screen !is ChatScreen || isEnabledInChat)
