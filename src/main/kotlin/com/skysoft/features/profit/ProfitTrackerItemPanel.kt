package com.skysoft.features.profit

import com.skysoft.config.ProfitTrackerPriceSource
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemRarity
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.features.pets.PetRepository
import com.skysoft.gui.OverlayControlTooltips
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.withScaledAlpha
import com.skysoft.utils.TextUtilities.removeColor
import com.skysoft.utils.animation.PanelFadeTransition
import com.skysoft.utils.gui.OverlayPanelStyle
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack

internal sealed interface ProfitTrackerControl {
    data object Period : ProfitTrackerControl
    data object PriceSource : ProfitTrackerControl
    data object Reset : ProfitTrackerControl
    data object CancelReset : ProfitTrackerControl
    data object ConfirmReset : ProfitTrackerControl
    data object More : ProfitTrackerControl
    data class ManageItem(
        val itemId: String,
        val stack: ItemStack,
        val formattedName: String,
    ) : ProfitTrackerControl
    data class ItemPriceSource(val itemId: String) : ProfitTrackerControl
    data class ExcludeItem(val itemId: String) : ProfitTrackerControl
    data class RestoreItem(val itemId: String) : ProfitTrackerControl
    data class RemoveCustomItem(val itemId: String) : ProfitTrackerControl
    data object AddItem : ProfitTrackerControl
    data object ResetCustomizations : ProfitTrackerControl
}

internal data class ProfitTrackerPanelControl(
    val action: ProfitTrackerControl,
    val bounds: Rect,
    val tooltipLines: List<String> = emptyList(),
)

internal class ProfitTrackerItemPanel(
    nanoTime: () -> Long = System::nanoTime,
) {
    private var content: Content? = null
    private val transition = PanelFadeTransition(nanoTime)

    fun toggleOverview() {
        if (content != null && !transition.isClosing) close() else open(Content.Overview)
    }

    fun showOverview() = open(Content.Overview)

    fun openItem(itemId: String) = open(Content.Item(itemId))

    fun toggleItem(itemId: String) {
        if (content == Content.Item(itemId) && !transition.isClosing) close() else openItem(itemId)
    }

    fun beginAddingItem() = open(Content.AddItem)

    fun isAddingItem(): Boolean = content == Content.AddItem && !transition.isClosing

    fun close() {
        if (content == null) return
        transition.hide()
    }

    fun clear() {
        content = null
        transition.reset()
    }

    fun render(
        context: GuiGraphicsExtractor,
        preset: ProfitTrackerPreset,
        trackerWidth: Int,
        placeRight: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): ProfitTrackerPanelControl? {
        val current = content ?: return null
        val opacity = transition.opacity()
        if (!transition.isVisible) {
            clear()
            return null
        }
        val rows = rows(current, preset)
        val width = maxOf(
            PANEL_MINIMUM_WIDTH,
            rows.maxOfOrNull { row -> Minecraft.getInstance().font.width(row.text) + row.iconOffset } ?: 0,
        ) + OverlayPanelStyle.PADDING * 2
        val height = rows.sumOf(PanelRow::height) + OverlayPanelStyle.PADDING * 2
        val x = if (placeRight) trackerWidth + PANEL_GAP else -width - PANEL_GAP
        context.fill(x, 0, x + width, height, OverlayPanelStyle.BACKGROUND.withScaledAlpha(opacity))
        context.outline(x, 0, width, height, OverlayPanelStyle.OUTLINE.withScaledAlpha(opacity))
        var hovered: ProfitTrackerPanelControl? = null
        var rowY = OverlayPanelStyle.PADDING
        rows.forEach { row ->
            val isHovered = transition.isInteractive && row.action != null &&
                mouseX in x until x + width && mouseY in rowY until rowY + row.height
            if (isHovered) context.fill(x, rowY, x + width, rowY + row.height, PANEL_HOVER.withScaledAlpha(opacity))
            if (opacity > ICON_VISIBILITY_THRESHOLD) {
                row.icon?.let { ItemIconRenderable(it, PANEL_ICON_SCALE).renderAt(context, x + OverlayPanelStyle.PADDING, rowY) }
            }
            context.text(
                Minecraft.getInstance().font,
                row.text,
                x + OverlayPanelStyle.PADDING + row.iconOffset,
                rowY + (row.height - PANEL_TEXT_HEIGHT) / 2,
                TEXT_BASE_COLOR.withScaledAlpha(opacity),
                false,
            )
            if (isHovered) {
                hovered = ProfitTrackerPanelControl(
                    row.action,
                    Rect(x, rowY, width, row.height),
                    row.tooltipLines,
                )
            }
            rowY += row.height
        }
        return hovered
    }

    private fun open(next: Content) {
        content = next
        transition.show()
    }

    private fun rows(content: Content, preset: ProfitTrackerPreset): List<PanelRow> = when (content) {
        Content.Overview -> overviewRows(preset)
        Content.AddItem -> listOf(
            PanelRow(styledText("Add Item", TITLE_COLOR, bold = true)),
            PanelRow(styledText("Click an inventory item.", MUTED_COLOR)),
        )
        is Content.Item -> itemRows(preset, content.itemId)
    }

    private fun overviewRows(preset: ProfitTrackerPreset): List<PanelRow> {
        val customizations = ProfitTrackerItemCustomizations.data(preset)
        return buildList {
            add(PanelRow(styledText("Item Settings", TITLE_COLOR, bold = true)))
            add(PanelRow(styledText("Add Item", ACTION_COLOR), ProfitTrackerControl.AddItem))
            customizations?.excludedItems?.takeIf { it.isNotEmpty() }?.let { excluded ->
                add(PanelRow(Component.empty(), heightOverride = PANEL_SECTION_GAP))
                add(PanelRow(styledText("Excluded", MUTED_COLOR)))
                excluded.forEach { itemId ->
                    val item = profitTrackerItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RestoreItem(itemId),
                            listOf("§7Restore"),
                            item.stack,
                        ),
                    )
                }
            }
            customizations?.customItems?.takeIf { it.isNotEmpty() }?.let { customItems ->
                add(PanelRow(styledText("Added Items", MUTED_COLOR)))
                customItems.forEach { itemId ->
                    val item = profitTrackerItemPresentation(itemId)
                    add(
                        PanelRow(
                            item.component,
                            ProfitTrackerControl.RemoveCustomItem(itemId),
                            listOf("§7Remove"),
                            item.stack,
                        ),
                    )
                }
            }
            add(
                PanelRow(
                    styledText("Reset Customizations", DANGER_COLOR),
                    ProfitTrackerControl.ResetCustomizations,
                ),
            )
        }
    }

    private fun itemRows(preset: ProfitTrackerPreset, itemId: String): List<PanelRow> {
        val override = ProfitTrackerItemCustomizations.priceSourceOverride(preset, itemId)
        val source = override?.toString() ?: "Tracker Default"
        val sources = listOf("Tracker Default") + ProfitTrackerPriceSource.entries.map { it.toString() }
        val presentation = profitTrackerItemPresentation(itemId)
        return listOf(
            PanelRow(presentation.component, icon = presentation.stack),
            PanelRow(
                styledText("Price Source ", MUTED_COLOR).append(styledText("[$source]", PRICE_COLOR, bold = true)),
                ProfitTrackerControl.ItemPriceSource(itemId),
                OverlayControlTooltips.cycle("Item Price Source", sources, (override?.ordinal ?: -1) + 1),
            ),
            PanelRow(styledText("Exclude", DANGER_COLOR), ProfitTrackerControl.ExcludeItem(itemId)),
        )
    }

    private sealed interface Content {
        data object Overview : Content
        data object AddItem : Content
        data class Item(val itemId: String) : Content
    }

    private data class PanelRow(
        val text: Component,
        val action: ProfitTrackerControl? = null,
        val tooltipLines: List<String> = emptyList(),
        val icon: ItemStack? = null,
        val heightOverride: Int? = null,
    ) {
        val height: Int = heightOverride ?: if (icon == null) PANEL_ROW_HEIGHT else PANEL_ICON_ROW_HEIGHT
        val iconOffset: Int = if (icon == null) 0 else PANEL_ICON_TEXT_OFFSET
    }
}

private fun profitTrackerItemPresentation(itemId: String): ProfitTrackerItemPresentation {
    val key = SkyBlockDataRepository.itemKey(itemId)
    val entry = SkyBlockDataRepository.entry(key)
    val stack = SkyBlockDataRepository.displayStack(key) ?: PetRepository.itemStackOrNull(itemId)
    val formattedName = (entry?.formattedDisplayName ?: PetRepository.itemName(itemId) ?: itemId)
        .replace("Enchanted ", "Ench ")
    val name = formattedName.removeColor()
    val rarity = LEGACY_COLOR_PATTERN.find(formattedName)?.groupValues?.get(1)?.singleOrNull()
        ?.let(SkyBlockRarity::getByColorCode) ?: stack?.let(SkyBlockItemRarity::from)
    val color = rarity?.color?.rgb ?: ITEM_DEFAULT_COLOR
    return ProfitTrackerItemPresentation(name, formattedName, stack, styledText(name, color))
}

private data class ProfitTrackerItemPresentation(
    val name: String,
    val formattedName: String,
    val stack: ItemStack?,
    val component: Component,
)

private fun styledText(text: String, color: Int, bold: Boolean = false): MutableComponent =
    Component.literal(text).withStyle { style -> style.withColor(color and RGB_MASK).withBold(bold) }

private val LEGACY_COLOR_PATTERN = Regex("§([0-9a-f])", RegexOption.IGNORE_CASE)

private const val PANEL_MINIMUM_WIDTH = 130
private const val PANEL_ROW_HEIGHT = 11
private const val PANEL_SECTION_GAP = 6
private const val PANEL_ICON_ROW_HEIGHT = 16
private const val PANEL_TEXT_HEIGHT = 9
private const val PANEL_ICON_TEXT_OFFSET = 16
private const val PANEL_ICON_SCALE = 0.75
private const val ICON_VISIBILITY_THRESHOLD = 0.35
private const val PANEL_GAP = 4
private const val TEXT_BASE_COLOR = 0xFFFFFFFF.toInt()
private const val TITLE_COLOR = 0xFFFFFF55.toInt()
private const val ITEM_DEFAULT_COLOR = 0xFFFFFFFF.toInt()
private const val MUTED_COLOR = 0xFFAAAAAA.toInt()
private const val ACTION_COLOR = 0xFF55FF55.toInt()
private const val PRICE_COLOR = 0xFFFFFF55.toInt()
private const val DANGER_COLOR = 0xFFFF5555.toInt()
private const val PANEL_HOVER = 0x28FFFFFF
