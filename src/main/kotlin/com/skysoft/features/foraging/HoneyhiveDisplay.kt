package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorage
import com.skysoft.data.SkyBlockIsland
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.TextUtilities.formattedText
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayListScroll
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.renderRenderable
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

internal object HoneyhiveDisplay {
    private val config get() = SkysoftConfigGui.config().foraging.honeyhiveHelper
    private var scrollOffset = 0
    private var hoveredHive: ProfileStorage.HoneyhiveData? = null
    private var isDisplayHovered = false
    private var hoveredScreen: AbstractContainerScreen<*>? = null

    fun clear() {
        scrollOffset = 0
        clearInteraction()
    }

    fun register() {
        registerInput()
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "honeyhive_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = setOf(GuiOverlayContextType.WORLD, GuiOverlayContextType.CHAT) + GuiOverlayContextType.INVENTORIES,
                screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
                render = { context, _ -> render(context) },
            ),
            object : HudEditorElement {
                override val id: String = "honeyhive_display"
                override val label: String = "Honeyhive Display"
                override val position get() = config.position
                override val hasEditorBackground: Boolean get() = !config.details.background
                override fun width(): Int = buildRenderable().width
                override fun height(): Int = buildRenderable().height
                override fun isVisible(): Boolean = isDisplayVisible()
                override fun renderEditor(context: GuiGraphicsExtractor) = buildRenderable().render(context)
                override fun openConfig() = SkysoftConfigGui.open("Honeyhive Helper")
            },
        )
    }

    private fun registerInput() {
        InventoryOverlayInput.registerClickHandler("Honeyhive display click", ::isDisplayVisible) { screen, click ->
            if (screen !== hoveredScreen || InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) {
                return@registerClickHandler InputHandlingResult.IGNORED
            }
            val hive = hoveredHive ?: return@registerClickHandler InputHandlingResult.IGNORED
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@registerClickHandler InputHandlingResult.IGNORED
            HoneyhiveHelper.toggleWaypoint(hive)
            InputHandlingResult.CONSUMED
        }
        InventoryOverlayInput.registerScrollHandler("Honeyhive display scroll", ::isDisplayVisible) {
                screen, mouseX, mouseY, amount ->
            if (screen !== hoveredScreen || !isDisplayHovered || amount == 0.0 ||
                InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY)
            ) {
                return@registerScrollHandler InputHandlingResult.IGNORED
            }
            val maximumOffset = (HoneyhiveHelper.currentHives().size - maximumLines()).coerceAtLeast(0)
            if (maximumOffset == 0) return@registerScrollHandler InputHandlingResult.IGNORED
            scrollOffset = OverlayListScroll.nextOffset(scrollOffset, amount, maximumOffset)
            InputHandlingResult.CONSUMED
        }
    }

    private fun clearInteraction() {
        hoveredHive = null
        isDisplayHovered = false
        hoveredScreen = null
    }

    private fun localPoint(view: HoneyhiveRenderable, mouseX: Int, mouseY: Int): Pair<Int, Int> {
        val (normalX, normalY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
        val position = config.position
        val scale = position.effectiveScale
        val x = position.getAbsX0AllowingOverflow((view.width * scale).roundToInt())
        val y = position.getAbsY0AllowingOverflow((view.height * scale).roundToInt())
        return OverlayControlMouse.localCoordinate(normalX, x, scale) to OverlayControlMouse.localCoordinate(normalY, y, scale)
    }

    private fun isDisplayVisible(): Boolean = config.settings.display &&
        !MinecraftClient.isGuiHidden(Minecraft.getInstance()) &&
        (config.settings.showOutsideTorrhus || SkyBlockIsland.TORRHUS_CANYON.isInIsland()) &&
        HoneyhiveHelper.currentHives().isNotEmpty()

    private fun render(context: GuiGraphicsExtractor) {
        clearInteraction()
        if (!isDisplayVisible()) return
        val view = buildRenderable()
        val screen = MinecraftClient.screen() as? AbstractContainerScreen<*>
        val (mouseX, mouseY) = InputUtilities.scaledMousePosition(Minecraft.getInstance())
        val (screenX, screenY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
        val (localX, localY) = localPoint(view, mouseX, mouseY)
        val interactive = screen != null &&
            !InventoryOverlayInput.isPointCovered(screen, screenX.toDouble(), screenY.toDouble())
        isDisplayHovered = interactive && localX in 0 until view.width && localY in 0 until view.height
        val hovered = view.hiveAt(localY).takeIf { isDisplayHovered }
        hoveredHive = hovered
        hoveredScreen = screen
        view.hoveredHive = hovered
        config.position.renderRenderable(context, view)
        if (hovered != null) {
            SkysoftNativeTooltip.setForNextFrame(
                context,
                listOf(
                    "§eHoneyhive §7(${hovered.x}, ${hovered.y}, ${hovered.z})",
                    if (HoneyhiveHelper.hasWaypoint(hovered)) "§eClick §7to clear waypoint" else "§eClick §7to show waypoint",
                    "§7Waypoint appears in Torrhus Canyon.",
                ),
                screenX,
                screenY,
                scrollable = false,
            )
        }
    }

    private fun buildRenderable(): HoneyhiveRenderable {
        val now = System.currentTimeMillis()
        val rows = HoneyhiveHelper.currentHives().mapIndexed { index, hive -> HoneyhiveRow(index + 1, hive, now) }
            .sortedWith(
                compareBy<HoneyhiveRow> { !it.hive.hasKnownStatus() }
                    .thenBy { (it.hive.readyAtMillis - now).coerceAtLeast(0L) }.thenBy { it.number },
            )
        scrollOffset = scrollOffset.coerceIn(0, (rows.size - maximumLines()).coerceAtLeast(0))
        return HoneyhiveRenderable(rows.drop(scrollOffset).take(maximumLines()), scrollOffset, rows.size, config.details.background)
    }

    private fun maximumLines(): Int = config.settings.maximumLines.coerceIn(1, MAXIMUM_LINES)

    private const val MAXIMUM_LINES = 30
}

private class HoneyhiveRenderable(
    private val rows: List<HoneyhiveRow>,
    offset: Int,
    total: Int,
    private val background: Boolean,
) : GuiRenderable {
    private val padding = if (background) OverlayPanelStyle.PADDING else 0
    private val footer = OverlayListScroll.indicator(offset, (total - offset - rows.size).coerceAtLeast(0))
    override val width: Int = maxOf(
        LegacyTextRenderer.width(OverlayTextStyle.title("Honeyhives")),
        rows.maxOfOrNull { it.width } ?: 0,
        LegacyTextRenderer.width(footer),
    ) + padding * 2
    override val height: Int = padding * 2 + OverlayTextStyle.TITLE_HEIGHT + rows.size * OverlayItemRowStyle.HEIGHT +
        if (footer.isEmpty()) 0 else OverlayTextStyle.ROW_HEIGHT
    var hoveredHive: ProfileStorage.HoneyhiveData? = null

    fun hiveAt(y: Int): ProfileStorage.HoneyhiveData? {
        val rowY = y - padding - OverlayTextStyle.TITLE_HEIGHT
        if (rowY < 0) return null
        return rows.getOrNull(rowY / OverlayItemRowStyle.HEIGHT)?.hive
    }

    override fun render(context: GuiGraphicsExtractor) {
        if (background) OverlayPanelStyle.draw(context, 0, 0, width, height)
        LegacyTextRenderer.draw(context, OverlayTextStyle.title("Honeyhives"), padding, padding)
        var y = padding + OverlayTextStyle.TITLE_HEIGHT
        rows.forEach { row ->
            if (hoveredHive === row.hive || HoneyhiveHelper.hasWaypoint(row.hive)) {
                OverlayTextStyle.drawControlHover(context, Rect(padding, y, width - padding * 2, OverlayItemRowStyle.HEIGHT), 1.0)
            }
            hiveIcon.renderAt(context, padding, y)
            val textY = y + OverlayItemRowStyle.TEXT_Y_OFFSET
            LegacyTextRenderer.draw(context, row.name, padding + OverlayItemRowStyle.ICON_TEXT_OFFSET, textY)
            LegacyTextRenderer.draw(context, row.status, width - padding - LegacyTextRenderer.width(row.status), textY)
            y += OverlayItemRowStyle.HEIGHT
        }
        if (footer.isNotEmpty()) {
            LegacyTextRenderer.draw(context, footer, (width - LegacyTextRenderer.width(footer)) / 2, y)
        }
    }

    companion object {
        private val hiveIcon by lazy { ItemIconRenderable(ItemStack(Items.BEE_NEST), OverlayItemRowStyle.ICON_SCALE) }
    }
}

private class HoneyhiveRow(val number: Int, val hive: ProfileStorage.HoneyhiveData, now: Long) {
    val name = "§6Hive $number"
    val status = honeyhiveStatusComponent(hive, now).formattedText()
    val width = OverlayItemRowStyle.ICON_TEXT_OFFSET + LegacyTextRenderer.width(name) +
        OverlayItemRowStyle.VALUE_COLUMN_GAP + LegacyTextRenderer.width(status)
}
