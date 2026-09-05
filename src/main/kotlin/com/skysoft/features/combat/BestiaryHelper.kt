package com.skysoft.features.combat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockBestiaryFamilies
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.SkyBlockOpenInventoryApi
import com.skysoft.data.skyblock.SkyBlockOpenInventorySnapshot
import com.skysoft.features.inventory.InventoryOverlayInput
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.OverlayControlMouse
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.TextUtilities.truncateLegacyText
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.input.InputUtilities
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import com.skysoft.utils.render.EntityLabelRenderer
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.withIsolatedPose
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object BestiaryHelper {
    private val config get() = SkysoftConfigGui.config().combat.bestiaryHelper
    private val highlightedEntities = EntityHighlightTracker<Entity>(this)
    private val resetTransition = PanelFadeTransition()
    private val knownFamilies = mutableMapOf<String, BestiaryFamily>()
    private var openBestiary: OpenBestiary? = null
    private var scrollOffset = 0
    private var hoveredControl: OverlayControlArea<BestiaryControl>? = null
    private var displayHovered = false
    private var ticks = 0

    fun register() {
        SkyBlockDataRepository.Demand.register("Bestiary Helper") { config.enabled }
        SkyBlockOpenInventoryApi.onChange(
            "Bestiary Helper inventory",
            isActive = { config.enabled && HypixelLocationState.inSkyBlock },
            listener = ::updateOpenBestiary,
        )
        SkysoftClientEvents.onEndTick(
            "Bestiary Helper highlighting",
            isActive = { config.enabled || highlightedEntities.isNotEmpty() },
        ) { updateHighlights() }
        SkysoftClientEvents.onDisconnect("Bestiary Helper reset") {
            clearDisplay()
            clearHighlights()
            knownFamilies.clear()
        }
        WorldRenderDispatcher.registerHandler(
            "Bestiary Helper target labels",
            isActive = {
                config.enabled && HypixelLocationState.inSkyBlock && config.selectedMobs.isNotEmpty()
            },
            handler = ::renderWorld,
        )
        registerInput()
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "bestiary_helper",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.INVENTORIES,
                screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
                render = { context, _ -> render(context) },
            ),
            object : HudEditorElement {
                override val id: String = "bestiary_helper"
                override val label: String = "Bestiary Helper"
                override val position get() = config.position
                override val hasEditorBackground: Boolean = false
                override fun width(): Int = openBestiary?.let(::buildRenderable)?.width ?: 0
                override fun height(): Int = openBestiary?.let(::buildRenderable)?.height ?: 0
                override fun isVisible(): Boolean = BestiaryHelper.isVisible()
                override fun absoluteX(width: Int): Int = position.getAbsX0AllowingOverflow(0)
                override fun absoluteY(height: Int): Int = position.getAbsY0AllowingOverflow(0)
                override fun renderEditor(context: GuiGraphicsExtractor) =
                    openBestiary?.let(::buildRenderable)?.render(context) ?: Unit
                override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
                    position.moveToAbsoluteAllowingOverflow(
                        position.getAbsX0AllowingOverflow(0) + deltaX,
                        position.getAbsY0AllowingOverflow(0) + deltaY,
                        0,
                        0,
                    )
                    return InputHandlingResult.CONSUMED
                }
                override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
                    position.scale += if (scrollY > 0.0) HUD_SCALE_STEP else -HUD_SCALE_STEP
                    return InputHandlingResult.CONSUMED
                }
                override fun openConfig() = SkysoftConfigGui.open("Bestiary Helper")
            },
        )
    }

    private fun registerInput() {
        InventoryOverlayInput.registerClickHandler(
            "Bestiary Helper mouse click",
            isActive = { config.enabled },
        ) { screen, click ->
            if (shouldAllowClick(screen, click)) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
        }
        InventoryOverlayInput.registerScrollHandler(
            "Bestiary Helper mouse scroll",
            isActive = { config.enabled },
        ) { screen, mouseX, mouseY, verticalAmount ->
            val allowed = InventoryOverlayInput.isPointCovered(screen, mouseX, mouseY) ||
                !wasScrollHandled(verticalAmount)
            if (allowed) InputHandlingResult.IGNORED else InputHandlingResult.CONSUMED
        }
    }

    private fun shouldAllowClick(screen: AbstractContainerScreen<*>, click: MouseButtonEvent): Boolean {
        if (!isVisible() || InventoryOverlayInput.isPointCovered(screen, click.x(), click.y())) return true
        val action = hoveredControl?.action ?: return true
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        when (action) {
            is BestiaryControl.Family -> toggle(action.family.name)
            BestiaryControl.Reset -> resetTransition.show()
            BestiaryControl.CancelReset -> resetTransition.hide()
            BestiaryControl.ConfirmReset -> {
                config.selectedMobs.clear()
                scrollOffset = 0
                SkysoftConfigGui.config().saveNow()
                resetTransition.hide()
            }
        }
        SoundUtilities.playClickSound()
        return false
    }

    private fun wasScrollHandled(verticalAmount: Double): Boolean {
        if (!isVisible() || !displayHovered || verticalAmount == 0.0) return false
        val maximumOffset = maximumScrollOffset(openBestiary?.let(::familiesForDisplay)?.size ?: 0)
        if (maximumOffset == 0) return false
        scrollOffset = (scrollOffset + if (verticalAmount < 0.0) 1 else -1).coerceIn(0, maximumOffset)
        return true
    }

    private fun toggle(name: String) {
        val selected = config.selectedMobs.firstOrNull { it.equals(name, ignoreCase = true) }
        if (selected == null) {
            config.selectedMobs += name
        } else {
            config.selectedMobs.remove(selected)
        }
        SkysoftConfigGui.config().saveNow()
    }

    private fun updateOpenBestiary(snapshot: SkyBlockOpenInventorySnapshot?) {
        if (snapshot == null && MinecraftClient.screen() is SkysoftHudEditor.EditorScreen) return
        if (snapshot == null || !isBestiaryMenu(snapshot)) {
            clearDisplay()
            return
        }
        val previous = openBestiary
        if (previous?.containerId != snapshot.containerId || previous.title != snapshot.title) {
            scrollOffset = 0
            resetTransition.reset()
        }
        val families = snapshot.items.toSortedMap().values.mapNotNull(::parseFamily)
        families.forEach { family -> knownFamilies[familyKey(family.name)] = family }
        val bestiary = OpenBestiary(snapshot.title, snapshot.containerId, families)
        openBestiary = bestiary
        scrollOffset = scrollOffset.coerceIn(0, maximumScrollOffset(familiesForDisplay(bestiary).size))
    }

    private fun updateHighlights() {
        if (!config.enabled || !HypixelLocationState.inSkyBlock || config.selectedMobs.isEmpty()) {
            clearHighlights()
            return
        }
        if (++ticks % HIGHLIGHT_SCAN_INTERVAL_TICKS != 0) return
        val highlights = SkyBlockMobEntityMatcher.visibleSignals(SkyBlockBestiaryFamilies.mobNames(config.selectedMobs))
            .flatMap { signal ->
                val parts = signal.nameplate?.let { nameplate ->
                    SegmentedMobHighlights.parts(nameplate, SkyBlockMobEntityMatcher.allEntities())
                }.orEmpty()
                parts.ifEmpty { listOfNotNull(signal.entity?.let { SkyBlockMobHighlight(it, it) }) }
            }
        highlightedEntities.replaceWith(highlights.mapTo(mutableSetOf()) { it.entity })
        val color = config.details.highlightColor.get().toColor()
        highlights.forEach { highlight ->
            EntityHighlightRenderer.setEntityColor(
                highlight.entity,
                color,
                source = this,
                visibilityEntity = highlight.visibilityEntity,
            ) {
                config.enabled && highlight.entity in highlightedEntities
            }
        }
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val lines = buildList {
            config.details.targetText.takeIf(String::isNotBlank)?.let { text -> add(Component.literal(text)) }
            add(Component.literal(TARGET_MARKER))
        }
        val style = WorldLabelStyle(
            textColor = config.details.textColor.get().toColor().rgb,
            displayMode = Font.DisplayMode.NORMAL,
        )
        SkyBlockMobEntityMatcher.visibleSignals(SkyBlockBestiaryFamilies.mobNames(config.selectedMobs)).forEach { signal ->
            val anchor = signal.nameplate ?: signal.entity ?: return@forEach
            EntityLabelRenderer.drawAboveNameTag(context, anchor, lines, style)
        }
    }

    private fun clearHighlights() {
        highlightedEntities.clear()
        ticks = 0
    }

    private fun clearDisplay() {
        openBestiary = null
        scrollOffset = 0
        hoveredControl = null
        displayHovered = false
        resetTransition.reset()
    }

    private fun render(context: GuiGraphicsExtractor) {
        if (!isVisible()) {
            hoveredControl = null
            displayHovered = false
            return
        }
        val bestiary = openBestiary ?: return
        val renderable = buildRenderable(bestiary)
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*> ?: return
        val (mouseX, mouseY) = InputUtilities.scaledMousePosition(minecraft)
        val (normalMouseX, normalMouseY) = OverlayControlMouse.normalPoint(mouseX, mouseY)
        val (screenMouseX, screenMouseY) = OverlayControlMouse.screenPoint(mouseX, mouseY)
        val interactive = !InventoryOverlayInput.isPointCovered(
            screen,
            screenMouseX.toDouble(),
            screenMouseY.toDouble(),
        )
        val scale = config.position.effectiveScale
        val x = config.position.getAbsX0AllowingOverflow(0)
        val y = config.position.getAbsY0AllowingOverflow(0)
        val localMouseX = floor((normalMouseX - x) / scale).toInt()
        val localMouseY = floor((normalMouseY - y) / scale).toInt()

        context.nextStratum()
        val localControl = context.withIsolatedPose {
            pose().translate(x.toFloat(), y.toFloat())
            pose().scale(scale, scale)
            renderable.renderInteractive(
                context,
                localMouseX.takeIf { interactive },
                localMouseY.takeIf { interactive },
            )
        }
        displayHovered = interactive && localMouseX in 0 until renderable.width && localMouseY in 0 until renderable.height
        hoveredControl = localControl?.let { control ->
            OverlayControlArea(
                action = control.action,
                bounds = Rect(
                    x = x + (control.bounds.x * scale).roundToInt(),
                    y = y + (control.bounds.y * scale).roundToInt(),
                    width = (control.bounds.width * scale).roundToInt().coerceAtLeast(1),
                    height = (control.bounds.height * scale).roundToInt().coerceAtLeast(1),
                ),
                tooltipLines = control.tooltipLines,
            )
        }
        if (interactive) hoveredControl?.let { control ->
            context.nextStratum()
            val family = (control.action as? BestiaryControl.Family)?.family
            if (family?.menuStack != null) {
                val selected = isSelected(family.name)
                SkysoftNativeTooltip.setItemActionForNextFrame(
                    context,
                    family.menuStack,
                    if (selected) "§cStop highlighting" else "§eHighlight",
                    "§f${family.name}",
                    screenMouseX,
                    screenMouseY,
                )
            } else {
                SkysoftNativeTooltip.setForNextFrame(
                    context,
                    control.tooltipLines,
                    screenMouseX,
                    screenMouseY,
                    scrollable = false,
                )
            }
        }
    }

    private fun isVisible(): Boolean {
        if (!config.enabled || !HypixelLocationState.inSkyBlock) return false
        val minecraft = Minecraft.getInstance()
        if (MinecraftClient.isGuiHidden(minecraft)) return false
        val screen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*> ?: return false
        val bestiary = openBestiary ?: return false
        return screen.menu.containerId == bestiary.containerId && screen.title.cleanSkyBlockText() == bestiary.title
    }

    private fun buildRenderable(bestiary: OpenBestiary): BestiaryRenderable {
        val families = familiesForDisplay(bestiary)
        val maximumOffset = maximumScrollOffset(families.size)
        scrollOffset = scrollOffset.coerceIn(0, maximumOffset)
        val displayed = families.drop(scrollOffset).take(MAXIMUM_DISPLAY_FAMILIES)
        return BestiaryRenderable(
            families = displayed,
            selected = config.selectedMobs,
            hiddenAbove = scrollOffset,
            hiddenBelow = (families.size - scrollOffset - displayed.size).coerceAtLeast(0),
            resetOpacity = resetTransition.opacity(),
            resetPending = resetTransition.isVisible,
            resetInteractive = resetTransition.isInteractive,
        )
    }

    private fun familiesForDisplay(bestiary: OpenBestiary): List<BestiaryFamily> {
        val selectedNames = config.selectedMobs.mapTo(mutableSetOf(), ::familyKey)
        return buildList {
            config.selectedMobs.forEach { name ->
                val key = familyKey(name)
                add(knownFamilies[key] ?: BestiaryFamily(name, null))
            }
            bestiary.families.filterTo(this) { family -> familyKey(family.name) !in selectedNames }
        }
    }

    private fun isSelected(name: String): Boolean =
        config.selectedMobs.any { selected -> selected.equals(name, ignoreCase = true) }

    private fun maximumScrollOffset(itemCount: Int): Int =
        (itemCount - MAXIMUM_DISPLAY_FAMILIES).coerceAtLeast(0)
}

private fun isBestiaryMenu(snapshot: SkyBlockOpenInventorySnapshot): Boolean =
    snapshot.title == BESTIARY_TITLE || ARROW in snapshot.title && snapshot.items.values.any { stack ->
        stack.loreLines().any { line -> line.cleanSkyBlockText().startsWith(FAMILIES_FOUND_PREFIX) }
    }

private fun parseFamily(stack: ItemStack): BestiaryFamily? {
    val lore = stack.loreLines().map { line -> line.cleanSkyBlockText() }
    if (lore.none { line ->
            line.startsWith(MOB_TYPE_PREFIX) || line.startsWith(MOB_TYPES_PREFIX) || line == LOCKED_FAMILY_LINE
        }
    ) return null
    val name = normalizeSkyBlockMobName(stack.hoverName.cleanSkyBlockText()).takeIf(String::isNotEmpty) ?: return null
    return BestiaryFamily(name, stack)
}

private class BestiaryRenderable(
    families: List<BestiaryFamily>,
    selected: Collection<String>,
    hiddenAbove: Int,
    hiddenBelow: Int,
    private val resetOpacity: Double,
    private val resetPending: Boolean,
    private val resetInteractive: Boolean,
) : GuiRenderable {
    private val selectedNames = selected.mapTo(mutableSetOf(), ::familyKey)
    private val rows = families.map { family -> BestiaryRow(family, familyKey(family.name) in selectedNames) }
    private val indicatorText = buildList {
        if (hiddenAbove > 0) add("$hiddenAbove above")
        if (hiddenBelow > 0) add("$hiddenBelow more")
    }.joinToString(", ", prefix = "§7", postfix = if (hiddenAbove > 0 || hiddenBelow > 0) "..." else "")
    private val title = OverlayTextStyle.title("Bestiary Helper")
    private val emptyText = "§7Select a Bestiary mob list."
    private val resetText = if (selected.isEmpty()) "§8[Reset]" else "§c[Reset]"
    private val confirmationLeft = "§c[Cancel]"
    private val confirmationRight = "§a[Confirm]"
    private val contentWidth = maxOf(
        MINIMUM_WIDTH,
        LegacyTextRenderer.width(title),
        rows.maxOfOrNull(BestiaryRow::width) ?: LegacyTextRenderer.width(emptyText),
        LegacyTextRenderer.width(indicatorText),
        LegacyTextRenderer.width(confirmationLeft) + CONTROL_GAP + LegacyTextRenderer.width(confirmationRight),
    )

    override val width: Int = contentWidth + OverlayPanelStyle.PADDING * 2
    override val height: Int = OverlayPanelStyle.PADDING * 2 + OverlayTextStyle.TITLE_HEIGHT +
        (if (rows.isEmpty()) OverlayTextStyle.ROW_HEIGHT else rows.size * OverlayItemRowStyle.HEIGHT) +
        (if (indicatorText.isEmpty()) 0 else OverlayTextStyle.ROW_HEIGHT) + CONTROL_ROW_HEIGHT

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(context: GuiGraphicsExtractor, mouseX: Int?, mouseY: Int?): LocalBestiaryControl? {
        OverlayPanelStyle.draw(context, 0, 0, width, height)
        var y = OverlayPanelStyle.PADDING
        LegacyTextRenderer.draw(context, title, OverlayPanelStyle.PADDING, y)
        y += OverlayTextStyle.TITLE_HEIGHT
        var hovered: LocalBestiaryControl? = null
        if (rows.isEmpty()) {
            LegacyTextRenderer.draw(context, emptyText, OverlayPanelStyle.PADDING, y)
            y += OverlayTextStyle.ROW_HEIGHT
        } else {
            rows.forEach { row ->
                hovered = row.render(
                    context,
                    OverlayPanelStyle.PADDING,
                    width - OverlayPanelStyle.PADDING,
                    y,
                    mouseX,
                    mouseY,
                ) ?: hovered
                y += OverlayItemRowStyle.HEIGHT
            }
        }
        if (indicatorText.isNotEmpty()) {
            LegacyTextRenderer.draw(context, indicatorText, (width - LegacyTextRenderer.width(indicatorText)) / 2, y)
            y += OverlayTextStyle.ROW_HEIGHT
        }
        return renderReset(context, y, mouseX, mouseY) ?: hovered
    }

    private fun renderReset(
        context: GuiGraphicsExtractor,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalBestiaryControl? {
        val resetBounds = Rect(OverlayPanelStyle.PADDING, y, LegacyTextRenderer.width(resetText), CONTROL_ROW_HEIGHT)
        val resetHovered = !resetPending && selectedNames.isNotEmpty() && mouseX != null && mouseY != null &&
            resetBounds.contains(mouseX, mouseY)
        if (resetHovered) OverlayTextStyle.drawControlHover(context, resetBounds, 1.0 - resetOpacity)
        LegacyTextRenderer.draw(
            context,
            resetText,
            resetBounds.x,
            y + CONTROL_TEXT_Y_OFFSET,
            defaultColor = TEXT_COLOR.withScaledAlpha(1.0 - resetOpacity),
        )

        val cancelBounds = Rect(
            OverlayPanelStyle.PADDING,
            y,
            LegacyTextRenderer.width(confirmationLeft),
            CONTROL_ROW_HEIGHT,
        )
        val confirmWidth = LegacyTextRenderer.width(confirmationRight)
        val confirmBounds = Rect(width - OverlayPanelStyle.PADDING - confirmWidth, y, confirmWidth, CONTROL_ROW_HEIGHT)
        val cancelHovered = resetInteractive && mouseX != null && mouseY != null && cancelBounds.contains(mouseX, mouseY)
        val confirmHovered = resetInteractive && mouseX != null && mouseY != null && confirmBounds.contains(mouseX, mouseY)
        if (cancelHovered) OverlayTextStyle.drawControlHover(context, cancelBounds, resetOpacity)
        if (confirmHovered) OverlayTextStyle.drawControlHover(context, confirmBounds, resetOpacity)
        val confirmationColor = TEXT_COLOR.withScaledAlpha(resetOpacity)
        LegacyTextRenderer.draw(
            context,
            confirmationLeft,
            cancelBounds.x,
            y + CONTROL_TEXT_Y_OFFSET,
            defaultColor = confirmationColor,
        )
        LegacyTextRenderer.draw(
            context,
            confirmationRight,
            confirmBounds.x,
            y + CONTROL_TEXT_Y_OFFSET,
            defaultColor = confirmationColor,
        )

        return when {
            confirmHovered -> LocalBestiaryControl(
                BestiaryControl.ConfirmReset,
                confirmBounds,
                listOf("§7Clear every selected Bestiary mob."),
            )
            cancelHovered -> LocalBestiaryControl(BestiaryControl.CancelReset, cancelBounds, emptyList())
            resetHovered -> LocalBestiaryControl(
                BestiaryControl.Reset,
                resetBounds,
                listOf("§7Clear every selected Bestiary mob."),
            )
            else -> null
        }
    }
}

private data class BestiaryRow(
    val family: BestiaryFamily,
    val selected: Boolean,
) {
    private val name = family.name.truncateLegacyText(MAXIMUM_FAMILY_NAME_LENGTH)
    private val status = if (selected) "§a[On]" else "§8[Off]"
    val width: Int = OverlayItemRowStyle.ICON_TEXT_OFFSET + LegacyTextRenderer.width(name) +
        OverlayItemRowStyle.VALUE_COLUMN_GAP + LegacyTextRenderer.width(status)

    fun render(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): LocalBestiaryControl? {
        val bounds = Rect(left, y, right - left, OverlayItemRowStyle.HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        SkyBlockBestiaryFamilies.icon(family.name)?.let { stack ->
            ItemIconRenderable(stack, OverlayItemRowStyle.ICON_SCALE).renderAt(context, left, y)
        }
        LegacyTextRenderer.draw(
            context,
            if (selected) "§a$name" else "§f$name",
            left + OverlayItemRowStyle.ICON_TEXT_OFFSET,
            y + OverlayItemRowStyle.TEXT_Y_OFFSET,
        )
        LegacyTextRenderer.draw(
            context,
            status,
            right - LegacyTextRenderer.width(status),
            y + OverlayItemRowStyle.TEXT_Y_OFFSET,
        )
        return LocalBestiaryControl(
            BestiaryControl.Family(family),
            bounds,
            listOf("§f${family.name}", if (selected) "§cStop highlighting" else "§eHighlight"),
        ).takeIf { hovered }
    }
}

private data class OpenBestiary(
    val title: String,
    val containerId: Int,
    val families: List<BestiaryFamily>,
)

private data class BestiaryFamily(
    val name: String,
    val menuStack: ItemStack?,
)

private sealed interface BestiaryControl {
    data class Family(val family: BestiaryFamily) : BestiaryControl
    data object Reset : BestiaryControl
    data object CancelReset : BestiaryControl
    data object ConfirmReset : BestiaryControl
}

private data class LocalBestiaryControl(
    val action: BestiaryControl,
    val bounds: Rect,
    val tooltipLines: List<String>,
)

private const val BESTIARY_TITLE = "Bestiary"
private const val ARROW = "➜"
private const val FAMILIES_FOUND_PREFIX = "Families Found:"
private const val MOB_TYPE_PREFIX = "Mob Type:"
private const val MOB_TYPES_PREFIX = "Mob Types:"
private const val LOCKED_FAMILY_LINE = "You haven't unlocked this Family yet!"
private const val MAXIMUM_DISPLAY_FAMILIES = 15
private const val MAXIMUM_FAMILY_NAME_LENGTH = 28
private const val MINIMUM_WIDTH = 165
private const val CONTROL_ROW_HEIGHT = 13
private const val CONTROL_TEXT_Y_OFFSET = 1
private const val CONTROL_GAP = 8
private const val HUD_SCALE_STEP = 0.1f
private const val HIGHLIGHT_SCAN_INTERVAL_TICKS = 4
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val TARGET_MARKER = "▾"

private fun familyKey(name: String): String = name.lowercase(Locale.ROOT)
