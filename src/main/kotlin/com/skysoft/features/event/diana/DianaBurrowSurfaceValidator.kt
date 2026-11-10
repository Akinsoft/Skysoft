package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec
import com.skysoft.utils.toBlockPos
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

internal enum class DianaBurrowSurfaceStatus {
    UNLOADED,
    VALID,
    INVALID,
}

internal object DianaBurrowSurfaceValidator {
    fun check(location: WorldVec): DianaBurrowSurfaceStatus {
        val level = Minecraft.getInstance().level ?: return DianaBurrowSurfaceStatus.UNLOADED
        return check(level, location.toBlockPos())
    }

    fun check(level: ClientLevel, blockPos: BlockPos): DianaBurrowSurfaceStatus {
        val above = blockPos.above()
        val secondAbove = blockPos.above(2)
        if (!level.isLoaded(blockPos) || !level.isLoaded(above) || !level.isLoaded(secondAbove)) {
            return DianaBurrowSurfaceStatus.UNLOADED
        }

        val block = level.getBlockState(blockPos)
        val aboveBlock = level.getBlockState(above)
        val secondAboveBlock = level.getBlockState(secondAbove)
        return if (isValidSurface(block, aboveBlock, secondAboveBlock)) {
            DianaBurrowSurfaceStatus.VALID
        } else {
            DianaBurrowSurfaceStatus.INVALID
        }
    }

    fun isValid(level: ClientLevel, blockPos: BlockPos): Boolean =
        check(level, blockPos) == DianaBurrowSurfaceStatus.VALID

    private fun isValidSurface(block: BlockState, above: BlockState, secondAbove: BlockState): Boolean =
        block.isGrassSurface() && above.isAir && secondAbove.isAir

    private fun BlockState.isGrassSurface(): Boolean =
        `is`(Blocks.GRASS_BLOCK) && fluidState.isEmpty
}
