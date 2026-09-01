package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.StoredPetData
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.AttributeShardCatalog
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockItemUtilities.extraAttributes
import com.skysoft.data.skyblock.SkyBlockItemUtilities.loreLines
import com.skysoft.data.skyblock.SkyBlockItemUtilities.skyBlockEnchantments
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.events.particle.ClientParticleEvents
import com.skysoft.features.pets.ActivePetTracker
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object ThrowingAxeHelper {
    private var highlights = HighlightSnapshot()
    private val throwTracker = ThrowingAxeThrowTracker()
    private var sweep: Double? = null

    fun register() {
        TabListApi.onChange(
            "Throwing Axe Helper",
            isActive = { config.enabled },
            listener = ::updateSweep,
        )
        ClientParticleEvents.register(
            "Throwing Axe Helper throw confirmation",
            isActive = { config.enabled && config.settings.highlightThrownLogs },
        ) { event ->
            Minecraft.getInstance().level?.let { level -> throwTracker.confirm(event, level) }
            false
        }
        SkysoftClientEvents.onEndTick(
            "Throwing Axe Helper update",
            isActive = { config.enabled },
            action = ::update,
        )
        SkysoftClientEvents.onEndTick(
            "Throwing Axe Helper aim tracking",
            isActive = { config.enabled && config.settings.highlightThrownLogs },
            action = ::recordAim,
        )
        WorldRenderDispatcher.registerHandler(
            "Throwing Axe Helper rendering",
            isActive = {
                config.enabled && (
                    highlights.primary.isNotEmpty() || highlights.possible.isNotEmpty() ||
                        config.settings.highlightThrownLogs && throwTracker.isNotEmpty
                    )
            },
            handler = ::render,
        )
        SkysoftClientEvents.onDisconnect("Throwing Axe Helper reset", throwTracker::clear)
    }

    private fun update(minecraft: Minecraft) {
        val island = HypixelLocationState.currentIsland
        val level = minecraft.level
        val player = minecraft.player
        if (island == null || island !in FORAGING_ISLANDS || level == null || player == null) {
            highlights = HighlightSnapshot()
            throwTracker.clear()
            return
        }
        throwTracker.update(level, config.settings.highlightThrownLogs)
        val axe = player.mainHandItem
        if (!axe.isThrowingAxe() || player.cooldowns.isOnCooldown(axe)) {
            highlights = HighlightSnapshot()
            return
        }

        val baseSweep = sweep ?: run {
            highlights = HighlightSnapshot()
            return
        }
        val target = aimedLog(level, island, player.eyePosition, player.lookAngle)
        val kind = target?.let { treeKind(island, level.getBlockState(it)) }
        if (target == null || kind == null) {
            highlights = HighlightSnapshot()
            return
        }
        val connected = connectedLogs(level, target)
        if (connected.isEmpty()) {
            highlights = HighlightSnapshot()
            return
        }

        val positions = connected.toSet()
        val section = section(kind, target, positions)
        val activePet = ActivePetTracker.currentPet
        val effectiveSweep = sharpenedSweep(baseSweep + currentStronkArmSweep(activePet), kind)
        val toughness = toughness(kind, section)
        val wrongStyle = hasWrongStylePenalty(level, kind, section, connected, positions)
        val missile = axe.extraAttributes()?.skyBlockEnchantments()?.get("ultimate_missile") ?: 0
        val throwMultiplier = throwMultiplier(
            missile,
            activePet?.petInternalName?.substringBefore(';') == PRECURSOR_DRONE,
        )
        val extraLogs = sweepExtraLogs(effectiveSweep, toughness) * throwMultiplier * if (wrongStyle) {
            WRONG_STYLE_MULTIPLIER
        } else {
            1.0
        }
        val chopLevel = AttributeShardCatalog.getActiveLevelByAbilityName(CHOP)
        val primaryCount = predictedPrimaryLogCount(extraLogs, chopLevel)
        val possibleCount = maxOf(
            if (chopLevel in 1 until MAX_ATTRIBUTE_LEVEL) 1 else 0,
            if (kind == TreeKind.FIG || kind == TreeKind.MANGROVE) TREE_SECTION_UNCERTAINTY else 0,
        )
        highlights = HighlightSnapshot(
            primary = connected.take(primaryCount),
            possible = connected.drop(primaryCount).take(possibleCount),
            expectedBlock = level.getBlockState(target).block,
        )
    }

    private fun recordAim(minecraft: Minecraft) {
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        if (HypixelLocationState.currentIsland !in FORAGING_ISLANDS || !player.mainHandItem.isThrowingAxe()) return
        throwTracker.record(
            recordedTick = level.gameTime,
            firstParticle = throwingAxePosition(player.eyePosition, player.lookAngle, tick = 1).toWorldVec(),
            blocks = highlights.primary + highlights.possible,
            expectedBlock = highlights.expectedBlock,
        )
    }

    private fun updateSweep() {
        sweep = TabListApi.skyBlockLines.asSequence()
            .map { it.cleanSkyBlockText() }
            .firstOrNull { it.startsWith(SWEEP_PREFIX) }
            ?.substringAfter(SWEEP_PREFIX)
            ?.replace(",", "")
            ?.let { SWEEP_VALUE.find(it)?.value?.toDoubleOrNull() }
    }

    private fun aimedLog(level: ClientLevel, island: SkyBlockIsland, eye: Vec3, look: Vec3): BlockPos? {
        for (tick in 1..MAX_AXE_FLIGHT_TICKS) {
            val position = BlockPos.containing(throwingAxePosition(eye, look, tick))
            val state = level.getBlockState(position)
            if (treeKind(island, state) != null) return position
            if (!isThrowingAxePassable(level, position, state)) return null
        }
        return null
    }

    private fun connectedLogs(level: ClientLevel, start: BlockPos): List<BlockPos> {
        val targetBlock = level.getBlockState(start).block
        val queue = ArrayDeque<BlockPos>()
        val visited = mutableSetOf(start)
        val result = mutableListOf<BlockPos>()
        queue += start
        while (queue.isNotEmpty() && result.size < MAX_CONNECTED_BLOCKS) {
            val position = queue.removeFirst()
            if (level.getBlockState(position).block != targetBlock) continue
            result += position
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) continue
                        val neighbor = position.offset(x, y, z)
                        if (neighbor.isInsideCapture(start) && visited.add(neighbor)) queue += neighbor
                    }
                }
            }
        }
        return result
    }

    private fun BlockPos.isInsideCapture(origin: BlockPos): Boolean =
        kotlin.math.abs(x - origin.x) <= CAPTURE_RADIUS &&
            kotlin.math.abs(y - origin.y) <= CAPTURE_RADIUS &&
            kotlin.math.abs(z - origin.z) <= CAPTURE_RADIUS

    private fun section(kind: TreeKind, target: BlockPos, positions: Set<BlockPos>): TreeSection {
        if (kind == TreeKind.HELIX_BEIGE) return TreeSection.BEIGE
        if (kind == TreeKind.HELIX_RED) return TreeSection.RED
        if (kind == TreeKind.PARK) return TreeSection.TRUNK
        val minimumY = positions.minOf(BlockPos::getY)
        val height = positions.maxOf(BlockPos::getY) - minimumY
        val normalizedHeight = if (height == 0) 0.0 else (target.y - minimumY).toDouble() / height
        val verticalRun = verticalRun(target, positions)
        return when (kind) {
            TreeKind.FIG -> if (normalizedHeight >= FIG_BRANCH_HEIGHT && verticalRun <= BRANCH_VERTICAL_RUN) {
                TreeSection.BRANCH
            } else {
                TreeSection.TRUNK
            }
            TreeKind.MANGROVE -> when {
                normalizedHeight <= MANGROVE_ROOT_HEIGHT -> TreeSection.ROOT
                normalizedHeight >= MANGROVE_BRANCH_HEIGHT && verticalRun <= BRANCH_VERTICAL_RUN -> TreeSection.BRANCH
                else -> TreeSection.TRUNK
            }
            TreeKind.PARK, TreeKind.HELIX_BEIGE, TreeKind.HELIX_RED -> error("Handled above")
        }
    }

    private fun verticalRun(position: BlockPos, positions: Set<BlockPos>): Int {
        var minimumY = position.y
        while (position.atY(minimumY - 1) in positions) minimumY--
        var maximumY = position.y
        while (position.atY(maximumY + 1) in positions) maximumY++
        return maximumY - minimumY + 1
    }

    private fun hasWrongStylePenalty(
        level: ClientLevel,
        kind: TreeKind,
        targetSection: TreeSection,
        connected: List<BlockPos>,
        positions: Set<BlockPos>,
    ): Boolean = when (kind) {
        TreeKind.FIG ->
            targetSection == TreeSection.BRANCH &&
                connected.any { section(kind, it, positions) == TreeSection.TRUNK }
        TreeKind.MANGROVE ->
            when (targetSection) {
                TreeSection.BRANCH -> false
                TreeSection.TRUNK -> connected.any { section(kind, it, positions) == TreeSection.BRANCH }
                TreeSection.ROOT -> connected.any { section(kind, it, positions) != TreeSection.ROOT }
                else -> false
            }
        TreeKind.HELIX_RED -> connected.any { position ->
            BlockPos.betweenClosed(
                position.offset(-HELIX_PAIR_DISTANCE, -HELIX_PAIR_DISTANCE, -HELIX_PAIR_DISTANCE),
                position.offset(HELIX_PAIR_DISTANCE, HELIX_PAIR_DISTANCE, HELIX_PAIR_DISTANCE),
            ).any { treeKind(SkyBlockIsland.TORRHUS_CANYON, level.getBlockState(it)) == TreeKind.HELIX_BEIGE }
        }
        else -> false
    }

    private fun currentStronkArmSweep(pet: StoredPetData?): Double =
        if (pet?.petInternalName?.substringBefore(';') == SLOTH) stronkArmSweep(pet.rarity, pet.level) else 0.0

    private fun sharpenedSweep(baseSweep: Double, kind: TreeKind): Double {
        val sharpeningLevel = when (kind) {
            TreeKind.FIG -> AttributeShardCatalog.getActiveLevelByAbilityName(FIG_SHARPENING)
            TreeKind.MANGROVE -> AttributeShardCatalog.getActiveLevelByAbilityName(MANGROVE_SHARPENING)
            else -> 0
        }
        val sweepPerLevel = when (kind) {
            TreeKind.FIG -> FIG_SHARPENING_PER_LEVEL
            TreeKind.MANGROVE -> MANGROVE_SHARPENING_PER_LEVEL
            else -> 0.0
        }
        val echoLevel = AttributeShardCatalog.getActiveLevelByAbilityName(ECHO_OF_SHARPENING)
        return baseSweep + sharpeningLevel * sweepPerLevel * (1.0 + echoLevel * ECHO_BONUS_PER_LEVEL)
    }

    private fun toughness(kind: TreeKind, section: TreeSection): Double = when (kind) {
        TreeKind.PARK -> 0.0
        TreeKind.FIG -> if (section == TreeSection.BRANCH) FIG_BRANCH_TOUGHNESS else FIG_TRUNK_TOUGHNESS
        TreeKind.MANGROVE -> if (section == TreeSection.BRANCH) {
            MANGROVE_BRANCH_TOUGHNESS
        } else {
            MANGROVE_TRUNK_TOUGHNESS
        }
        TreeKind.HELIX_BEIGE, TreeKind.HELIX_RED -> HELIX_TOUGHNESS
    }

    internal fun isTreeBlock(island: SkyBlockIsland, state: BlockState): Boolean = treeKind(island, state) != null

    private fun treeKind(island: SkyBlockIsland, state: BlockState): TreeKind? = when (island) {
        SkyBlockIsland.THE_PARK -> TreeKind.PARK.takeIf { state.`is`(BlockTags.LOGS) }
        SkyBlockIsland.GALATEA -> when (state.block) {
            Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD -> TreeKind.FIG
            Blocks.MANGROVE_LOG, Blocks.MANGROVE_WOOD -> TreeKind.MANGROVE
            else -> null
        }
        SkyBlockIsland.TORRHUS_CANYON -> when (state.block) {
            Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD -> TreeKind.HELIX_BEIGE
            Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD -> TreeKind.HELIX_RED
            else -> null
        }
        else -> null
    }

    private fun render(context: SkysoftRenderContext) {
        val settings = config.settings
        val details = config.details
        val thrown = if (settings.highlightThrownLogs) throwTracker.positions else emptySet()
        val overlapping = if (settings.highlightOverlappingLogs) {
            thrown.intersect(highlights.primary + highlights.possible)
        } else {
            emptySet()
        }
        val highlightColor = details.highlightColor.get().toColor()
        val possibleColor = details.possibleColor.get().toColor()
        highlights.primary.filterNot { it in thrown }.forEach { drawBlock(context, it, highlightColor) }
        highlights.possible.filterNot { it in thrown }.forEach { drawBlock(context, it, possibleColor) }
        val thrownColor = details.thrownLogColor.get().toColor()
        thrown.filterNot { it in overlapping }.forEach { drawBlock(context, it, thrownColor) }
        overlapping.forEach { drawBlock(context, it, OVERLAP_COLOR) }
    }

    private fun drawBlock(context: SkysoftRenderContext, position: BlockPos, color: Color) {
        val fill = Color(color.red, color.green, color.blue, (color.alpha * FILL_ALPHA_SCALE).roundToInt())
        BlockHighlightRenderer.drawBlock(
            context,
            position.toWorldVec(),
            outlineColor = color,
            fillColor = fill,
            depth = true,
        )
    }

    private val config
        get() = SkysoftConfigGui.config().foraging.throwingAxeHelper

    private data class HighlightSnapshot(
        val primary: List<BlockPos> = emptyList(),
        val possible: List<BlockPos> = emptyList(),
        val expectedBlock: Block? = null,
    )

    private enum class TreeKind {
        PARK,
        FIG,
        MANGROVE,
        HELIX_BEIGE,
        HELIX_RED,
    }

    private enum class TreeSection {
        TRUNK,
        BRANCH,
        ROOT,
        BEIGE,
        RED,
    }

    private val FORAGING_ISLANDS = setOf(SkyBlockIsland.THE_PARK, SkyBlockIsland.GALATEA, SkyBlockIsland.TORRHUS_CANYON)
    private const val SWEEP_PREFIX = "Sweep:"
    private val SWEEP_VALUE = Regex("""\d+(?:\.\d+)?""")
    private const val PRECURSOR_DRONE = "PRECURSOR_DRONE"
    private const val SLOTH = "SLOTH"
    private const val CHOP = "Chop"
    private const val FIG_SHARPENING = "Fig Sharpening"
    private const val MANGROVE_SHARPENING = "Mangrove Sharpening"
    private const val ECHO_OF_SHARPENING = "Echo of Sharpening"
    private const val MAX_ATTRIBUTE_LEVEL = 10
    private const val FIG_SHARPENING_PER_LEVEL = 5.0
    private const val MANGROVE_SHARPENING_PER_LEVEL = 10.0
    private const val ECHO_BONUS_PER_LEVEL = 0.02
    private const val FIG_TRUNK_TOUGHNESS = 10.0
    private const val FIG_BRANCH_TOUGHNESS = 5.0
    private const val MANGROVE_TRUNK_TOUGHNESS = 50.0
    private const val MANGROVE_BRANCH_TOUGHNESS = 25.0
    private const val HELIX_TOUGHNESS = 200.0
    private const val WRONG_STYLE_MULTIPLIER = 0.5
    private const val MAX_AIM_DISTANCE = 50.0
    private val MAX_AXE_FLIGHT_TICKS = ceil(MAX_AIM_DISTANCE / AXE_SPEED).toInt()
    private const val CAPTURE_RADIUS = 24
    private const val MAX_CONNECTED_BLOCKS = 4_096
    private const val HELIX_PAIR_DISTANCE = 2
    private const val FIG_BRANCH_HEIGHT = 0.65
    private const val MANGROVE_BRANCH_HEIGHT = 0.72
    private const val MANGROVE_ROOT_HEIGHT = 0.45
    private const val BRANCH_VERTICAL_RUN = 2
    private const val TREE_SECTION_UNCERTAINTY = 3
    private val OVERLAP_COLOR = Color(255, 85, 85, 204)
    private const val FILL_ALPHA_SCALE = 0.2
}

private val THROWING_AXES = setOf(
    "JUNGLE_AXE",
    "TREECAPITATOR_AXE",
    "FIG_AXE",
    "FIGSTONE_AXE",
    "DECENT_AXE",
    "SERIOUSLY_DAMAGED_AXE",
    "HELIX_CHOPPER",
)
private const val THROWING_AXE_ABILITY = "Ability: Throwing Axe"

internal fun ItemStack.isThrowingAxe(): Boolean =
    skyBlockId() in THROWING_AXES || loreLines().any { it.cleanSkyBlockText().contains(THROWING_AXE_ABILITY) }

internal fun stronkArmSweep(rarity: SkyBlockRarity, level: Int): Double {
    val sweepPerLevel = when (rarity) {
        SkyBlockRarity.RARE -> RARE_STRONK_ARM_SWEEP_PER_LEVEL
        SkyBlockRarity.EPIC, SkyBlockRarity.LEGENDARY -> EPIC_STRONK_ARM_SWEEP_PER_LEVEL
        else -> 0.0
    }
    return level * sweepPerLevel
}

internal fun isThrowingAxePassable(level: ClientLevel, position: BlockPos, state: BlockState): Boolean =
    state.block is LeavesBlock || state.getCollisionShape(level, position).isEmpty

internal fun throwingAxePosition(origin: Vec3, look: Vec3, tick: Int): Vec3 {
    val elapsed = tick.toDouble()
    return origin.add(
        look.x * AXE_SPEED * elapsed,
        look.y * AXE_SPEED * elapsed - AXE_GRAVITY * elapsed * (elapsed - 1.0) / 2.0,
        look.z * AXE_SPEED * elapsed,
    )
}

internal fun sweepExtraLogs(sweep: Double, toughness: Double): Double {
    if (sweep <= 0.0) return 0.0
    if (toughness <= 0.0) return sweep.coerceAtMost(MAX_EXTRA_LOGS)
    val scaledSweep = (sweep + sqrt(sweep) - toughness) / toughness.pow(TOUGHNESS_EXPONENT)
    if (scaledSweep <= 0.0) return 0.0
    return (SWEEP_LOG_MULTIPLIER * log10(1.0 + scaledSweep.pow(SWEEP_EXPONENT))).coerceAtMost(MAX_EXTRA_LOGS)
}

internal fun predictedPrimaryLogCount(extraLogs: Double, chopLevel: Int): Int =
    floor(extraLogs.coerceIn(0.0, MAX_EXTRA_LOGS)).toInt() + 1 + if (chopLevel >= MAX_CHOP_LEVEL) 1 else 0

internal fun throwMultiplier(missileLevel: Int, precursorDrone: Boolean): Double =
    if (precursorDrone) 1.0 else (BASE_THROW_MULTIPLIER + missileLevel * MISSILE_BONUS_PER_LEVEL).coerceAtMost(1.0)

private const val RARE_STRONK_ARM_SWEEP_PER_LEVEL = 0.2
private const val EPIC_STRONK_ARM_SWEEP_PER_LEVEL = 0.4
private const val AXE_SPEED = 1.2
private const val AXE_GRAVITY = 0.008
private const val MAX_EXTRA_LOGS = 35.0
private const val MAX_CHOP_LEVEL = 10
private const val TOUGHNESS_EXPONENT = 0.511
private const val SWEEP_EXPONENT = 1.9
private const val SWEEP_LOG_MULTIPLIER = 4.0
private const val BASE_THROW_MULTIPLIER = 0.5
private const val MISSILE_BONUS_PER_LEVEL = 0.1
