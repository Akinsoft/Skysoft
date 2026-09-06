package com.skysoft.features.inventory.crafting

import com.skysoft.config.CRAFTING_HELPER_MAXIMUM_LINES
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemChanges
import com.skysoft.data.skyblock.SkyBlockSupercrafts
import com.skysoft.features.inventory.InventoryTrackerFrame
import com.skysoft.features.inventory.InventoryTrackerLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.OverlayControlArea
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.gui.OverlayItemRowStyle
import com.skysoft.utils.gui.OverlayTextStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.LegacyTextRenderer
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object CraftingHelper {
    fun register() {
        ProfileStorageApi.registerConsumer("Crafting Helper") { craftingHelperConfig.enabled }
        SkyBlockDataRepository.Demand.register("Crafting Helper") { craftingHelperConfig.enabled }
        SkyBlockSupercrafts.onCraft(
            "Crafting Helper Supercrafts",
            { craftingHelperConfig.enabled },
            CraftingHelperOptimisticInventory::record,
        )
        SkyBlockItemChanges.onChange(
            "Crafting Helper optimistic changes",
            { craftingHelperConfig.enabled },
            CraftingHelperOptimisticInventory::reconcile,
        )
        SkyBlockProfileApi.onProfileChange(
            "Crafting Helper optimistic profile reset",
            { craftingHelperConfig.enabled || CraftingHelperOptimisticInventory.hasChanges },
        ) { CraftingHelperOptimisticInventory.clear() }
        SkysoftClientEvents.onDisconnect("Crafting Helper reset") {
            craftingHelperScrollOffset = 0
            craftingHelperItemPanel.clear()
            clearCraftingHelperInteraction()
            CraftingHelperOptimisticInventory.clear()
        }
        registerCraftingHelperInput()
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "crafting_helper",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                screenForegroundContexts = GuiOverlayContextType.INVENTORIES,
                render = { context, _ -> renderCraftingHelper(context) },
            ),
            object : HudEditorElement {
                override val id: String = "crafting_helper"
                override val label: String = "Crafting Helper"
                override val position get() = craftingHelperConfig.position
                override val hasEditorBackground: Boolean get() = !craftingHelperConfig.details.showBackground
                override fun width(): Int = buildCraftingHelperRenderable(inventoryOpen = false).width
                override fun height(): Int = buildCraftingHelperRenderable(inventoryOpen = false).height
                override fun isVisible(): Boolean = isCraftingHelperVisible()
                override fun absoluteX(width: Int): Int = position.getAbsX0AllowingOverflow(0)
                override fun absoluteY(height: Int): Int = position.getAbsY0AllowingOverflow(0)
                override fun renderEditor(context: GuiGraphicsExtractor) =
                    buildCraftingHelperRenderable(inventoryOpen = false).render(context)
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
                    position.scale += if (scrollY > 0.0) EDITOR_SCALE_STEP else -EDITOR_SCALE_STEP
                    return InputHandlingResult.CONSUMED
                }
                override fun openConfig() = SkysoftConfigGui.open("Crafting Helper")
            },
        )
    }
}

internal fun isCraftingHelperVisible(): Boolean {
    if (!craftingHelperConfig.enabled || !HypixelLocationState.inSkyBlock) return false
    val minecraft = Minecraft.getInstance()
    if (MinecraftClient.isGuiHidden(minecraft)) return false
    val inventoryOpen = MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
    return craftingHelperConfig.targets.isNotEmpty() ||
        inventoryOpen && !craftingHelperConfig.settings.hideWhenEmpty
}

private fun renderCraftingHelper(context: GuiGraphicsExtractor) {
    if (!isCraftingHelperVisible()) {
        craftingHelperItemPanel.clear()
        clearCraftingHelperInteraction()
        return
    }
    val minecraft = Minecraft.getInstance()
    val inventoryScreen = MinecraftClient.screen(minecraft) as? AbstractContainerScreen<*>
    val inventoryOpen = inventoryScreen != null
    if (!inventoryOpen) craftingHelperItemPanel.clear()
    val renderable = buildCraftingHelperRenderable(inventoryOpen)
    if (renderable.width <= 0 || renderable.height <= 0) {
        clearCraftingHelperInteraction()
        return
    }
    val frame = InventoryTrackerFrame(craftingHelperConfig.position, renderable.width, renderable.height)
    craftingHelperHoveredControl = frame.render(context) { mouseX, mouseY, placePanelRight ->
        val trackerControl = renderable.renderInteractive(context, mouseX, mouseY)
        craftingHelperItemPanel.render(
            context,
            renderable.width,
            placePanelRight,
            mouseX ?: Int.MIN_VALUE,
            mouseY ?: Int.MIN_VALUE,
        ) ?: trackerControl
    }
    craftingHelperHovered = frame.isHovered
    if (frame.interactive) craftingHelperHoveredControl?.action?.let { action ->
        context.nextStratum()
        when (action) {
            is CraftingHelperControl.Line -> renderCraftingHelperLineTooltip(
                context,
                action.line,
                frame.screenMouseX,
                frame.screenMouseY,
            )
            CraftingHelperControl.More -> SkysoftNativeTooltip.setForNextFrame(
                context,
                listOf("§7Add crafting targets."),
                frame.screenMouseX,
                frame.screenMouseY,
                scrollable = false,
            )
            else -> Unit
        }
    }
}

private fun renderCraftingHelperLineTooltip(
    context: GuiGraphicsExtractor,
    line: CraftingHelperLine,
    mouseX: Int,
    mouseY: Int,
) {
    val action = "Manage".takeIf { line.isTarget }
    val actionLines = craftingHelperLineActionLines(line)
    SkysoftNativeTooltip.setItemActionForNextFrame(
        context,
        line.stack ?: ItemStack.EMPTY,
        action,
        line.formattedName,
        mouseX,
        mouseY,
        actionLines,
    )
}

private fun craftingHelperLineActionLines(line: CraftingHelperLine): List<String> = buildList {
    if (!line.isTarget) {
        val copiedAmount = line.supercraft?.crafts ?: line.missing
        val copiesAmount = craftingHelperConfig.settings.copyAmount && copiedAmount > 0L
        val destination = line.supercraft?.let { "${it.itemName} Supercraft" } ?: line.acquisition?.displayName
        if (destination != null) {
            add("§eLeft-click §7to open $destination" + if (copiesAmount) " and copy amount" else "")
        } else if (copiesAmount) {
            add("§eLeft-click §7to copy missing amount")
        }
    }
    if (line.key != null && SkysoftConfigGui.config().inventory.itemList.enabled) {
        add("§eRight-click §7to open Item List Info")
    }
}

internal fun buildCraftingHelperRenderable(inventoryOpen: Boolean): CraftingHelperRenderable {
    val lines = craftingHelperLines()
    val maximumLines = craftingHelperConfig.settings.maximumLines.coerceIn(1, CRAFTING_HELPER_MAXIMUM_LINES)
    val maximumOffset = (lines.size - maximumLines).coerceAtLeast(0)
    craftingHelperScrollOffset = craftingHelperScrollOffset.coerceIn(0, maximumOffset)
    val displayed = lines.drop(craftingHelperScrollOffset).take(maximumLines)
    return CraftingHelperRenderable(
        lines = displayed,
        hiddenAbove = craftingHelperScrollOffset,
        hiddenBelow = (lines.size - craftingHelperScrollOffset - displayed.size).coerceAtLeast(0),
        targetsEmpty = craftingHelperConfig.targets.isEmpty(),
        showTitle = craftingHelperConfig.details.showTitle,
        showIcons = craftingHelperConfig.details.showItemIcons,
        background = craftingHelperConfig.details.showBackground,
        inventoryOpen = inventoryOpen,
    )
}

internal class CraftingHelperRenderable(
    lines: List<CraftingHelperLine>,
    private val hiddenAbove: Int,
    private val hiddenBelow: Int,
    targetsEmpty: Boolean,
    showTitle: Boolean,
    showIcons: Boolean,
    background: Boolean,
    inventoryOpen: Boolean,
) : GuiRenderable {
    private val rows = lines.map { line -> CraftingHelperRow(line, showIcons) }
    private val emptyText = if (targetsEmpty) "§7No crafting targets." else "§7Loading recipe data..."
    private val indicatorText = when {
        hiddenAbove <= 0 && hiddenBelow <= 0 -> ""
        else -> buildList {
            if (hiddenAbove > 0) add("$hiddenAbove above")
            if (hiddenBelow > 0) add("$hiddenBelow more")
        }.joinToString(", ", prefix = "§7", postfix = "...")
    }
    private val layout = InventoryTrackerLayout(
        title = "Crafting Helper",
        emptyText = emptyText,
        indicatorText = indicatorText,
        rowWidths = rows.map(CraftingHelperRow::width),
        minimumWidth = MINIMUM_WIDTH,
        showTitle = showTitle,
        background = background,
        inventoryOpen = inventoryOpen,
    )
    override val width: Int get() = layout.width
    override val height: Int get() = layout.height

    override fun render(context: GuiGraphicsExtractor) {
        renderInteractive(context, null, null)
    }

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<CraftingHelperControl>? = layout.render(
        context, mouseX, mouseY, CraftingHelperControl.More,
    ) { index, left, right, y ->
        rows[index].renderInteractive(context, left, right, y, mouseX, mouseY)
    }
}

private data class CraftingHelperRow(
    val line: CraftingHelperLine,
    val showIcon: Boolean,
) {
    private val prefixWidth = LegacyTextRenderer.width(line.prefix)
    private val iconWidth = if (showIcon) OverlayItemRowStyle.ICON_TEXT_OFFSET else 0
    private val text = buildString {
        if (line.owned == null) {
            append("§6${line.required.addSeparators()} ")
        } else {
            append(if (line.owned >= line.required) "§a" else "§c")
            append(line.owned.addSeparators())
            append("§7/§e${line.required.addSeparators()} ")
        }
        append(line.formattedName)
    }
    val width: Int = prefixWidth + iconWidth + LegacyTextRenderer.width(text)

    fun renderInteractive(
        context: GuiGraphicsExtractor,
        left: Int,
        right: Int,
        y: Int,
        mouseX: Int?,
        mouseY: Int?,
    ): OverlayControlArea<CraftingHelperControl>? {
        val bounds = Rect(left, y, (right - left).coerceAtLeast(1), OverlayItemRowStyle.HEIGHT)
        val hovered = mouseX != null && mouseY != null && bounds.contains(mouseX, mouseY)
        if (hovered) OverlayTextStyle.drawControlHover(context, bounds, 1.0)
        LegacyTextRenderer.draw(context, "§8${line.prefix}", left, y + OverlayItemRowStyle.TEXT_Y_OFFSET)
        if (showIcon) {
            line.stack?.let { ItemIconRenderable(it, OverlayItemRowStyle.ICON_SCALE).renderAt(context, left + prefixWidth, y) }
        }
        LegacyTextRenderer.draw(
            context,
            text,
            left + prefixWidth + iconWidth,
            y + OverlayItemRowStyle.TEXT_Y_OFFSET,
        )
        return OverlayControlArea<CraftingHelperControl>(CraftingHelperControl.Line(line), bounds).takeIf { hovered }
    }
}

private const val MINIMUM_WIDTH = 160
private const val EDITOR_SCALE_STEP = 0.1f
