package com.skysoft.features.farming

import com.skysoft.config.NoCropRotationLocation
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.utils.MinecraftRenderer
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object NoCropRotation {
    @Volatile
    private var active = false

    private val config
        get() = SkysoftConfigGui.config().farming.noCropRotation

    fun register() {
        active = shouldBeActive()
        SkysoftClientEvents.onEndTick("No Crop Rotation state", { active != shouldBeActive() }) { minecraft ->
            active = shouldBeActive()
            MinecraftRenderer.invalidateCompiledGeometry(minecraft)
        }
    }

    @JvmStatic
    fun modelSeed(blockState: BlockState, seed: Long): Long =
        if (shouldRemoveCoordinateVariation(blockState)) FIXED_MODEL_SEED else seed

    @JvmStatic
    fun offset(blockState: BlockState, offset: Vec3): Vec3 =
        if (shouldRemoveCoordinateVariation(blockState)) Vec3.ZERO else offset

    private fun shouldRemoveCoordinateVariation(blockState: BlockState): Boolean =
        active && blockState.hasOffsetFunction()

    private fun shouldBeActive(): Boolean = config.enabled && when (config.settings.location) {
        NoCropRotationLocation.ONLY_IN_GARDEN -> SkyBlockIsland.GARDEN.isInIsland()
        NoCropRotationLocation.EVERYWHERE -> true
    }

    private const val FIXED_MODEL_SEED = 0L
}
