package com.skysoft.features.foraging

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Display
import net.minecraft.world.item.Items

object FloorDropHighlighter {
    private val config get() = SkysoftConfigGui.config().foraging
    private var floorDropLevel: ClientLevel? = null
    private var floorDropBlocks = emptySet<BlockPos>()

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Floor Drop Highlighter discovery",
            isActive = { isEnabled() || floorDropLevel != null },
        ) { minecraft -> updateFloorDrops(minecraft) }
        SkysoftClientEvents.onDisconnect("Floor Drop Highlighter disconnect reset", ::clearFloorDrops)
        WorldRenderDispatcher.registerHandler("Floor Drop Highlighter rendering", ::isEnabled, ::render)
    }

    private fun updateFloorDrops(minecraft: Minecraft) {
        val level = minecraft.level
        if (!isEnabled() || level == null) {
            clearFloorDrops()
            return
        }
        floorDropLevel = level
        val stringDisplayBlocks = ClientEntitySnapshot.entities().asSequence()
            .filterIsInstance<Display.ItemDisplay>()
            .filter { display -> display.isAlive && display.itemStack.item == Items.STRING }
            .map { display -> display.blockPosition() }
            .asIterable()
        floorDropBlocks = findFloorDropBlocks(stringDisplayBlocks)
    }

    private fun clearFloorDrops() {
        floorDropLevel = null
        floorDropBlocks = emptySet()
    }

    private fun render(context: SkysoftRenderContext) {
        if (Minecraft.getInstance().level !== floorDropLevel) return
        floorDropBlocks.forEach { block ->
            BlockHighlightRenderer.drawBlock(
                context,
                block.toWorldVec(),
                outlineColor = OUTLINE_COLOR,
                fillColor = FILL_COLOR,
                depth = true,
            )
        }
    }

    private fun isEnabled(): Boolean =
        config.highlightFloorDrops &&
            HypixelLocationState.inSkyBlock &&
            HypixelLocationState.currentIsland in FLOOR_DROP_ISLANDS
}

internal fun findFloorDropBlocks(stringDisplayBlocks: Iterable<BlockPos>): Set<BlockPos> =
    stringDisplayBlocks.groupingBy { block -> block }.eachCount()
        .filterValues { count -> count == FLOOR_DROP_DISPLAY_COUNT }
        .keys

private val FLOOR_DROP_ISLANDS = setOf(
    SkyBlockIsland.GALATEA,
    SkyBlockIsland.TORRHUS_CANYON,
    SkyBlockIsland.SAFARI,
)
private const val FLOOR_DROP_DISPLAY_COUNT = 3
private val OUTLINE_COLOR = Color(85, 255, 85, 204)
private val FILL_COLOR = Color(85, 255, 85, 40)
