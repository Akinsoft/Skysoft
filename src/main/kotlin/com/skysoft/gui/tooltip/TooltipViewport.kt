package com.skysoft.gui.tooltip

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.TooltipScrollConfig
import com.skysoft.mixin.ClientTextTooltipAccessor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.Rect
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

interface TooltipScrollExcludedScreen

interface TooltipScrollPriorityScreen {
    val mouseScrollPriorityAreas: List<Rect>
}

object TooltipViewport {
    private val minecraft = Minecraft.getInstance()
    private var session: TooltipPanSession? = null
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
    fun didHandleCompetingMouseScroll(horizontal: Double, vertical: Double): Boolean =
        didHandleMouseScroll(horizontal, vertical, config().settings.interfaceScrollTooltipKey)

    @JvmStatic
    fun isCompetingScrollKeyDown(): Boolean =
        isKeyDown(config().settings.interfaceScrollTooltipKey)

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
        val frame = TooltipPanFrame(
            x = base.x(),
            y = base.y(),
            width = tooltipWidth,
            height = tooltipHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        val now = System.nanoTime()
        val isExpired = !hasVisibleSession(now)
        val activeSession = session
        val hasChangedTarget = activeSession == null || activeSession.screen !== screen ||
            activeSession.isDifferentTarget(identity, anchorX, anchorY)

        if (activeSession == null || activeSession.screen !== screen) {
            session = TooltipPanSession(
                screen = screen,
                identity = identity,
                anchorX = anchorX,
                anchorY = anchorY,
                frame = frame,
                observedAt = now,
                startOnTop = config().details.startOnTop,
            )
        } else {
            activeSession.observe(identity, anchorX, anchorY, frame, now)
            if (config().details.resetPositionWhenNotHovered && (isExpired || hasChangedTarget)) {
                activeSession.center()
                activeSession.alignTallTooltipToTop(config().details.startOnTop)
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
    private const val HASH_MULTIPLIER = 31
    private const val PERCENT_SCALE = 100.0
}

private fun isTooltipScrollEnabledForScreen(screen: Screen?, isEnabledInChat: Boolean): Boolean =
    screen !is TooltipScrollExcludedScreen && (screen !is ChatScreen || isEnabledInChat)
