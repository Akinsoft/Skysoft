package com.skysoft.features.combat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.events.entity.EntityLifecycleEvents
import com.skysoft.features.pets.PetRepository
import com.skysoft.utils.WorldVec
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldItemBadgeRenderer
import com.skysoft.utils.render.WorldRenderDispatcher
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.min
import kotlin.math.sqrt

object BetterShurikens {
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        EntityLifecycleEvents.LOAD.register(::onEntityLoad)
        EntityLifecycleEvents.UNLOAD.register(::onEntityUnload)
        BetterShurikenSoundCorrelation.register { resolution ->
            resolution.replacedMobUuid?.let(taggedMobs::remove)
            taggedMobs[resolution.target.uuid] = resolution.target
        }
        WorldRenderDispatcher.registerHandler(::renderWorld)
    }

    @JvmStatic
    fun adjustNameTag(entity: Entity, state: EntityRenderState) {
        if (!isEnabled() || entity !is ArmorStand) return
        state.nameTag = state.nameTag?.withoutShurikenMarker()
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        if (!isEnabled()) return
        val stack = shurikenStack() ?: return
        taggedMobs.values.filter { mob -> mob.isShurikenTarget }.forEach { mob ->
            val position = mob.getPosition(context.partialTicks)
            WorldItemBadgeRenderer.draw(
                context,
                WorldVec(position.x, position.y + MARKER_HEIGHT, position.z),
                stack,
                APPLIED_BADGE,
                cameraOffset = mob.markerCameraOffset(),
            )
        }
    }

    private fun onTick(minecraft: Minecraft) {
        if (!isEnabled()) {
            clearRuntimeState()
            return
        }
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        if (activeLevel !== level) {
            clearRuntimeState()
            activeLevel = level
        }
        clientTick++

        updateHeldShuriken(player)
        val entities = level.entitiesForRendering().toList()
        if (detectionTicksRemaining > 0) {
            discoverShurikens(entities, player)
            detectionTicksRemaining--
            if (detectionTicksRemaining == 0) {
                pendingThrows = 0
            }
        }
        updateTrackedShurikens(entities)
        updateTaggedMobs(level, entities)
        recentItemDisplays.entries.removeIf { (uuid, display) ->
            uuid in trackedShurikens || clientTick - display.loadedTick > Detection.RECENT_DISPLAY_TICKS
        }
    }

    private fun updateHeldShuriken(player: Player) {
        val previousHeld = heldShuriken
        val retainedHeld = previousHeld?.let { held ->
            val currentStack = player.inventory.getItem(held.slot)
            val currentCount = if (currentStack.isShuriken()) currentStack.count else 0
            if (held.count - currentCount == SHURIKEN_THROW_COUNT) {
                pendingThrows++
                detectionTicksRemaining = Detection.WINDOW_TICKS
                BetterShurikenSoundCorrelation.noteThrow()
            }
            held.copy(count = currentCount)
        }

        val selectedSlot = player.inventory.selectedSlot
        val selectedStack = player.inventory.getItem(selectedSlot)
        heldShuriken = if (selectedStack.isShuriken()) {
            cachedShurikenStack = selectedStack.copyWithCount(1)
            HeldShuriken(selectedSlot, selectedStack.count, clientTick)
        } else {
            retainedHeld?.takeIf { held ->
                clientTick - held.lastSelectedTick <= Detection.ARMED_GRACE_TICKS &&
                    player.inventory.getItem(held.slot).isShuriken()
            }
        }
    }

    private fun discoverShurikens(entities: List<Entity>, player: Player) {
        if (pendingThrows == 0) return
        recentItemDisplays.values.asSequence()
            .filter { recent -> recent.display.uuid !in trackedShurikens }
            .sortedBy { recent -> recent.display.distanceToSqr(player) }
            .take(pendingThrows)
            .forEach { recent ->
                val display = recent.display
                trackedShurikens[display.uuid] = TrackedShuriken(display, display.position())
                pendingThrows--
            }
        if (pendingThrows == 0) return
        entities.asSequence()
            .filterIsInstance<Display.ItemDisplay>()
            .filter { display -> display.uuid !in trackedShurikens }
            .filter { display -> display.itemStack.isShuriken() }
            .filter { display -> display.distanceToSqr(player) <= Detection.MAX_DISCOVERY_DISTANCE_SQUARED }
            .sortedBy { display -> display.distanceToSqr(player) }
            .take(pendingThrows)
            .forEach { display ->
                trackedShurikens[display.uuid] = TrackedShuriken(display, display.position())
                pendingThrows--
            }
    }

    private fun updateTrackedShurikens(entities: List<Entity>) {
        if (trackedShurikens.isEmpty()) return
        val candidates = entities.filterIsInstance<LivingEntity>().filter { entity -> entity.isShurikenTarget }
        val iterator = trackedShurikens.values.iterator()
        while (iterator.hasNext()) {
            val tracked = iterator.next()
            val currentPosition = tracked.entity.position()
            val crossedMob = HitResolver.intersectedMob(tracked.lastPosition, currentPosition, candidates)
            if (crossedMob != null) {
                tracked.lastCrossedMob = crossedMob
            }
            tracked.lastPosition = currentPosition
            tracked.updateVelocity()
            if (tracked.entity.isRemoved) {
                resolveAndTag(tracked, currentPosition, candidates)
                iterator.remove()
            }
        }
    }

    private fun updateTaggedMobs(level: ClientLevel, entities: List<Entity>) {
        val livingEntities = entities.filterIsInstance<LivingEntity>()
        val currentMobs = livingEntities.filter { entity -> entity.isShurikenTarget }.associateBy { entity -> entity.uuid }
        taggedMobs.keys.retainAll(currentMobs.keys)
        taggedMobs.replaceAll { uuid, _ -> currentMobs.getValue(uuid) }

        entities.asSequence()
            .filterIsInstance<ArmorStand>()
            .filter { nameplate -> nameplate.hasShurikenMarker() }
            .mapNotNull { nameplate -> nameplate.supportingMob(level, livingEntities) }
            .forEach { mob ->
                taggedMobs[mob.uuid] = mob
            }
    }

    private fun onEntityLoad(entity: Entity) {
        if (!isEnabled()) return
        val display = entity as? Display.ItemDisplay ?: return
        val player = Minecraft.getInstance().player ?: return
        if (display.distanceToSqr(player) <= Detection.RECENT_DISPLAY_DISTANCE_SQUARED) {
            recentItemDisplays[display.uuid] = RecentItemDisplay(display, clientTick)
        }
        if (detectionTicksRemaining == 0 || pendingThrows == 0) return
        if (!display.itemStack.isShuriken()) return
        if (display.distanceToSqr(player) > Detection.MAX_DISCOVERY_DISTANCE_SQUARED) return
        trackedShurikens[display.uuid] = TrackedShuriken(display, display.position())
        pendingThrows--
    }

    private fun onEntityUnload(entity: Entity) {
        taggedMobs.remove(entity.uuid)
        val tracked = trackedShurikens.remove(entity.uuid) ?: return
        val candidates = activeLevel?.entitiesForRendering()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { candidate -> candidate.isShurikenTarget }
            .orEmpty()
        tracked.updateVelocity()
        resolveAndTag(tracked, entity.position(), candidates)
    }

    private fun resolveAndTag(
        tracked: TrackedShuriken,
        currentPosition: Vec3,
        candidates: List<LivingEntity>,
    ) {
        val resolution = HitResolver.resolve(tracked, currentPosition, candidates)
        resolution.mob?.let(BetterShurikenSoundCorrelation::noteProjectedImpact)
        val soundTarget = BetterShurikenSoundCorrelation.recentTarget()
        val hasSoundConfirmation = BetterShurikenSoundCorrelation.hasRecentConfirmation()
        val isConfirmed = hasSoundConfirmation || resolution.isDirectOverlap
        val hitMob = soundTarget ?: resolution.mob?.takeIf { isConfirmed }
        if (hitMob != null && hitMob.isAlive) {
            taggedMobs[hitMob.uuid] = hitMob
        }
    }

    private val LivingEntity.isShurikenTarget: Boolean
        get() = isAlive && this !is ArmorStand && this !is Player

    private fun ItemStack.isShuriken(): Boolean =
        skyBlockId() == SHURIKEN_ITEM_ID || (item == Items.NETHER_STAR && hoverName.string == SHURIKEN_ITEM_NAME)

    private fun shurikenStack(): ItemStack? {
        cachedShurikenStack?.let { return it }
        return PetRepository.itemStackOrNull(SHURIKEN_ITEM_ID)?.copyWithCount(1)?.also {
            cachedShurikenStack = it
        }
    }

    private fun ArmorStand.hasShurikenMarker(): Boolean = customName?.string?.endsWith(SHURIKEN_MARKER_SUFFIX) == true

    private fun ArmorStand.supportingMob(level: ClientLevel, livingEntities: List<LivingEntity>): LivingEntity? {
        val direct = level.getEntity(id - 1)
        if (direct is LivingEntity && direct.isShurikenTarget) return direct
        return livingEntities
            .filter { entity -> entity.isShurikenTarget }
            .filter { entity -> isSupportedBy(entity) }
            .minByOrNull { entity -> distanceToSqr(entity) }
    }

    private fun ArmorStand.isSupportedBy(entity: LivingEntity): Boolean {
        val dx = x - entity.x
        val dz = z - entity.z
        val verticalOffset = y - entity.y
        return dx * dx + dz * dz <= MAX_HORIZONTAL_OFFSET_SQUARED &&
            verticalOffset in MIN_VERTICAL_OFFSET..MAX_VERTICAL_OFFSET
    }

    private fun LivingEntity.markerCameraOffset(): Double {
        val bounds = boundingBox
        val horizontalRadius = sqrt(bounds.xsize * bounds.xsize + bounds.zsize * bounds.zsize) / 2.0
        return horizontalRadius + MARKER_CLEARANCE
    }

    private fun clearRuntimeState() {
        activeLevel = null
        clientTick = 0
        heldShuriken = null
        detectionTicksRemaining = 0
        pendingThrows = 0
        trackedShurikens.clear()
        recentItemDisplays.clear()
        taggedMobs.clear()
        BetterShurikenSoundCorrelation.clear()
    }

    private fun Component.withoutShurikenMarker(): Component {
        val fullText = string
        if (!fullText.endsWith(SHURIKEN_MARKER_SUFFIX)) return this
        var remainingCharacters = fullText.length - SHURIKEN_MARKER_SUFFIX.length
        val result = Component.empty()
        for (part in toFlatList()) {
            if (remainingCharacters <= 0) break
            val partText = part.string
            val length = min(remainingCharacters, partText.length)
            if (length > 0) {
                result.append(Component.literal(partText.take(length)).withStyle(part.style))
                remainingCharacters -= length
            }
        }
        return result
    }

    private fun isEnabled(): Boolean =
        HypixelLocationState.inSkyBlock && SkysoftConfigGui.config().combat.isBetterShurikensEnabled

    private data class HeldShuriken(
        val slot: Int,
        val count: Int,
        val lastSelectedTick: Int,
    )

    private data class TrackedShuriken(
        val entity: Display.ItemDisplay,
        var lastPosition: Vec3,
        var lastVelocity: Vec3 = entity.deltaMovement,
        var lastCrossedMob: LivingEntity? = null,
    ) {
        fun updateVelocity() {
            val velocity = entity.deltaMovement
            if (velocity.lengthSqr() > Detection.MIN_VELOCITY_SQUARED) lastVelocity = velocity
        }
    }

    private data class HitResolution(
        val mob: LivingEntity?,
        val strategy: String,
        val projectedEnd: Vec3,
        val isDirectOverlap: Boolean = false,
    )

    private object HitResolver {
        fun resolve(
            tracked: TrackedShuriken,
            currentPosition: Vec3,
            candidates: List<LivingEntity>,
        ): HitResolution {
            val projectedEnd = currentPosition.add(tracked.lastVelocity.scale(Detection.UNSEEN_FLIGHT_TICKS))
            val directMob = containingMob(currentPosition, candidates)
            if (directMob != null) return HitResolution(directMob, "direct-overlap", projectedEnd, true)

            val projectedMob = intersectedMob(currentPosition, projectedEnd, candidates)
            if (projectedMob != null) return HitResolution(projectedMob, "projected-flight", projectedEnd)

            val endpointMob = nearestEndpointMob(currentPosition, candidates)
            if (endpointMob != null) return HitResolution(endpointMob, "endpoint", projectedEnd)

            return HitResolution(tracked.lastCrossedMob, "last-crossing", projectedEnd)
        }

        private fun containingMob(location: Vec3, candidates: List<LivingEntity>): LivingEntity? =
            candidates
                .filter { mob -> mob.boundingBox.inflate(Detection.HITBOX_TOLERANCE).contains(location) }
                .minByOrNull { mob -> mob.distanceToSqr(location) }

        fun intersectedMob(start: Vec3, end: Vec3, candidates: List<LivingEntity>): LivingEntity? =
            candidates.mapNotNull { mob ->
                val bounds = mob.boundingBox.inflate(Detection.HITBOX_TOLERANCE)
                val hit = when {
                    bounds.contains(start) -> start
                    bounds.contains(end) -> end
                    else -> bounds.clip(start, end).orElse(null)
                } ?: return@mapNotNull null
                mob to start.distanceToSqr(hit)
            }
                .minByOrNull { (_, distance) -> distance }
                ?.first

        private fun nearestEndpointMob(end: Vec3, candidates: List<LivingEntity>): LivingEntity? =
            candidates.map { mob -> mob to mob.boundingBox.distanceToSqr(end) }
                .minByOrNull { (_, distance) -> distance }
                ?.takeIf { (_, distance) -> distance <= Detection.MAX_ENDPOINT_HIT_DISTANCE_SQUARED }
                ?.first
    }

    private data class RecentItemDisplay(val display: Display.ItemDisplay, val loadedTick: Int)

    private object Detection {
        const val WINDOW_TICKS = 60
        const val ARMED_GRACE_TICKS = 20
        const val RECENT_DISPLAY_TICKS = 3
        const val MAX_DISCOVERY_DISTANCE_SQUARED = 16.0 * 16.0
        const val RECENT_DISPLAY_DISTANCE_SQUARED = 6.0 * 6.0
        const val MAX_ENDPOINT_HIT_DISTANCE_SQUARED = 2.0 * 2.0
        const val HITBOX_TOLERANCE = 0.25
        const val UNSEEN_FLIGHT_TICKS = 4.0
        const val MIN_VELOCITY_SQUARED = 0.01 * 0.01
    }

    private var activeLevel: ClientLevel? = null
    private var clientTick = 0
    private var heldShuriken: HeldShuriken? = null
    private var detectionTicksRemaining = 0
    private var pendingThrows = 0
    private var cachedShurikenStack: ItemStack? = null
    private val trackedShurikens = mutableMapOf<UUID, TrackedShuriken>()
    private val recentItemDisplays = mutableMapOf<UUID, RecentItemDisplay>()
    private val taggedMobs = mutableMapOf<UUID, LivingEntity>()
    private val APPLIED_BADGE = Component.literal("✔").withStyle { style ->
        style.withColor(TextColor.fromRgb(APPLIED_BADGE_COLOR)).withBold(true)
    }
    private const val SHURIKEN_ITEM_ID = "FAKE_SHURIKEN"
    private const val SHURIKEN_ITEM_NAME = "Extremely Real Shuriken"
    private const val SHURIKEN_MARKER_SUFFIX = " ✯"
    private const val APPLIED_BADGE_COLOR = 0x55FF55
    private const val MARKER_HEIGHT = 0.22
    private const val MARKER_CLEARANCE = 0.75
    private const val SHURIKEN_THROW_COUNT = 1
    private const val MAX_HORIZONTAL_OFFSET_SQUARED = 0.25 * 0.25
    private const val MIN_VERTICAL_OFFSET = 1.5
    private const val MAX_VERTICAL_OFFSET = 3.2
}
