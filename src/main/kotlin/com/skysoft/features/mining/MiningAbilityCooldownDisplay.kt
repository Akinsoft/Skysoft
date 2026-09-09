package com.skysoft.features.mining

import com.skysoft.config.CUSTOM_BAR_TRACK_COLOR
import com.skysoft.config.MiningAbilityBarOrientation
import com.skysoft.config.MiningAbilityReadyTextPosition
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.EasingUtilities
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.render.BarFillDirection
import com.skysoft.utils.render.drawGlossyProgressBar
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import com.skysoft.features.misc.conditions.FeatureConditionState
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB

object MiningAbilityCooldownDisplay {
    private val config get() = SkysoftConfigGui.config().mining.abilityCooldown
    private val conditions = FeatureConditionState()
    private var widget = MiningAbilityWidgetState(false, null)
    private var currentStatus: MiningAbilityStatus? = null
    private var cooldown = MiningAbilityCooldownTimer()

    fun register() {
        conditions.startSession(config.settings.locations)
        SkyBlockEventState.registerConsumer("Mining Ability Cooldown") { config.enabled }
        TabListApi.onChange(
            "Mining Ability Cooldown",
            isActive = ::isActive,
            listener = { updateTabState() },
        )
        ChatEvents.onVisibleMessage("Mining Ability Cooldown use", ::isActive) { message ->
            if (isMiningAbilityUse(message.cleanText)) recordAbilityUse()
            ChatMessageVisibility.SHOW
        }
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "mining_ability_cooldown",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ -> renderHud(context) },
            ),
            object : HudEditorElement {
                override val id: String = "mining_ability_cooldown"
                override val label: String = "Mining Ability Cooldown"
                override val position get() = config.position
                override val hasEditorBackground: Boolean = false
                override fun width(): Int = currentRenderable()?.width ?: 0
                override fun height(): Int = currentRenderable()?.height ?: 0
                override fun isVisible(): Boolean = currentRenderable() != null
                override fun renderEditor(context: GuiGraphicsExtractor) {
                    currentRenderable()?.render(context)
                }
                override fun openConfig() = SkysoftConfigGui.open("Mining Ability Cooldown")
            },
        )
    }

    internal fun markConditionsChanged() = conditions.markChanged()

    private fun isActive(): Boolean =
        config.enabled &&
            HypixelLocationState.inSkyBlock &&
            conditions.isActivationAllowed(config.settings.locations, heldItemId = null, isConditionActivationReversed = false)

    private fun recordAbilityUse() {
        val nowNanos = System.nanoTime()
        if (!widget.isVisible || currentStatus == null) return
        currentStatus = MiningAbilityStatus(remainingSeconds = 0)
        cooldown = MiningAbilityCooldownTimer(
            startedAtNanos = nowNanos,
            durationNanos = Long.MAX_VALUE,
            animateEmpty = true,
            waitingForTabCooldown = true,
        )
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        val renderable = currentRenderable() ?: return
        config.position.renderRenderable(context, renderable)
    }

    private fun updateTabState(nowNanos: Long = System.nanoTime()) {
        widget = parseMiningAbilityWidget(TabListApi.lines.map { it.cleanSkyBlockText() })
        updateStatus(widget.status, nowNanos)
    }

    private fun updateStatus(next: MiningAbilityStatus?, nowNanos: Long) {
        val previous = currentStatus
        if (next == previous || cooldown.waitingForTabCooldown && next?.isReady == true) return
        currentStatus = next
        val remaining = next?.remainingSeconds
        cooldown = when {
            cooldown.waitingForTabCooldown && remaining != null -> cooldown.confirmRemaining(remaining, nowNanos)
            remaining == null -> MiningAbilityCooldownTimer()
            previous == null || previous.isReady || previous.remainingSeconds?.let { remaining > it } == true ->
                MiningAbilityCooldownTimer(
                    startedAtNanos = nowNanos,
                    durationNanos = (remaining * NANOS_PER_SECOND).coerceAtLeast(NANOS_PER_SECOND),
                    animateEmpty = previous?.isReady == true,
                )
            else -> cooldown
        }
    }

    private fun currentRenderable(nowNanos: Long = System.nanoTime()): GuiRenderable? {
        if (!isActive() || MinecraftClient.isGuiHidden(Minecraft.getInstance())) return null
        val status = currentStatus
        if (status != null) {
            return abilityRenderable(status.isReady, if (status.isReady) 1f else cooldown.progress(nowNanos))
        }
        return missingWidgetRenderable().takeIf {
            !widget.isVisible &&
                TabListApi.isSkyBlockDataLoaded &&
                TabListApi.hasWaitedForSkyBlockData(WIDGET_LOAD_GRACE)
        }
    }

    private fun abilityRenderable(isReady: Boolean, progress: Float): GuiRenderable {
        val details = config.details
        val orientation = config.settings.orientation
        val barWidth = if (orientation == MiningAbilityBarOrientation.VERTICAL) SLIM_BAR_SIZE else LONG_BAR_SIZE
        val barHeight = if (orientation == MiningAbilityBarOrientation.VERTICAL) LONG_BAR_SIZE else SLIM_BAR_SIZE
        val fillDirection = if (orientation == MiningAbilityBarOrientation.VERTICAL) {
            BarFillDirection.UP
        } else {
            BarFillDirection.RIGHT
        }
        val emptyColor = details.emptyColor.get().toColor().rgb
        val readyColor = details.readyColor.get().toColor().rgb
        val bar = object : GuiRenderable {
            override val width = barWidth
            override val height = barHeight
            override val horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER
            override val verticalAlign = GuiAlignment.VerticalAlignment.CENTER

            override fun render(context: GuiGraphicsExtractor) {
                context.drawGlossyProgressBar(
                    0,
                    0,
                    width,
                    height,
                    progress,
                    ARGB.srgbLerp(progress, emptyColor, readyColor),
                    CUSTOM_BAR_TRACK_COLOR,
                    fillDirection,
                )
            }
        }
        val label = StringRenderable(
            READY_TEXT,
            color = readyColor,
            horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER,
            verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
        )
        val readyText = object : GuiRenderable by label {
            override fun render(context: GuiGraphicsExtractor) {
                if (isReady) label.render(context)
            }
        }
        return when (config.settings.readyText) {
            MiningAbilityReadyTextPosition.OFF -> bar
            MiningAbilityReadyTextPosition.RIGHT -> horizontalLayout(listOf(bar, readyText), READY_TEXT_SPACING)
            MiningAbilityReadyTextPosition.LEFT -> horizontalLayout(listOf(readyText, bar), READY_TEXT_SPACING)
            MiningAbilityReadyTextPosition.TOP -> verticalLayout(listOf(readyText, bar), READY_TEXT_SPACING)
            MiningAbilityReadyTextPosition.BOTTOM -> verticalLayout(listOf(bar, readyText), READY_TEXT_SPACING)
        }
    }
}

private data class MiningAbilityCooldownTimer(
    val startedAtNanos: Long = 0L,
    val durationNanos: Long = 0L,
    val animateEmpty: Boolean = false,
    val waitingForTabCooldown: Boolean = false,
) {
    fun confirmRemaining(remainingSeconds: Int, nowNanos: Long): MiningAbilityCooldownTimer = copy(
        durationNanos = (elapsed(nowNanos) + remainingSeconds * NANOS_PER_SECOND).coerceAtLeast(NANOS_PER_SECOND),
        waitingForTabCooldown = false,
    )

    fun progress(nowNanos: Long): Float {
        val elapsed = elapsed(nowNanos)
        val fill = (elapsed.toDouble() / durationNanos.coerceAtLeast(1L)).coerceIn(0.0, 1.0)
        if (!animateEmpty || elapsed >= EMPTY_ANIMATION_NANOS) return fill.toFloat()
        val emptying = EasingUtilities.smoothStep(elapsed.toDouble() / EMPTY_ANIMATION_NANOS)
        return (1.0 + (fill - 1.0) * emptying).toFloat()
    }

    private fun elapsed(nowNanos: Long): Long = (nowNanos - startedAtNanos).coerceAtLeast(0L)
}

private fun missingWidgetRenderable(): GuiRenderable = verticalLayout(
    listOf(
        StringRenderable("Pickaxe Ability Widget Missing", color = WARNING_COLOR),
        StringRenderable("Enable it with /widget", color = WARNING_COLOR),
    ),
    horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER,
)

internal data class MiningAbilityWidgetState(
    val isVisible: Boolean,
    val status: MiningAbilityStatus?,
)

internal data class MiningAbilityStatus(
    val remainingSeconds: Int?,
) {
    val isReady: Boolean get() = remainingSeconds == null
}

internal fun isMiningAbilityUse(message: String): Boolean = MINING_ABILITY_USED_PATTERN.matches(message)

internal fun parseMiningAbilityWidget(lines: Iterable<String>): MiningAbilityWidgetState {
    val tabLines = lines.toList()
    val headerIndex = tabLines.indexOf(PICKAXE_ABILITY_WIDGET_HEADER)
    if (headerIndex < 0) return MiningAbilityWidgetState(false, null)
    val match = tabLines.getOrNull(headerIndex + 1)?.let(PICKAXE_ABILITY_STATUS_PATTERN::matchEntire)
        ?: return MiningAbilityWidgetState(true, null)
    return MiningAbilityWidgetState(
        isVisible = true,
        status = MiningAbilityStatus(
            remainingSeconds = match.groups["remaining"]?.value?.toInt(),
        ),
    )
}

private val MINING_ABILITY_USED_PATTERN = Regex("""^You used your .+ Pickaxe Ability!$""")
private val PICKAXE_ABILITY_STATUS_PATTERN =
    Regex("""^[^:]+: (?:(?<remaining>\d+)s|Available)$""")
private val WIDGET_LOAD_GRACE = 3.seconds
private const val PICKAXE_ABILITY_WIDGET_HEADER = "Pickaxe Ability:"
private const val READY_TEXT = "Ready"
private const val READY_TEXT_SPACING = 3
private const val SLIM_BAR_SIZE = 7
private const val LONG_BAR_SIZE = 42
private const val WARNING_COLOR = 0xFFFF5555.toInt()
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val EMPTY_ANIMATION_NANOS = 180_000_000L
