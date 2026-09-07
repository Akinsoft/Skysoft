package com.skysoft.features.foraging

import com.skysoft.data.SkyBlockIsland
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

internal object ThrowingAxeTrees {
    fun connectedLogs(level: ClientLevel, start: BlockPos): List<BlockPos> {
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

    fun section(kind: TreeKind, target: BlockPos, positions: Set<BlockPos>): TreeSection {
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

    fun hasWrongStylePenalty(
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

    fun toughness(kind: TreeKind, section: TreeSection): Double = when (kind) {
        TreeKind.PARK -> 0.0
        TreeKind.FIG -> if (section == TreeSection.BRANCH) FIG_BRANCH_TOUGHNESS else FIG_TRUNK_TOUGHNESS
        TreeKind.MANGROVE -> if (section == TreeSection.BRANCH) {
            MANGROVE_BRANCH_TOUGHNESS
        } else {
            MANGROVE_TRUNK_TOUGHNESS
        }
        TreeKind.HELIX_BEIGE, TreeKind.HELIX_RED -> HELIX_TOUGHNESS
    }

    fun treeKind(island: SkyBlockIsland, state: BlockState): TreeKind? = when (island) {
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

    private const val FIG_TRUNK_TOUGHNESS = 10.0
    private const val FIG_BRANCH_TOUGHNESS = 5.0
    private const val MANGROVE_TRUNK_TOUGHNESS = 50.0
    private const val MANGROVE_BRANCH_TOUGHNESS = 25.0
    private const val HELIX_TOUGHNESS = 200.0
    private const val CAPTURE_RADIUS = 24
    private const val MAX_CONNECTED_BLOCKS = 4_096
    private const val HELIX_PAIR_DISTANCE = 2
    private const val FIG_BRANCH_HEIGHT = 0.65
    private const val MANGROVE_BRANCH_HEIGHT = 0.72
    private const val MANGROVE_ROOT_HEIGHT = 0.45
    private const val BRANCH_VERTICAL_RUN = 2
}

internal enum class TreeKind {
    PARK,
    FIG,
    MANGROVE,
    HELIX_BEIGE,
    HELIX_RED,
}

internal enum class TreeSection {
    TRUNK,
    BRANCH,
    ROOT,
    BEIGE,
    RED,
}
