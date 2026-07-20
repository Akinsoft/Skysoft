package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.toPackedArgb
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

object ItemChangeLog {
    private val config get() = SkysoftConfigGui.config().inventory.itemChangeLog
    private val state = ItemChangeLogState()
    private var wasEnabled = false

    fun register() {
        SkyBlockItemChanges.onChange(
            "Item Change Log item changes",
            isActive = { config.enabled },
        ) { change ->
            change.changes.forEach { (itemId, amount) ->
                state.add(itemId, amount, config.settings.maximumLines)
            }
        }
        SkysoftClientEvents.onEndTick(
            "Item Change Log state",
            isActive = { config.enabled || wasEnabled },
        ) {
            if (!config.enabled && wasEnabled) state.clear()
            wasEnabled = config.enabled
        }
        SkysoftClientEvents.onDisconnect("Item Change Log reset", state::clear)
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "item_change_log",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ -> renderHud(context) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "item_change_log"
            override val label: String = "Item Change Log"
            override val position get() = config.position
            private var dragWidth = 0
            private var dragHeight = 0

            override fun width(): Int = currentRenderable()?.width ?: 0
            override fun height(): Int = currentRenderable()?.height ?: 0
            override fun isVisible(): Boolean = config.enabled
            override fun absoluteY(height: Int): Int = itemChangeLogY(
                anchorY = position.getAbsY0AllowingOverflow(0),
                height = height,
                growsDownward = config.settings.invertDirection,
            )
            override fun renderDummy(context: GuiGraphicsExtractor) {
                currentRenderable()?.render(context)
            }
            override fun beginEditorDrag(localX: Int, localY: Int, width: Int, height: Int) {
                dragWidth = width
                dragHeight = height
            }
            override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
                val targetX = absoluteX(dragWidth) + deltaX
                val targetY = absoluteY(dragHeight) + deltaY
                val anchorY = if (config.settings.invertDirection) targetY else targetY + dragHeight
                position.moveToAbsoluteAllowingOverflow(targetX, anchorY, dragWidth, 0)
                return InputHandlingResult.CONSUMED
            }
            override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
                val oldWidth = (width() * position.scale).roundToInt()
                val anchorY = position.getAbsY0AllowingOverflow(0)
                val x = absoluteX(oldWidth)
                position.scale += if (scrollY > 0.0) HUD_SCALE_STEP else -HUD_SCALE_STEP
                val newWidth = (width() * position.scale).roundToInt()
                position.moveToAbsoluteAllowingOverflow(x, anchorY, newWidth, 0)
                return InputHandlingResult.CONSUMED
            }
            override fun openConfig() = SkysoftConfigGui.open("Item Change Log")
        })
    }

    private fun renderHud(context: GuiGraphicsExtractor) {
        if (
            !config.enabled ||
            !HypixelLocationState.inSkyBlock ||
            MinecraftClient.isGuiHidden(Minecraft.getInstance())
        ) return
        val renderable = currentRenderable() ?: return
        renderPositioned(context, renderable)
    }

    private fun renderPositioned(context: GuiGraphicsExtractor, renderable: GuiRenderable) {
        val scale = config.position.effectiveScale
        val scaledWidth = (renderable.width * scale).roundToInt()
        val scaledHeight = (renderable.height * scale).roundToInt()
        val x = config.position.getAbsX0AllowingOverflow(scaledWidth)
        val y = itemChangeLogY(
            anchorY = config.position.getAbsY0AllowingOverflow(0),
            height = scaledHeight,
            growsDownward = config.settings.invertDirection,
        )
        context.withIsolatedPose {
            pose().translate(x.toFloat(), y.toFloat())
            pose().scale(scale, scale)
            renderable.render(context)
        }
    }

    private fun currentRenderable(): GuiRenderable? {
        val entries = state.visibleEntries(
            lifetimeMillis = config.settings.displaySeconds * MILLIS_PER_SECOND,
            maximumLines = config.settings.maximumLines,
            newestFirst = config.settings.invertDirection,
        )
        val rows = entries.mapNotNull(::entryRenderable)
        return rows.takeIf { it.isNotEmpty() }?.let { verticalLayout(it, spacing = ROW_SPACING) }
    }

    private fun entryRenderable(entry: ItemChangeVisual): GuiRenderable? {
        val key = SkyBlockDataRepository.itemKey(entry.itemId)
        val stack = SkyBlockDataRepository.displayStack(key) ?: return null
        val name = SkyBlockDataRepository.entry(key)?.formattedDisplayName ?: return null
        return changeRow(stack, name, entry.amount, entry.opacity)
    }

    private fun changeRow(stack: ItemStack, name: String, amount: Long, opacity: Float): GuiRenderable {
        val colour = if (amount > 0) config.details.gainColor.get() else config.details.lossColor.get()
        val sign = if (amount > 0) "+" else "-"
        val color = colour.toColor().toPackedArgb(opacity.toDouble())
        return horizontalLayout(
            listOf(
                StringRenderable(
                    "$sign ${amount.absoluteValue.addSeparators()}",
                    color = color,
                    verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
                ),
                ItemIconRenderable(
                    stack,
                    scale = ICON_SCALE,
                    alpha = opacity,
                    verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
                ),
                StringRenderable(
                    name,
                    color = color,
                    verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
                ),
            ),
            spacing = COLUMN_SPACING,
            verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
        )
    }
}

internal fun itemChangeLogY(anchorY: Int, height: Int, growsDownward: Boolean): Int =
    if (growsDownward) anchorY else (anchorY - height).coerceAtLeast(0)

internal class ItemChangeLogState(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = linkedMapOf<String, ItemChangeEntry>()

    fun add(itemId: String, change: Int, maximumLines: Int = Int.MAX_VALUE) {
        if (change == 0) return
        val updatedAmount = (entries.remove(itemId)?.amount ?: 0L) + change
        if (updatedAmount != 0L) {
            entries[itemId] = ItemChangeEntry(itemId, updatedAmount, currentTimeMillis())
        }
        while (entries.size > maximumLines.coerceAtLeast(1)) entries.remove(entries.keys.first())
    }

    fun clear() = entries.clear()

    fun visibleEntries(
        lifetimeMillis: Int,
        maximumLines: Int,
        newestFirst: Boolean = false,
        now: Long = currentTimeMillis(),
    ): List<ItemChangeVisual> {
        val lifetime = lifetimeMillis.coerceAtLeast(1).toLong()
        entries.values.removeIf { entry -> now - entry.updatedAtMillis >= lifetime }
        val visibleEntries = entries.values.toList().takeLast(maximumLines.coerceAtLeast(1))
            .let { visible -> if (newestFirst) visible.asReversed() else visible }
        return visibleEntries.map { entry ->
            val age = (now - entry.updatedAtMillis).coerceAtLeast(0L)
            val remaining = (lifetime - age).coerceAtLeast(0L)
            val fadeIn = smoothStep((age.toFloat() / min(FADE_IN_MILLIS, lifetime).coerceAtLeast(1L)).coerceIn(0f, 1f))
            val fadeOut = smoothStep(
                (remaining.toFloat() / min(FADE_OUT_MILLIS, lifetime).coerceAtLeast(1L)).coerceIn(0f, 1f),
            )
            ItemChangeVisual(entry.itemId, entry.amount, min(fadeIn, fadeOut))
        }
    }

    private data class ItemChangeEntry(
        val itemId: String,
        val amount: Long,
        val updatedAtMillis: Long,
    )
}

internal data class ItemChangeVisual(
    val itemId: String,
    val amount: Long,
    val opacity: Float,
)

private fun smoothStep(value: Float): Float = value * value * (SMOOTH_STEP_END - SMOOTH_STEP_SCALE * value)

private const val MILLIS_PER_SECOND = 1_000
private const val FADE_IN_MILLIS = 200L
private const val FADE_OUT_MILLIS = 500L
private const val ROW_SPACING = 1
private const val COLUMN_SPACING = 2
private const val ICON_SCALE = 0.75
private const val HUD_SCALE_STEP = 0.1f
private const val SMOOTH_STEP_END = 3f
private const val SMOOTH_STEP_SCALE = 2f
