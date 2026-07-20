package com.skysoft.features.combat

import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldItemBadgeRenderer
import java.util.UUID
import kotlin.math.sqrt
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

internal object BetterShurikenReminderMarkers {
    val hasState: Boolean
        get() = mobs.isNotEmpty()

    fun update(clientTick: Int, entities: List<Entity>, labels: Collection<String>) {
        if (clientTick != 1 && clientTick % SCAN_INTERVAL_TICKS != 0) return
        mobs.clear()
        SkyBlockMobEntityMatcher.visibleSignals(labels, entities).forEach { signal ->
            val entity = signal.entity?.takeIf { mob -> mob.isShurikenTarget } ?: return@forEach
            mobs[entity.uuid] = entity
        }
    }

    fun render(context: SkysoftRenderContext, stack: ItemStack, appliedMobs: Set<UUID>) {
        mobs.values.asSequence()
            .filter { mob -> mob.isShurikenTarget && mob.uuid !in appliedMobs }
            .forEach { mob -> drawShurikenBadge(context, mob, stack, REMINDER_BADGE) }
    }

    fun remove(uuid: UUID) {
        mobs.remove(uuid)
    }

    fun clear() {
        mobs.clear()
    }

    private val mobs = mutableMapOf<UUID, LivingEntity>()
    private val REMINDER_BADGE = Component.literal("✖").withStyle { style ->
        style.withColor(TextColor.fromRgb(REMINDER_BADGE_COLOR)).withBold(true)
    }
    private const val REMINDER_BADGE_COLOR = 0xFF5555
    private const val SCAN_INTERVAL_TICKS = 5
}

internal val LivingEntity.isShurikenTarget: Boolean
    get() = isAlive && this !is ArmorStand && this !is Player

internal fun drawShurikenBadge(
    context: SkysoftRenderContext,
    mob: LivingEntity,
    stack: ItemStack,
    badge: Component,
) {
    if (!mob.isVisibleToPlayer()) return
    val position = mob.getPosition(context.partialTicks)
    val bounds = mob.boundingBox
    val horizontalRadius = sqrt(bounds.xsize * bounds.xsize + bounds.zsize * bounds.zsize) / RADIUS_DIVISOR
    WorldItemBadgeRenderer.draw(
        context,
        WorldVec(position.x, position.y + MARKER_HEIGHT, position.z),
        stack,
        badge,
        cameraOffset = horizontalRadius + MARKER_CLEARANCE,
    )
}

private const val RADIUS_DIVISOR = 2.0
private const val MARKER_HEIGHT = 0.22
private const val MARKER_CLEARANCE = 0.75
