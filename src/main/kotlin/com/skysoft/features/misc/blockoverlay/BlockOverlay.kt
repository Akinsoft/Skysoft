package com.skysoft.features.misc.blockoverlay

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockEventState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.features.misc.conditions.FeatureConditionActivationCache
import com.skysoft.features.misc.conditions.FeatureConditionActivationKey
import com.skysoft.features.misc.conditions.FeatureConditionContext
import com.skysoft.features.misc.conditions.FeatureConditionVersion
import com.skysoft.features.misc.conditions.FeatureConditions
import com.skysoft.features.misc.conditions.FeatureItemConditionCatalogue
import com.skysoft.features.misc.conditions.FeatureItemConditionCommand
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
import java.awt.Color
import kotlin.math.roundToInt

object BlockOverlay {
    private var pendingTarget: BlockOverlayTarget? = null
    private val activationCache = FeatureConditionActivationCache()
    private val conditionVersion = FeatureConditionVersion()
    private val itemCatalogue = FeatureItemConditionCatalogue()

    fun register() {
        itemCatalogue.startSession(config.settings.combinations)
        WorldRenderDispatcher.registerHandler("Block Overlay world rendering", ::renderWorld)
    }

    @JvmStatic
    fun selectBlockOutline(vanillaEligible: Boolean): BlockOutlineSelection {
        pendingTarget = null
        val minecraft = Minecraft.getInstance()
        val rawTarget = minecraft.hitResult as? BlockHitResult
        val target = rawTarget?.takeIf {
            val blockState = minecraft.level?.getBlockState(it.blockPos)
            isRenderableBlockTarget(it.type == HitResult.Type.BLOCK, blockState == null || blockState.isAir)
        }
        val heldItemId = if (config.enabled && target != null) minecraft.player?.mainHandItem?.skyBlockId() else null
        val isActivationAllowed = config.enabled && target != null && isFeatureActivationAllowed(heldItemId)
        if (!vanillaEligible || !config.enabled || target == null || !isActivationAllowed) {
            return if (vanillaEligible) BlockOutlineSelection.VANILLA else BlockOutlineSelection.NONE
        }

        val blockTarget = requireNotNull(target)
        val level = minecraft.level
        val cameraEntity = minecraft.cameraEntity
        val shape = if (level != null && cameraEntity != null) {
            level.getBlockState(blockTarget.blockPos)
                .getShape(level, blockTarget.blockPos, CollisionContext.of(cameraEntity))
        } else {
            null
        }
        if (
            !shouldReplaceVanillaBlockOutline(
                isVanillaEligible = vanillaEligible,
                isEnabled = config.enabled,
                hasBlockTarget = true,
                isActivationAllowed = isActivationAllowed,
                hasRenderableShape = shape != null && !shape.isEmpty,
            )
        ) {
            return BlockOutlineSelection.VANILLA
        }

        pendingTarget = BlockOverlayTarget(blockTarget.blockPos, requireNotNull(shape))
        return BlockOutlineSelection.CUSTOM
    }

    fun addHeldItem(source: FabricClientCommandSource): Int {
        return FeatureItemConditionCommand.addHeldItem(
            source = source,
            featureName = "Block Overlay",
            combinations = config.settings.combinations,
            catalogue = itemCatalogue,
            onChanged = conditionVersion::markChanged,
        )
    }

    private fun isFeatureActivationAllowed(heldItemId: String?): Boolean {
        val settings = config.settings
        val key = FeatureConditionActivationKey(
            locationVersion = HypixelLocationState.locationVersion,
            eventVersion = SkyBlockEventState.version,
            rulesVersion = conditionVersion.version,
            heldItemId = heldItemId,
            isConditionActivationReversed = settings.isConditionActivationReversed,
        )
        return activationCache.isActivationAllowed(key) {
            FeatureConditions.isActivationAllowed(
                settings.combinations,
                FeatureConditionContext(
                    isInSkyBlock = HypixelLocationState.inSkyBlock,
                    island = HypixelLocationState.currentIsland,
                    activeEvents = SkyBlockEventState.activeEvents(),
                    heldItemId = heldItemId,
                ),
                isConditionActivationReversed = settings.isConditionActivationReversed,
            )
        }
    }

    internal fun itemConditions() = itemCatalogue.conditions()

    internal fun markConditionsChanged() = conditionVersion.markChanged()

    private fun renderWorld(context: SkysoftRenderContext) {
        val target = pendingTarget ?: return
        pendingTarget = null
        val color = config.details.color.get().toColor()
        val fillColor = Color(
            color.red,
            color.green,
            color.blue,
            (color.alpha * FILL_ALPHA_SCALE).roundToInt(),
        )
        BlockHighlightRenderer.drawShape(
            context,
            target.position.toWorldVec(),
            target.shape,
            color,
            fillColor,
            lineWidth = LINE_WIDTH,
            depth = true,
        )
    }

    private val config
        get() = SkysoftConfigGui.config().misc.blockOverlay

    private const val FILL_ALPHA_SCALE = 0.2
    private const val LINE_WIDTH = 3
}

enum class BlockOutlineSelection(val rendersVanilla: Boolean) {
    NONE(false),
    VANILLA(true),
    CUSTOM(false),
}

internal fun shouldReplaceVanillaBlockOutline(
    isVanillaEligible: Boolean,
    isEnabled: Boolean,
    hasBlockTarget: Boolean,
    isActivationAllowed: Boolean,
    hasRenderableShape: Boolean,
): Boolean = isVanillaEligible && isEnabled && hasBlockTarget && isActivationAllowed && hasRenderableShape

internal fun isRenderableBlockTarget(isBlockHit: Boolean, isAir: Boolean): Boolean = isBlockHit && !isAir

private data class BlockOverlayTarget(
    val position: BlockPos,
    val shape: VoxelShape,
)
