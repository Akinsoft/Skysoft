package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.ItemListEntryKey
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockDropSource
import com.skysoft.data.skyblock.SkyBlockEntityInfo
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.isMob
import com.skysoft.data.skyblock.price.SkyBlockPriceData
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.Rect
import com.skysoft.utils.render.LegacyTextRenderer
import kotlin.math.roundToLong
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class ItemListInfoPanel {
    private var currentKey: ItemListEntryKey? = null
    private var scrollOffset = 0
    private var maximumScroll = 0
    private var entityBounds: List<Pair<Rect, String>> = emptyList()
    private var panelBounds: Rect? = null

    fun render(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        key: ItemListEntryKey,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (currentKey != key) {
            currentKey = key
            scrollOffset = 0
        }
        panelBounds = bounds
        val info = SkyBlockDataRepository.info(key)
        val entity = key.takeIf { it.kind == ItemListEntryKind.ENTITY }?.let { SkyBlockDataRepository.entity(it.id) }
        val motesSellPrice = SkyBlockPriceData.getNpcSellPrices(key.id).motes?.roundToLong()
        val lines = entity?.let(::entityInfoLines) ?: itemInfoLines(key, info, motesSellPrice)
        val headerLineCount = if (entity == null) {
            1 + (if (info?.category != null) 1 else 0) + (if (motesSellPrice != null) 1 else 0)
        } else {
            2
        }
        val headerLines = lines.take(headerLineCount)
        val loreLines = lines.drop(headerLineCount)
        val enchantmentTargets = info?.enchantment?.applicableOn?.let(::enchantmentTargets).orEmpty()
        val enchantmentHeight = if (info?.enchantment == null) {
            0
        } else {
            infoIconSectionHeight(font, bounds, "Applies to:", enchantmentTargets.size) +
                (info.enchantment.applyCostLevels?.let { INFO_ICON_SIZE + SECTION_GAP } ?: 0)
        }
        val contentHeight = lines.size * LINE_HEIGHT + enchantmentHeight
        updateScrollBounds(contentHeight, bounds)

        context.enableScissor(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        try {
            var y = bounds.y + CONTENT_INSET - scrollOffset
            headerLines.forEach { line ->
                LegacyTextRenderer.draw(context, line, bounds.x + CONTENT_INSET, y)
                y += LINE_HEIGHT
            }
            if (info?.enchantment != null) {
                y += renderEnchantmentTargets(
                    context,
                    font,
                    bounds,
                    y,
                    enchantmentTargets,
                    mouseX,
                    mouseY,
                )
                info.enchantment.applyCostLevels?.let { levels ->
                    renderExperienceCost(context, font, bounds, y, levels, mouseX, mouseY)
                    y += INFO_ICON_SIZE + SECTION_GAP
                }
            }
            loreLines.forEach { line ->
                LegacyTextRenderer.draw(context, line, bounds.x + CONTENT_INSET, y)
                y += LINE_HEIGHT
            }
        } finally {
            context.disableScissor()
        }
        entityBounds = emptyList()
        renderScrollbar(context, bounds)
    }

    fun entityAt(mouseX: Int, mouseY: Int): String? =
        panelBounds?.takeIf { it.contains(mouseX, mouseY) }?.let {
            entityBounds.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second
        }

    fun applyScroll(bounds: Rect, mouseX: Int, mouseY: Int, amount: Double): ViewerInputResult {
        if (!bounds.contains(mouseX, mouseY) || amount == 0.0 || maximumScroll == 0) return ViewerInputResult.IGNORED
        scrollOffset = (scrollOffset + if (amount > 0.0) -SCROLL_STEP else SCROLL_STEP).coerceIn(0, maximumScroll)
        return ViewerInputResult.HANDLED
    }

    private fun updateScrollBounds(contentHeight: Int, bounds: Rect) {
        maximumScroll = (contentHeight - bounds.height + CONTENT_INSET * 2).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maximumScroll)
    }

    private fun renderScrollbar(context: GuiGraphicsExtractor, bounds: Rect) {
        if (maximumScroll == 0) return
        val trackX = bounds.x + bounds.width - SCROLLBAR_RIGHT_INSET
        val trackHeight = bounds.height - SCROLLBAR_VERTICAL_INSET * 2
        val visibleRatio = bounds.height.toFloat() / (bounds.height + maximumScroll)
        val thumbHeight = (trackHeight * visibleRatio).toInt().coerceAtLeast(MINIMUM_THUMB_HEIGHT)
        val thumbTravel = trackHeight - thumbHeight
        val thumbY = bounds.y + SCROLLBAR_VERTICAL_INSET + thumbTravel * scrollOffset / maximumScroll
        context.fill(
            trackX,
            bounds.y + SCROLLBAR_VERTICAL_INSET,
            trackX + SCROLLBAR_WIDTH,
            bounds.y + bounds.height - SCROLLBAR_VERTICAL_INSET,
            SCROLLBAR_TRACK,
        )
        context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB)
    }

    private fun renderEnchantmentTargets(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        y: Int,
        targets: List<EnchantmentTarget>,
        mouseX: Int,
        mouseY: Int,
    ): Int {
        if (targets.isEmpty()) return 0
        LegacyTextRenderer.draw(context, "§7Applies to:", bounds.x + CONTENT_INSET, y + LABEL_Y_OFFSET)
        targets.zip(infoIconBounds(font, bounds, y, "Applies to:", targets.size)).forEach { (target, targetBounds) ->
            renderInfoIcon(
                context,
                targetBounds,
                ItemStack(target.item),
                listOf("§f${target.displayName}"),
                mouseX,
                mouseY,
            )
        }
        return infoIconSectionHeight(font, bounds, "Applies to:", targets.size)
    }

    private fun renderExperienceCost(
        context: GuiGraphicsExtractor,
        font: Font,
        bounds: Rect,
        y: Int,
        levels: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        LegacyTextRenderer.draw(context, "§7Apply cost:", bounds.x + CONTENT_INSET, y + LABEL_Y_OFFSET)
        val iconX = bounds.x + CONTENT_INSET + font.width("Apply cost:") + LABEL_ICON_GAP
        val iconBounds = Rect(iconX, y, INFO_ICON_SIZE, INFO_ICON_SIZE)
        renderInfoIcon(
            context,
            iconBounds,
            ItemStack(Items.EXPERIENCE_BOTTLE),
            listOf("§a$levels Exp Levels"),
            mouseX,
            mouseY,
        )
        LegacyTextRenderer.draw(
            context,
            "§a$levels Exp Levels",
            iconBounds.x + INFO_ICON_SIZE + INFO_TEXT_GAP,
            y + LABEL_Y_OFFSET,
        )
    }

    private fun infoIconBounds(font: Font, bounds: Rect, y: Int, label: String, count: Int): List<Rect> {
        val iconStartX = bounds.x + CONTENT_INSET + font.width(label) + LABEL_ICON_GAP
        val availableWidth = bounds.x + bounds.width - SCROLLBAR_RESERVED_WIDTH - iconStartX
        val columns = (availableWidth / INFO_ICON_SIZE).coerceAtLeast(1)
        return List(count) { index ->
            Rect(
                iconStartX + index % columns * INFO_ICON_SIZE,
                y + index / columns * INFO_ICON_SIZE,
                INFO_ICON_SIZE,
                INFO_ICON_SIZE,
            )
        }
    }

    private fun infoIconSectionHeight(font: Font, bounds: Rect, label: String, count: Int): Int {
        if (count == 0) return LINE_HEIGHT + SECTION_GAP
        val iconStartX = bounds.x + CONTENT_INSET + font.width(label) + LABEL_ICON_GAP
        val availableWidth = bounds.x + bounds.width - SCROLLBAR_RESERVED_WIDTH - iconStartX
        val columns = (availableWidth / INFO_ICON_SIZE).coerceAtLeast(1)
        return (count + columns - 1) / columns * INFO_ICON_SIZE + SECTION_GAP
    }

    private companion object {
        const val CONTENT_INSET = 8
        const val LINE_HEIGHT = 12
        const val INFO_ICON_SIZE = 18
        const val LABEL_Y_OFFSET = 5
        const val LABEL_ICON_GAP = 5
        const val INFO_TEXT_GAP = 3
        const val SECTION_GAP = 4
        const val SCROLL_STEP = 24
        const val SCROLLBAR_RESERVED_WIDTH = 8
        const val SCROLLBAR_RIGHT_INSET = 4
        const val SCROLLBAR_VERTICAL_INSET = 3
        const val SCROLLBAR_WIDTH = 2
        const val MINIMUM_THUMB_HEIGHT = 12
        val SCROLLBAR_TRACK = 0x80404040.toInt()
        val SCROLLBAR_THUMB = 0xFF9AA5AD.toInt()
    }
}

private fun renderInfoIcon(
    context: GuiGraphicsExtractor,
    bounds: Rect,
    stack: ItemStack,
    tooltip: List<String>,
    mouseX: Int,
    mouseY: Int,
) {
    context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, ENTITY_SLOT_BORDER)
    context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, ENTITY_SLOT_FILL)
    context.item(stack, bounds.x + 1, bounds.y + 1)
    if (bounds.contains(mouseX, mouseY)) SkysoftNativeTooltip.setForNextFrame(context, tooltip, mouseX, mouseY)
}

private data class EnchantmentTarget(val displayName: String, val item: net.minecraft.world.item.Item)

private fun enchantmentTargets(value: String): List<EnchantmentTarget> = value.split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map(::enchantmentTarget)
    .distinctBy(EnchantmentTarget::displayName)

private fun enchantmentTarget(value: String): EnchantmentTarget {
    val normalized = value.lowercase(java.util.Locale.ROOT)
    return when {
        "melee" in normalized || normalized == "weapons" -> EnchantmentTarget("Melee Weapons", Items.DIAMOND_SWORD)
        "longsword" in normalized || normalized == "sword" -> EnchantmentTarget("Swords", Items.IRON_SWORD)
        "bow" in normalized -> EnchantmentTarget("Bows", Items.BOW)
        "fishing" in normalized -> EnchantmentTarget("Fishing Rods", Items.FISHING_ROD)
        "helmet" in normalized -> EnchantmentTarget("Helmets", Items.DIAMOND_HELMET)
        "chestplate" in normalized -> EnchantmentTarget("Chestplates", Items.DIAMOND_CHESTPLATE)
        "legging" in normalized -> EnchantmentTarget("Leggings", Items.DIAMOND_LEGGINGS)
        "boot" in normalized -> EnchantmentTarget("Boots", Items.DIAMOND_BOOTS)
        normalized == "armor" -> EnchantmentTarget("Armor", Items.DIAMOND_CHESTPLATE)
        "axe" in normalized -> EnchantmentTarget("Axes", Items.DIAMOND_AXE)
        "hoe" in normalized -> EnchantmentTarget("Hoes", Items.DIAMOND_HOE)
        "mining" in normalized || normalized == "tools" -> EnchantmentTarget("Mining Tools", Items.DIAMOND_PICKAXE)
        "shear" in normalized -> EnchantmentTarget("Shears", Items.SHEARS)
        "necklace" in normalized || "equipment" in normalized -> EnchantmentTarget("Equipment", Items.IRON_CHESTPLATE)
        else -> EnchantmentTarget(value, Items.PAPER)
    }
}

internal fun renderEntityIcon(
    context: GuiGraphicsExtractor,
    font: Font,
    bounds: Rect,
    entityId: String,
    dropSources: List<SkyBlockDropSource> = emptyList(),
    mouseX: Int,
    mouseY: Int,
) {
    context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, ENTITY_SLOT_BORDER)
    context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, ENTITY_SLOT_FILL)
    val entity = SkyBlockDataRepository.entity(entityId)
    val stack = com.skysoft.data.skyblock.SkyBlockEntityStacks.stack(entityId)
    if (stack != null) {
        renderViewerItem(context, font, stack, bounds)
    } else {
        context.text(
            font,
            "?",
            bounds.x + (bounds.width - font.width("?")) / 2,
            bounds.y + MISSING_TEXT_Y_OFFSET,
            ENTITY_MISSING_COLOR,
            false,
        )
    }
    if (bounds.contains(mouseX, mouseY)) {
        SkysoftNativeTooltip.setForNextFrame(
            context,
            entityTooltipLines(entityId, entity, entity?.canNavigateToEntity() == true, dropSources),
            mouseX,
            mouseY,
        )
    }
}

internal fun entityTooltipLines(
    entityId: String,
    entity: SkyBlockEntityInfo?,
    canWarp: Boolean,
    dropSources: List<SkyBlockDropSource> = emptyList(),
): List<String> {
    if (entity == null) return listOf("§cSource data unavailable", "§7$entityId")
    return buildList {
        add("§f${entity.name}")
        val dropDetails = dropSources.flatMap(SkyBlockDropSource::details).distinct()
        when {
            entity.location != null -> add("§7${entity.location}")
            entity.details.isNotEmpty() -> entity.details.forEach { add("§7$it") }
            dropDetails.isEmpty() -> derivedEntityDetails(entityId, entity).forEach { add("§7$it") }
        }
        dropDetails.forEach { add("§7$it") }
        addAll(dropChanceLines(entity, dropSources))
        add("§8${entity.type}")
        val canOpen = entity.isMob()
        if (canOpen || canWarp) add("")
        if (canOpen) add("§e§lCLICK TO VIEW")
        if (canWarp) add(ItemListNpcWaypoint.warpActionLabel(entityId))
    }
}

internal fun entityInfoLines(entity: SkyBlockEntityInfo): List<String> = buildList {
    add("§7ID: §f${entity.id}")
    add("§7Type: §f${entity.type}")
    entity.location?.let { add("§7Location: §f$it") }
        ?: derivedEntityDetails(entity.id, entity).forEach { add("§7$it") }
    val levels = entity.lootTables.mapNotNull { it.mobLevel }
    val xp = entity.lootTables.mapNotNull { it.xp }
    val combatXp = entity.lootTables.mapNotNull { it.combatXp }
    entityStatRange("Level", levels)?.let(::add)
    entityStatRange("XP", xp)?.let(::add)
    entityStatRange("Combat XP", combatXp)?.let(::add)
    entity.details.filterNot { it == entity.location }.distinct().forEach { add("§7$it") }
}

private fun entityStatRange(label: String, values: List<Int>): String? {
    val distinct = values.distinct().sorted()
    if (distinct.isEmpty()) return null
    val formatted = if (distinct.size == 1) {
        ItemListFormatting.number(distinct.single().toLong())
    } else {
        "${ItemListFormatting.number(distinct.first().toLong())}–${ItemListFormatting.number(distinct.last().toLong())}"
    }
    return "§7$label: §f$formatted"
}

private fun dropChanceLines(entity: SkyBlockEntityInfo, sources: List<SkyBlockDropSource>): List<String> {
    val known = sources.filter { it.chance != null }
        .distinctBy { it.sourceName to it.chance }
    if (known.size == 1) {
        val source = known.single()
        val label = if (source.details.any { it.contains("Pocket Black Hole", ignoreCase = true) }) {
            "Pocket Black Hole chance"
        } else {
            "Drop chance"
        }
        return listOf("§7$label: §f${formatDropChance(requireNotNull(source.chance))}")
    }
    return known.map { source ->
        val name = source.sourceName?.takeUnless { it.equals(entity.name, ignoreCase = true) } ?: "Drop"
        "§7$name chance: §f${formatDropChance(requireNotNull(source.chance))}"
    }
}

internal fun derivedEntityDetails(entityId: String, entity: SkyBlockEntityInfo): List<String> = when {
    SkyBlockSlayerType.fromBossEntityId(entityId) != null -> listOf(slayerSpawnDescription(entityId))
    entity.type.equals("Sea Creature", ignoreCase = true) -> listOf("Caught while fishing")
    entity.type.contains("Pest", ignoreCase = true) -> listOf("Found in the Garden")
    entity.type.contains("Mythological", ignoreCase = true) -> listOf("Found during the Mythological Ritual")
    else -> listOf("Source location unknown")
}

internal fun formatDropChance(chance: Double): String {
    val percent = chance * PERCENT_MULTIPLIER
    val decimals = when {
        percent >= WHOLE_PERCENT_THRESHOLD -> WHOLE_PERCENT_DECIMALS
        percent >= SMALL_PERCENT_THRESHOLD -> SMALL_PERCENT_DECIMALS
        else -> TINY_PERCENT_DECIMALS
    }
    return "% .${decimals}f%%".format(java.util.Locale.ROOT, percent).trim().trimStart('0')
}

private fun slayerSpawnDescription(entityId: String): String {
    val (type, tier) = requireNotNull(SkyBlockSlayerType.fromBossEntityId(entityId))
    return "Spawned from a Tier $tier ${type.displayName} Slayer quest"
}

internal fun itemListEntityAt(
    mode: ItemListViewMode,
    infoPanel: ItemListInfoPanel,
    entityBounds: List<Pair<Rect, String>>,
    mouseX: Int,
    mouseY: Int,
): String? = (if (mode == ItemListViewMode.INFO) infoPanel.entityAt(mouseX, mouseY) else null)
    ?: entityBounds.firstOrNull { (bounds, _) -> bounds.contains(mouseX, mouseY) }?.second

internal fun SkyBlockEntityInfo.canNavigateToEntity(): Boolean =
    SkyBlockDataRepository.ViewerData.bestWarpFor(id) != null &&
        (position != null || island != com.skysoft.data.hypixel.HypixelLocationState.currentIsland)

private val ENTITY_SLOT_BORDER = 0xFF111315.toInt()
private val ENTITY_SLOT_FILL = 0xD0202428.toInt()
private val ENTITY_MISSING_COLOR = 0xFFFF5555.toInt()
private const val MISSING_TEXT_Y_OFFSET = 5
private const val PERCENT_MULTIPLIER = 100.0
private const val WHOLE_PERCENT_THRESHOLD = 1.0
private const val SMALL_PERCENT_THRESHOLD = 0.01
private const val WHOLE_PERCENT_DECIMALS = 2
private const val SMALL_PERCENT_DECIMALS = 4
private const val TINY_PERCENT_DECIMALS = 6
