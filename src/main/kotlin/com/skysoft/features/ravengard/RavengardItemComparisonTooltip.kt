package com.skysoft.features.ravengard

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.ravengard.RAVENGARD_NAMESPACE
import com.skysoft.gui.tooltip.AdjacentTooltipRenderer
import com.skysoft.mixin.AbstractContainerScreenAccessor
import com.skysoft.utils.ItemTooltipEvents
import java.math.BigDecimal
import java.util.Optional
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

object RavengardItemComparisonTooltip {
    fun register() {
        ItemTooltipEvents.register("Ravengard item comparison deltas", ::isEnabled) tooltip@{ stack, _, _, tooltip ->
            val equipped = comparedEquippedItem(stack) ?: return@tooltip
            addRavengardStatDeltas(tooltip, equipped.get(DataComponents.LORE)?.lines().orEmpty())
        }
    }

    fun prepare(
        screen: AbstractContainerScreen<*>,
        context: GuiGraphicsExtractor,
    ) {
        if (!isEnabled()) return
        if (!screen.menu.carried.isEmpty) return
        val hovered = (screen as AbstractContainerScreenAccessor).skysoftGetHoveredSlot()?.item ?: return
        val equipped = comparedEquippedItem(hovered) ?: return

        val minecraft = Minecraft.getInstance()
        val lines = buildList {
            add(EQUIPPED_HEADER.visualOrderText)
            Screen.getTooltipFromItem(minecraft, equipped).forEach { add(it.visualOrderText) }
        }
        AdjacentTooltipRenderer.prepare(context, lines, equipped.get(DataComponents.TOOLTIP_STYLE))
    }

    private fun isEnabled(): Boolean =
        HypixelLocationState.inRavengard && SkysoftConfigGui.config().ravengard.showItemComparison

    private fun comparedEquippedItem(hovered: ItemStack): ItemStack? {
        val group = ravengardComparisonGroup(hovered.get(DataComponents.ITEM_MODEL)) ?: return null
        val equipped = equippedItem(hovered, group) ?: return null
        return equipped.takeUnless { it.isEmpty || it === hovered }
    }

    private fun equippedItem(hovered: ItemStack, group: String): ItemStack? {
        val player = Minecraft.getInstance().player ?: return null
        if (group == ARMOR_GROUP) {
            val slot = hovered.get(DataComponents.EQUIPPABLE)?.slot() ?: return null
            return player.getItemBySlot(slot).takeIf { slot.type == EquipmentSlot.Type.HUMANOID_ARMOR }
        }
        return ACCESSORY_EQUIPMENT_SLOTS.asSequence()
            .map(player.inventory::getItem)
            .firstOrNull { ravengardComparisonGroup(it.get(DataComponents.ITEM_MODEL)) == group }
    }
}

internal fun addRavengardStatDeltas(
    candidateLines: MutableList<Component>,
    equippedLines: Iterable<Component>,
) {
    val equippedStats = equippedLines.mapNotNull(::ravengardStat)
    if (equippedStats.isEmpty()) return
    val equippedByKey = equippedStats.associateBy(RavengardStat::key)
    val candidateStats = candidateLines.mapIndexedNotNull { index, line ->
        ravengardStat(line)?.let { IndexedRavengardStat(index, it) }
    }
    val candidateKeys = candidateStats.mapTo(mutableSetOf()) { it.stat.key }

    candidateStats.forEach { (index, stat) ->
        val delta = stat.value - (equippedByKey[stat.key]?.value ?: BigDecimal.ZERO)
        if (delta.signum() != 0) candidateLines[index] = candidateLines[index].withDeltaBeforeName(stat, delta)
    }

    val missing = equippedStats
        .filter { it.key !in candidateKeys && it.value.signum() != 0 }
        .map { stat -> missingStatLine(stat, stat.value.negate()) }
    if (missing.isEmpty()) return
    val insertionIndex = candidateStats.maxOfOrNull(IndexedRavengardStat::index)?.plus(1)
        ?: fallbackStatInsertionIndex(candidateLines)
    candidateLines.addAll(insertionIndex, missing)
}

private fun ravengardStat(line: Component): RavengardStat? {
    val text = line.string
    val trimmed = text.trim()
    val match = RAVENGARD_STAT_PATTERN.matchEntire(trimmed) ?: return null
    val value = match.groups["value"]?.value?.replace(",", "")?.toBigDecimalOrNull() ?: return null
    val nameGroup = match.groups["name"] ?: return null
    val name = nameGroup.value.trim().replace(STAT_MULTIPLIER_PATTERN, "")
    if (name.isEmpty()) return null
    return RavengardStat(
        key = RavengardStatKey(name, match.groups["percent"]?.value == "%"),
        icon = match.groups["icon"]?.value.orEmpty(),
        value = value,
        nameOffset = text.indexOf(trimmed) + nameGroup.range.first,
    )
}

internal fun ravengardStatValue(line: Component, name: String): BigDecimal? =
    ravengardStat(line)?.takeIf { stat -> stat.key.name == name && !stat.key.percent }?.value

private fun Component.withDeltaBeforeName(stat: RavengardStat, delta: BigDecimal): Component {
    val result = Component.empty()
    var offset = 0
    var inserted = false
    visit({ style: Style, segment: String ->
        val segmentEnd = offset + segment.length
        if (!inserted && stat.nameOffset <= segmentEnd) {
            val insertion = (stat.nameOffset - offset).coerceIn(0, segment.length)
            result.append(Component.literal(segment.substring(0, insertion)).withStyle(style))
            result.append(deltaComponent(delta, stat.key.percent))
            result.append(Component.literal(segment.substring(insertion)).withStyle(style))
            inserted = true
        } else {
            result.append(Component.literal(segment).withStyle(style))
        }
        offset = segmentEnd
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return result
}

private fun deltaComponent(delta: BigDecimal, percent: Boolean): Component =
    Component.literal("(${formattedDelta(delta, percent)}) ").withStyle { style ->
        style.withColor(TextColor.fromRgb(deltaColor(delta))).withItalic(false)
    }

private fun missingStatLine(stat: RavengardStat, delta: BigDecimal): Component =
    Component.literal("${stat.icon}${formattedDelta(delta, stat.key.percent)}").withStyle { style ->
        style.withColor(TextColor.fromRgb(deltaColor(delta))).withItalic(false)
    }.append(
        Component.literal(" ${stat.key.name}").withStyle { style ->
            style.withColor(TextColor.fromRgb(STAT_NAME_COLOR)).withItalic(false)
        },
    )

private fun formattedDelta(delta: BigDecimal, percent: Boolean): String {
    val value = delta.stripTrailingZeros().toPlainString()
    return (if (delta.signum() > 0) "+$value" else value) + if (percent) "%" else ""
}

private fun deltaColor(delta: BigDecimal): Int = if (delta.signum() > 0) GAIN_COLOR else LOSS_COLOR

private fun fallbackStatInsertionIndex(lines: List<Component>): Int {
    val crownIndex = lines.indexOfFirst { it.string.trimStart().startsWith(CROWN) }
    if (crownIndex < 0) return lines.size
    val preceding = crownIndex - 1
    return preceding.takeIf { it >= 0 && lines[it].string.isBlank() } ?: crownIndex
}

internal fun ravengardComparisonGroup(itemModel: Identifier?): String? {
    if (itemModel?.namespace != RAVENGARD_NAMESPACE) return null
    val path = itemModel.path
    if (path.endsWith(GREYED_SUFFIX)) return null
    if (path.startsWith(ARMOR_PREFIX)) return ARMOR_GROUP
    if (!path.startsWith(ACCESSORY_PREFIX)) return null
    val category = path.removePrefix(ACCESSORY_PREFIX).substringBefore('/')
    return category.takeIf(String::isNotBlank)?.let { "$ACCESSORY_PREFIX$it" }
}

private val EQUIPPED_HEADER = Component.literal("Equipped").withStyle { style ->
    style.withColor(TextColor.fromRgb(EQUIPPED_COLOR)).withBold(true).withItalic(false)
}
private val ACCESSORY_EQUIPMENT_SLOTS = 9..12
private val RAVENGARD_STAT_PATTERN = Regex(
    """^(?<icon>\p{Co})(?<value>[+-]?\d[\d,]*(?:\.\d+)?)(?<percent>%?) (?<name>.+)$""",
)
private val STAT_MULTIPLIER_PATTERN = Regex(""" \([+-]?\d+(?:\.\d+)?x\)$""")
private data class RavengardStatKey(val name: String, val percent: Boolean)
private data class RavengardStat(
    val key: RavengardStatKey,
    val icon: String,
    val value: BigDecimal,
    val nameOffset: Int,
)
private data class IndexedRavengardStat(val index: Int, val stat: RavengardStat)
private const val ARMOR_PREFIX = "item/armor/"
private const val ARMOR_GROUP = "item/armor"
private const val ACCESSORY_PREFIX = "item/accessories/"
private const val GREYED_SUFFIX = "_greyed"
private const val CROWN = "\uD83D\uDC51"
private const val EQUIPPED_COLOR = 0xFFCE47
private const val GAIN_COLOR = 0x5FEC7B
private const val LOSS_COLOR = 0xFF5555
private const val STAT_NAME_COLOR = 0xFFFFFF
