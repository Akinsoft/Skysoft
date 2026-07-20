package com.skysoft.features.hunting

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.utils.ColorUtilities.addAlpha
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.EntityUtilities.isVisibleToPlayer
import com.skysoft.utils.getWorldVec
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.SkysoftClientEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color
import kotlin.math.hypot

object LotumHelper {
    private val config get() = SkysoftConfigGui.config().hunting.lotumHelper

    private const val LOTUM_NAME = "Lotum"
    private const val LOTUM_NAME_TAG_RANGE = 6.0
    private const val LOTUM_FROG_HORIZONTAL_RANGE = 1.5
    private const val LOTUM_SCAN_INTERVAL_TICKS = 5
    private const val LOTUM_HIGHLIGHT_ALPHA = 80
    private val LOTUM_COLOR = Color(85, 255, 85)

    private val trackedLotums = mutableSetOf<ArmorStand>()
    private val highlightedLotums = mutableSetOf<Int>()
    private var ticks = 0

    fun register() {
        WorldRenderDispatcher.registerHandler("Lotum Helper world rendering", ::isActive, ::onRenderWorld)

        EntityInteractionEvents.register("Lotum Helper entity interaction", ::isActive) { event ->
            trackedLotums += event.clickedEntity.findLotumNameTag() ?: return@register false
            false
        }

        SkysoftClientEvents.onEndTick(
            "Lotum Helper tick",
            isActive = { isActive() || trackedLotums.isNotEmpty() || highlightedLotums.isNotEmpty() },
        ) tick@{
            if (!config.enabled || !SkyBlockIsland.LOTUS_ATOLL.isInIsland()) {
                clear()
                return@tick
            }
            if (!config.settings.highlightLotums) {
                highlightedLotums.clear()
                return@tick
            }
            if (++ticks % LOTUM_SCAN_INTERVAL_TICKS != 0) return@tick

            removeInvalidLotums()
            val entities = allEntities()
            val frogs = entities.filterIsInstance<Frog>()
            val nameTags = entities.filterIsInstance<ArmorStand>().filter { armorStand -> armorStand.isLotumName() }
            val liveNameTags = nameTags.filter { armorStand -> armorStand.isAlive }
            val confirmedLotumIds = frogs
                .filter { frog -> frog.isConfirmedLotum(liveNameTags, frogs) }
                .mapTo(mutableSetOf()) { frog -> frog.id }
            highlightedLotums.removeIf { lotumId -> lotumId !in confirmedLotumIds }

            nameTags
                .mapNotNull { armorStand -> armorStand.findLotumFrog(frogs) }
                .forEach(::highlightLotum)
        }
    }

    fun onRenderWorld(context: SkysoftRenderContext) {
        if (!config.enabled || !SkyBlockIsland.LOTUS_ATOLL.isInIsland()) {
            trackedLotums.clear()
            return
        }
        removeInvalidLotums()

        val player = Minecraft.getInstance().player ?: return
        val closestLotum = trackedLotums
            .filter { lotum -> lotum.isVisibleToPlayer() }
            .minByOrNull { lotum -> lotum.distanceToSqr(player) }
            ?: return

        context.drawLineToCrosshair(
            closestLotum.getWorldVec(),
            LOTUM_COLOR,
            depth = true,
        )
    }

    private fun highlightLotum(lotum: Frog) {
        if (!highlightedLotums.add(lotum.id)) return
        EntityHighlightRenderer.setEntityColor(lotum, LOTUM_COLOR.addAlpha(LOTUM_HIGHLIGHT_ALPHA)) {
            config.enabled && config.settings.highlightLotums && SkyBlockIsland.LOTUS_ATOLL.isInIsland() && lotum.isAlive
        }
    }

    private fun isActive(): Boolean = config.enabled && SkyBlockIsland.LOTUS_ATOLL.isInIsland()

    private fun removeInvalidLotums() = trackedLotums.removeIf { !it.isAlive || !it.isLotumName() }

    private fun Entity.findLotumNameTag(): ArmorStand? =
        (this as? ArmorStand)?.takeIf { it.isLotumName() }
            ?: nearbyLotumNameTag(allEntities().filterIsInstance<ArmorStand>())

    private fun Frog.isConfirmedLotum(nameTags: List<ArmorStand>, frogs: List<Frog>): Boolean =
        isAlive && nearbyLotumNameTag(nameTags)?.findLotumFrog(frogs) == this

    private fun Entity.nearbyLotumNameTag(nameTags: Iterable<ArmorStand>): ArmorStand? = nameTags
        .filter { it.isAlive && it.isLotumName() && it.distanceTo(this) <= LOTUM_NAME_TAG_RANGE }
        .minByOrNull { it.distanceToSqr(this) }

    private fun ArmorStand.findLotumFrog(frogs: Iterable<Frog>): Frog? {
        val nameTagPosition = position()
        return frogs.filter { frog ->
            val frogPosition = frog.position()
            frog.isAlive &&
                frogPosition.y < nameTagPosition.y &&
                frogPosition.distanceTo(nameTagPosition) <= LOTUM_NAME_TAG_RANGE &&
                hypot(frogPosition.x - nameTagPosition.x, frogPosition.z - nameTagPosition.z) <
                LOTUM_FROG_HORIZONTAL_RANGE
        }
            .minByOrNull { it.distanceToSqr(this) }
    }

    private fun Entity.isLotumName(): Boolean = cleanName().contains(LOTUM_NAME)

    private fun allEntities(): List<Entity> = Minecraft.getInstance().level
        ?.entitiesForRendering()
        ?.toList()
        .orEmpty()

    private fun clear() {
        trackedLotums.clear()
        highlightedLotums.clear()
    }
}
