package com.skysoft.features.inventory

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContext
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.SkysoftHudEditor
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.gui.fillOverlayBackground
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.renderRenderable
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

object InventoryHud {
    private val config get() = SkysoftConfigGui.config().gui.inventoryHud

    fun register() {
        InventoryEquipmentCache.registerConsumer("Inventory HUD") {
            config.enabled && config.settings.equipment
        }
        BottomHudLayout.registerReservation("inventory_hud", ::bottomReservation)
        registerVanillaHudElements()
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "inventory_hud",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                screenForegroundContexts = GuiOverlayContextType.entries.filter { it != GuiOverlayContextType.WORLD }.toSet(),
                visible = ::isVisible,
                render = { context, _ -> config.position.renderRenderable(context, currentRenderable()) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "inventory_hud"
            override val label: String = "Inventory HUD"
            override val position get() = config.position
            override val hasEditorBackground: Boolean
                get() = !config.details.background && !config.details.outline && !config.details.slotBackgrounds
            override fun width(): Int = currentLayout().width
            override fun height(): Int = currentLayout().height
            override fun isVisible(): Boolean = config.enabled
            override fun renderDummy(context: GuiGraphicsExtractor) = currentRenderable().render(context)
            override fun openConfig() = SkysoftConfigGui.open("Inventory HUD")
        })
    }

    private fun registerVanillaHudElements() {
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR) { vanilla ->
            HudElement { context, tick ->
                if (!isLiveVisible()) vanilla.extractRenderState(context, tick)
            }
        }
        listOf(
            VanillaHudElements.ARMOR_BAR,
            VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR,
            VanillaHudElements.AIR_BAR,
            VanillaHudElements.MOUNT_HEALTH,
            VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL,
            VanillaHudElements.HELD_ITEM_TOOLTIP,
            VanillaHudElements.OVERLAY_MESSAGE,
        ).forEach(::shiftVanillaElement)
    }

    private fun shiftVanillaElement(id: Identifier) {
        HudElementRegistry.replaceElement(id) { vanilla ->
            HudElement { context, tick ->
                val offset = BottomHudLayout.reservedHeight()
                if (offset == 0) {
                    vanilla.extractRenderState(context, tick)
                } else {
                    context.pose().pushMatrix()
                    context.pose().translate(0f, -offset.toFloat())
                    try {
                        vanilla.extractRenderState(context, tick)
                    } finally {
                        context.pose().popMatrix()
                    }
                }
            }
        }
    }

    private fun isVisible(context: GuiOverlayContext): Boolean =
        isLiveVisible() && (context.type == GuiOverlayContextType.WORLD || config.settings.showInScreens)

    private fun isLiveVisible(): Boolean {
        val minecraft = Minecraft.getInstance()
        val screen = MinecraftClient.screen(minecraft)
        return isActive() &&
            !MinecraftClient.isGuiHidden(minecraft) &&
            (screen == null || config.settings.showInScreens || screen is SkysoftHudEditor.EditorScreen)
    }

    private fun isActive(): Boolean {
        val player = Minecraft.getInstance().player
        return config.enabled && HypixelLocationState.inSkyBlock && player != null && !player.isSpectator
    }

    private fun bottomReservation(): Int {
        if (!isLiveVisible()) return 0
        val layout = currentLayout()
        val scaledHeight = (layout.height * config.position.effectiveScale).roundToInt()
        val top = config.position.getAbsY0AllowingOverflow(scaledHeight)
        val screenHeight = Minecraft.getInstance().window.guiScaledHeight
        val bottom = top + scaledHeight
        if (bottom < screenHeight - STANDARD_HOTBAR_HEIGHT) return 0
        return (screenHeight - STANDARD_HOTBAR_HEIGHT - top).coerceAtLeast(0)
    }

    private fun currentLayout(): InventoryHudLayout = InventoryHudLayout(
        showArmor = config.settings.armor,
        showEquipment = config.settings.equipment,
    )

    private fun currentRenderable(): InventoryHudRenderable {
        val player = Minecraft.getInstance().player
        val equipment = if (config.settings.equipment) InventoryEquipmentCache.stacks() else emptyList()
        return InventoryHudRenderable(currentLayout(), player, equipment)
    }
}

internal data class InventoryHudLayout(
    val showArmor: Boolean,
    val showEquipment: Boolean,
) {
    val armorX: Int? = 0.takeIf { showArmor }
    val mainX: Int = if (showArmor) SIDE_PANEL_WIDTH + GROUP_GAP else 0
    val equipmentX: Int? = (mainX + MAIN_PANEL_WIDTH + GROUP_GAP).takeIf { showEquipment }
    val width: Int = mainX + MAIN_PANEL_WIDTH + if (showEquipment) GROUP_GAP + SIDE_PANEL_WIDTH else 0
    val height: Int = MAIN_PANEL_HEIGHT + GROUP_GAP + HOTBAR_PANEL_HEIGHT

    companion object {
        const val SLOT_SIZE = 18
        const val PANEL_PADDING = 2
        const val GROUP_GAP = 4
        const val MAIN_COLUMNS = 9
        const val MAIN_ROWS = 3
        const val SIDE_ROWS = 4
        const val MAIN_PANEL_WIDTH = MAIN_COLUMNS * SLOT_SIZE + PANEL_PADDING * 2
        const val MAIN_PANEL_HEIGHT = MAIN_ROWS * SLOT_SIZE + PANEL_PADDING * 2
        const val HOTBAR_PANEL_HEIGHT = SLOT_SIZE + PANEL_PADDING * 2
        const val SIDE_PANEL_WIDTH = SLOT_SIZE + PANEL_PADDING * 2
        const val SIDE_PANEL_HEIGHT = SIDE_ROWS * SLOT_SIZE + PANEL_PADDING * 2
        const val SIDE_Y = (MAIN_PANEL_HEIGHT + GROUP_GAP + HOTBAR_PANEL_HEIGHT - SIDE_PANEL_HEIGHT) / 2
    }
}

private class InventoryHudRenderable(
    private val layout: InventoryHudLayout,
    private val player: Player?,
    private val equipment: List<ItemStack>,
) : GuiRenderable {
    override val width: Int = layout.width
    override val height: Int = layout.height

    private val details = SkysoftConfigGui.config().gui.inventoryHud.details
    private val backgroundColor = details.backgroundColor.get().toColor().rgb
    private val outlineColor = details.outlineColor.get().toColor().rgb
    private val slotBackgroundColor = details.slotBackgroundColor.get().toColor().rgb
    private val itemCountColor = details.itemCountColor.get().toColor().rgb

    override fun render(context: GuiGraphicsExtractor) {
        drawGridPanel(
            context,
            layout.mainX,
            0,
            InventoryHudLayout.MAIN_COLUMNS,
            InventoryHudLayout.MAIN_ROWS,
        ) { row, column -> player?.inventory?.getItem(MAIN_INVENTORY_START + row * InventoryHudLayout.MAIN_COLUMNS + column) }
        drawGridPanel(
            context,
            layout.mainX,
            InventoryHudLayout.MAIN_PANEL_HEIGHT + InventoryHudLayout.GROUP_GAP,
            InventoryHudLayout.MAIN_COLUMNS,
            1,
        ) { _, column -> player?.inventory?.getItem(column) }
        layout.armorX?.let { x ->
            drawGridPanel(context, x, InventoryHudLayout.SIDE_Y, 1, InventoryHudLayout.SIDE_ROWS) { row, _ ->
                player?.inventory?.getItem(LAST_ARMOR_SLOT - row)
            }
        }
        layout.equipmentX?.let { x ->
            drawGridPanel(context, x, InventoryHudLayout.SIDE_Y, 1, InventoryHudLayout.SIDE_ROWS) { row, _ ->
                equipment.getOrNull(row)
            }
        }
    }

    private fun drawGridPanel(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        columns: Int,
        rows: Int,
        stack: (row: Int, column: Int) -> ItemStack?,
    ) {
        val panelWidth = columns * InventoryHudLayout.SLOT_SIZE + InventoryHudLayout.PANEL_PADDING * 2
        val panelHeight = rows * InventoryHudLayout.SLOT_SIZE + InventoryHudLayout.PANEL_PADDING * 2
        drawPanel(context, x, y, panelWidth, panelHeight)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val slotX = x + InventoryHudLayout.PANEL_PADDING + column * InventoryHudLayout.SLOT_SIZE
                val slotY = y + InventoryHudLayout.PANEL_PADDING + row * InventoryHudLayout.SLOT_SIZE
                drawSlot(context, slotX, slotY, stack(row, column) ?: ItemStack.EMPTY)
                if (rows == 1 && columns == InventoryHudLayout.MAIN_COLUMNS &&
                    column == player?.inventory?.selectedSlot
                ) {
                    drawOutline(
                        context,
                        slotX,
                        slotY,
                        InventoryHudLayout.SLOT_SIZE,
                        InventoryHudLayout.SLOT_SIZE,
                        HOTBAR_SELECTION_COLOR,
                    )
                }
            }
        }
    }

    private fun drawPanel(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        when {
            details.background && details.outline -> {
                context.fillOverlayBackground(x, y, x + width, y + height, outlineColor, details.roundedCorners)
                context.fillOverlayBackground(
                    x + 1,
                    y + 1,
                    x + width - 1,
                    y + height - 1,
                    backgroundColor,
                    details.roundedCorners,
                )
            }
            details.background ->
                context.fillOverlayBackground(x, y, x + width, y + height, backgroundColor, details.roundedCorners)
            details.outline -> drawOutline(context, x, y, width, height, outlineColor)
        }
    }

    private fun drawSlot(context: GuiGraphicsExtractor, x: Int, y: Int, stack: ItemStack) {
        if (details.slotBackgrounds) {
            context.fillOverlayBackground(
                x,
                y,
                x + InventoryHudLayout.SLOT_SIZE,
                y + InventoryHudLayout.SLOT_SIZE,
                slotBackgroundColor,
                details.roundedCorners,
            )
        }
        if (stack.isEmpty) return
        val itemX = x + ITEM_INSET
        val itemY = y + ITEM_INSET
        RarityHighlightRenderer.renderSlot(context, stack, itemX, itemY) {
            context.item(stack, itemX, itemY)
        }
        val countText = if (stack.count == 1) null else ""
        context.itemDecorations(Minecraft.getInstance().font, stack, itemX, itemY, countText)
        if (stack.count != 1) {
            val text = stack.count.toString()
            val font = Minecraft.getInstance().font
            context.text(
                font,
                text,
                itemX + ITEM_COUNT_RIGHT - font.width(text),
                itemY + ITEM_COUNT_Y,
                itemCountColor,
                details.itemCountShadow,
            )
        }
    }

    private fun drawOutline(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        if (!details.roundedCorners) {
            context.outline(x, y, width, height, color)
            return
        }
        val right = x + width
        val bottom = y + height
        context.fill(x + 2, y, right - 2, y + 1, color)
        context.fill(x + 1, y + 1, x + 2, y + 2, color)
        context.fill(right - 2, y + 1, right - 1, y + 2, color)
        context.fill(x, y + 2, x + 1, bottom - 2, color)
        context.fill(right - 1, y + 2, right, bottom - 2, color)
        context.fill(x + 1, bottom - 2, x + 2, bottom - 1, color)
        context.fill(right - 2, bottom - 2, right - 1, bottom - 1, color)
        context.fill(x + 2, bottom - 1, right - 2, bottom, color)
    }
}

private const val MAIN_INVENTORY_START = 9
private const val LAST_ARMOR_SLOT = 39
private const val STANDARD_HOTBAR_HEIGHT = 26
private const val ITEM_INSET = 1
private const val ITEM_COUNT_RIGHT = 17
private const val ITEM_COUNT_Y = 9
private val HOTBAR_SELECTION_COLOR = 0xFF55FFFF.toInt()
