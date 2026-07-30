package com.skysoft.features.inventory

import com.skysoft.config.INVENTORY_HUD_DEFAULT_BOTTOM_MARGIN
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
                visible = ::isVisible,
                render = { context, _ -> renderParts(context) },
            ),
        )
        InventoryHudPart.entries.forEach { part ->
            HudEditorRegistry.register(object : HudEditorElement {
                override val id: String = "inventory_hud_${part.name.lowercase()}"
                override val label: String = part.label
                override val position get() = part.position()
                override val canScale: Boolean = false
                override val editorGridSpacing: Int = INVENTORY_HUD_GRID_SPACING
                override val hasEditorBackground: Boolean
                    get() = !config.details.background && !config.details.outline && !config.details.slotBackgrounds
                override fun width(): Int = part.width
                override fun height(): Int = part.height
                override fun isVisible(): Boolean = config.enabled && part.isEnabled()
                override fun renderDummy(context: GuiGraphicsExtractor) = currentRenderable(part).render(context)
                override fun openConfig() = SkysoftConfigGui.open("Inventory HUD")
            })
        }
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
        val inventoryOffset = if (config.settings.inventory) {
            InventoryHudLayout.MAIN_PANEL_HEIGHT + InventoryHudLayout.GROUP_GAP
        } else {
            0
        }
        val sideOffset = if (config.settings.armor || config.settings.equipment) {
            InventoryHudLayout.MAIN_PANEL_HEIGHT
        } else {
            0
        }
        val hotbarOffset = InventoryHudLayout.HOTBAR_PANEL_HEIGHT -
            VANILLA_HOTBAR_TOP_OFFSET -
            INVENTORY_HUD_DEFAULT_BOTTOM_MARGIN
        return hotbarOffset + maxOf(inventoryOffset, sideOffset)
    }

    private fun currentRenderable(part: InventoryHudPart): InventoryHudRenderable {
        val player = Minecraft.getInstance().player
        val equipment = if (config.settings.equipment) InventoryEquipmentCache.stacks() else emptyList()
        return InventoryHudRenderable(part, player, equipment)
    }

    private fun renderParts(context: GuiGraphicsExtractor) {
        InventoryHudPart.entries.filter(InventoryHudPart::isEnabled).forEach { part ->
            part.position().renderRenderable(context, currentRenderable(part))
        }
    }
}

internal object InventoryHudLayout {
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
}

private enum class InventoryHudPart(val label: String, val width: Int, val height: Int) {
    HOTBAR("Inventory Hotbar", InventoryHudLayout.MAIN_PANEL_WIDTH, InventoryHudLayout.HOTBAR_PANEL_HEIGHT),
    INVENTORY("Inventory", InventoryHudLayout.MAIN_PANEL_WIDTH, InventoryHudLayout.MAIN_PANEL_HEIGHT),
    ARMOR("Armor", InventoryHudLayout.SIDE_PANEL_WIDTH, InventoryHudLayout.SIDE_PANEL_HEIGHT),
    EQUIPMENT("Equipment", InventoryHudLayout.SIDE_PANEL_WIDTH, InventoryHudLayout.SIDE_PANEL_HEIGHT),
    ;

    fun isEnabled(): Boolean = when (this) {
        HOTBAR -> true
        INVENTORY -> inventoryHudConfig().settings.inventory
        ARMOR -> inventoryHudConfig().settings.armor
        EQUIPMENT -> inventoryHudConfig().settings.equipment
    }

    fun position() = when (this) {
        HOTBAR -> inventoryHudConfig().position
        INVENTORY -> inventoryHudConfig().inventoryPosition
        ARMOR -> inventoryHudConfig().armorPosition
        EQUIPMENT -> inventoryHudConfig().equipmentPosition
    }
}

private fun inventoryHudConfig() = SkysoftConfigGui.config().gui.inventoryHud

private class InventoryHudRenderable(
    private val part: InventoryHudPart,
    private val player: Player?,
    private val equipment: List<ItemStack>,
) : GuiRenderable {
    override val width: Int = part.width
    override val height: Int = part.height

    private val details = SkysoftConfigGui.config().gui.inventoryHud.details
    private val backgroundColor = details.backgroundColor.get().toColor().rgb
    private val outlineColor = details.outlineColor.get().toColor().rgb
    private val slotBackgroundColor = details.slotBackgroundColor.get().toColor().rgb
    private val itemCountColor = details.itemCountColor.get().toColor().rgb

    override fun render(context: GuiGraphicsExtractor) {
        when (part) {
            InventoryHudPart.HOTBAR ->
                drawGridPanel(context, InventoryHudLayout.MAIN_COLUMNS, 1) { _, column ->
                    player?.inventory?.getItem(column)
                }
            InventoryHudPart.INVENTORY ->
                drawGridPanel(
                    context,
                    InventoryHudLayout.MAIN_COLUMNS,
                    InventoryHudLayout.MAIN_ROWS,
                ) { row, column ->
                    player?.inventory?.getItem(
                        MAIN_INVENTORY_START + row * InventoryHudLayout.MAIN_COLUMNS + column,
                    )
                }
            InventoryHudPart.ARMOR ->
                drawGridPanel(context, 1, InventoryHudLayout.SIDE_ROWS) { row, _ ->
                    player?.inventory?.getItem(LAST_ARMOR_SLOT - row)
                }
            InventoryHudPart.EQUIPMENT ->
                drawGridPanel(context, 1, InventoryHudLayout.SIDE_ROWS) { row, _ -> equipment.getOrNull(row) }
        }
    }

    private fun drawGridPanel(
        context: GuiGraphicsExtractor,
        columns: Int,
        rows: Int,
        stack: (row: Int, column: Int) -> ItemStack?,
    ) {
        val panelWidth = columns * InventoryHudLayout.SLOT_SIZE + InventoryHudLayout.PANEL_PADDING * 2
        val panelHeight = rows * InventoryHudLayout.SLOT_SIZE + InventoryHudLayout.PANEL_PADDING * 2
        drawPanel(context, 0, 0, panelWidth, panelHeight)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val slotX = InventoryHudLayout.PANEL_PADDING + column * InventoryHudLayout.SLOT_SIZE
                val slotY = InventoryHudLayout.PANEL_PADDING + row * InventoryHudLayout.SLOT_SIZE
                drawSlot(context, slotX, slotY, stack(row, column) ?: ItemStack.EMPTY)
                if (part == InventoryHudPart.HOTBAR && column == player?.inventory?.selectedSlot) {
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
private const val VANILLA_HOTBAR_TOP_OFFSET = 22
private const val INVENTORY_HUD_GRID_SPACING = 2
private const val ITEM_INSET = 1
private const val ITEM_COUNT_RIGHT = 17
private const val ITEM_COUNT_Y = 9
private val HOTBAR_SELECTION_COLOR = 0xFF55FFFF.toInt()
