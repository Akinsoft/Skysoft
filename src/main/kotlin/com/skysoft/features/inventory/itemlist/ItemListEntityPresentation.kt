package com.skysoft.features.inventory.itemlist

import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockDropSource
import com.skysoft.data.skyblock.SkyBlockEntityInfo
import com.skysoft.data.skyblock.SkyBlockEntityStacks
import com.skysoft.data.skyblock.SkyBlockSlayerType
import com.skysoft.data.skyblock.isMob
import com.skysoft.gui.tooltip.SkysoftNativeTooltip
import com.skysoft.utils.gui.Rect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

internal fun renderEntityIcon(
    context: GuiGraphicsExtractor,
    font: Font,
    bounds: Rect,
    entityId: String,
    dropSources: List<SkyBlockDropSource> = emptyList(),
    mouseX: Int,
    mouseY: Int,
) {
    context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, ItemListSlotStyle.BORDER)
    context.fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, ItemListSlotStyle.FILL)
    val entity = SkyBlockDataRepository.entity(entityId)
    val stack = entity?.let(SkyBlockEntityStacks::stack)
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

private val ENTITY_MISSING_COLOR = 0xFFFF5555.toInt()
private const val MISSING_TEXT_Y_OFFSET = 5
private const val PERCENT_MULTIPLIER = 100.0
private const val WHOLE_PERCENT_THRESHOLD = 1.0
private const val SMALL_PERCENT_THRESHOLD = 0.01
private const val WHOLE_PERCENT_DECIMALS = 2
private const val SMALL_PERCENT_DECIMALS = 4
private const val TINY_PERCENT_DECIMALS = 6
