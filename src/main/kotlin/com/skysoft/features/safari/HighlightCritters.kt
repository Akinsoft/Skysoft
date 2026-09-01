package com.skysoft.features.safari

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import java.util.UUID
import net.minecraft.world.entity.Entity

object HighlightCritters {
    private val config get() = SkysoftConfigGui.config().safari
    private val trackedCritters = mutableListOf<TrackedCritter>()
    private val highlightedEntities = EntityHighlightTracker<Entity>(this)
    private var ticks = 0L

    fun register() {
        HypixelLocationState.onChange(
            "Highlight Critters location",
            isActive = { isEnabled() || trackedCritters.isNotEmpty() || highlightedEntities.isNotEmpty() },
        ) { clear() }
        SkysoftClientEvents.onEndTick(
            "Highlight Critters tick",
            isActive = { isEnabled() || trackedCritters.isNotEmpty() || highlightedEntities.isNotEmpty() },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Highlight Critters disconnect reset", ::clear)
    }

    private fun tick() {
        if (!isEnabled()) {
            clear()
            return
        }
        if (++ticks % SCAN_INTERVAL_TICKS != 0L) return

        val loadedEntities = SkyBlockMobEntityMatcher.allEntities()
            .filter(Entity::isAlive)
            .associateBy(Entity::getUUID)
        SafariCritterDetector.detectedCritters(loadedEntities.values.toList()).forEach { critter ->
            confirmCritter(critter)
        }
        trackedCritters.forEach { critter ->
            if (critter.highlightVisibilityUuids.keys.any(loadedEntities::containsKey)) {
                critter.lastModelSeenTick = ticks
            }
        }
        trackedCritters.removeIf { critter ->
            ticks - maxOf(critter.lastNameplateSeenTick, critter.lastModelSeenTick) >= STALE_AFTER_TICKS
        }

        val nextHighlights = buildMap {
            trackedCritters.forEach { critter ->
                critter.highlightVisibilityUuids.forEach highlight@{ (modelUuid, visibilityUuid) ->
                    val entity = loadedEntities[modelUuid] ?: return@highlight
                    val visibilityEntity = loadedEntities[visibilityUuid] ?: return@highlight
                    put(entity, CritterHighlight(critter.rarity, visibilityEntity))
                }
            }
        }
        updateHighlights(nextHighlights)
    }

    private fun confirmCritter(critter: SafariCritter) {
        val highlightVisibilityUuids = critter.highlights.associate { highlight ->
            highlight.entity.uuid to highlight.visibilityEntity.uuid
        }
        val highlightEntityUuids = highlightVisibilityUuids.keys
        val tracked = trackedCritters.firstOrNull { tracked -> tracked.nameplateUuid == critter.nameplate.uuid }
            ?: trackedCritters.firstOrNull { tracked ->
                tracked.highlightVisibilityUuids.keys.any(highlightEntityUuids::contains)
            }
            ?: TrackedCritter(
                nameplateUuid = critter.nameplate.uuid,
                rarity = critter.rarity,
                lastNameplateSeenTick = ticks,
                lastModelSeenTick = ticks,
            ).also(trackedCritters::add)
        tracked.nameplateUuid = critter.nameplate.uuid
        tracked.rarity = critter.rarity
        tracked.highlightVisibilityUuids += highlightVisibilityUuids
        tracked.lastNameplateSeenTick = ticks
        if (highlightEntityUuids.isNotEmpty()) tracked.lastModelSeenTick = ticks
    }

    private fun updateHighlights(nextHighlights: Map<Entity, CritterHighlight>) {
        highlightedEntities.replaceWith(nextHighlights.keys)
        nextHighlights.forEach { (entity, highlight) ->
            EntityHighlightRenderer.setEntityColor(
                entity = entity,
                color = highlight.rarity.color,
                source = this,
                visibilityEntity = highlight.visibilityEntity,
            ) { isEnabled() && entity in highlightedEntities }
        }
    }

    private fun clear() {
        highlightedEntities.clear()
        trackedCritters.clear()
        ticks = 0L
    }

    private fun isEnabled(): Boolean = config.highlightCritters && SkyBlockIsland.SAFARI.isInIsland()

    private data class TrackedCritter(
        var nameplateUuid: UUID,
        var rarity: SkyBlockRarity,
        val highlightVisibilityUuids: MutableMap<UUID, UUID> = mutableMapOf(),
        var lastNameplateSeenTick: Long,
        var lastModelSeenTick: Long,
    )

    private data class CritterHighlight(
        val rarity: SkyBlockRarity,
        val visibilityEntity: Entity,
    )

    private const val SCAN_INTERVAL_TICKS = 4L
    private const val STALE_AFTER_TICKS = 30L * 20L
}
