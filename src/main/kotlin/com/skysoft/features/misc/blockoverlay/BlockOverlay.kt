package com.skysoft.features.misc.blockoverlay

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.misc.conditions.FeatureConditionState
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.render.BlockHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

object BlockOverlay {
    private var pendingTarget: BlockOverlayTarget? = null
    private val conditions = FeatureConditionState()

    fun register() {
        conditions.startSession(config.settings.combinations)
        SkyBlockEventState.registerConsumer("Block Overlay") { config.enabled }
        WorldRenderDispatcher.registerHandler(
            "Block Overlay world rendering",
            isActive = { pendingTarget != null },
            handler = ::renderWorld,
        )
    }

    @JvmStatic
    fun selectBlockOutline(vanillaEligible: Boolean): BlockOutlineSelection {
        pendingTarget = null
        if (!vanillaEligible) return BlockOutlineSelection.NONE
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val cameraEntity = minecraft.cameraEntity
        val target = minecraft.hitResult as? BlockHitResult
        if (!config.enabled || level == null || cameraEntity == null || target == null || target.type != HitResult.Type.BLOCK) {
            return BlockOutlineSelection.VANILLA
        }
        val blockState = level.getBlockState(target.blockPos)
        if (blockState.isAir || !isFeatureActivationAllowed(minecraft.player?.mainHandItem?.skyBlockId())) {
            return BlockOutlineSelection.VANILLA
        }
        val shape = blockState.getShape(level, target.blockPos, CollisionContext.of(cameraEntity))
        if (shape.isEmpty) return BlockOutlineSelection.VANILLA
        pendingTarget = BlockOverlayTarget(target.blockPos, shape)
        return BlockOutlineSelection.CUSTOM
    }

    fun addHeldItem(source: FabricClientCommandSource): Int =
        conditions.addHeldItem(source, "Block Overlay", config.settings.combinations)

    private fun isFeatureActivationAllowed(heldItemId: String?): Boolean {
        val settings = config.settings
        return conditions.isActivationAllowed(
            settings.combinations,
            heldItemId,
            settings.isConditionActivationReversed,
        )
    }

    internal fun itemConditions() = conditions.itemConditions()

    internal fun markConditionsChanged() = conditions.markChanged()

    private fun renderWorld(context: SkysoftRenderContext) {
        val target = pendingTarget ?: return
        pendingTarget = null
        BlockHighlightRenderer.drawOverlay(context, target.position.toWorldVec(), target.shape, config.details.color.get().toColor())
    }

    private val config
        get() = SkysoftConfigGui.config().world.blockOverlay
}

enum class BlockOutlineSelection(val rendersVanilla: Boolean) {
    NONE(false),
    VANILLA(true),
    CUSTOM(false),
}

private data class BlockOverlayTarget(
    val position: BlockPos,
    val shape: VoxelShape,
)
