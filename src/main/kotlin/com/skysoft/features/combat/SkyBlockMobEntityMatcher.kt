package com.skysoft.features.combat

import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.skyblock.withoutSkyBlockMobModifierPrefix
import com.skysoft.utils.EntityUtilities.cleanName
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

internal data class SkyBlockMobSignal(
    val label: String,
    val location: WorldVec,
    val entity: LivingEntity?,
    val nameplate: ArmorStand?,
    val trackedMob: SkyBlockMob,
)

internal data class DetectedSkyBlockMob(
    val name: String,
    val location: WorldVec,
    val entity: LivingEntity,
    val nameplate: ArmorStand?,
    val health: SkyBlockMobHealth?,
)

internal object SkyBlockMobEntityMatcher {
    fun visibleSignals(labels: Collection<String>): List<SkyBlockMobSignal> {
        val preparedLabels = prepareMobLabels(labels)
        if (preparedLabels.isEmpty()) return emptyList()
        return SkyBlockMobTracker.visibleMobs().mapNotNull { mob -> mob.signal(preparedLabels) }
    }

    fun hasVisibleNameplateFor(
        entity: LivingEntity,
        labels: Collection<String>,
        entities: Iterable<Entity> = ClientEntitySnapshot.entities(),
    ): Boolean {
        val preparedLabels = prepareMobLabels(labels)
        if (preparedLabels.isEmpty()) return false
        for (candidate in entities) {
            val nameplate = candidate as? ArmorStand ?: continue
            val detected = nameplate.detectedMob(entities) ?: continue
            if (detected.entity.uuid == entity.uuid && matchingPreparedMobLabel(detected.name, preparedLabels) != null) {
                return true
            }
        }
        return false
    }

    fun canPairWithNameplate(entity: LivingEntity, nameplate: ArmorStand): Boolean =
        entity.isTightPair(nameplate)

    fun allEntities(): List<Entity> = ClientEntitySnapshot.entities()

    fun physicalEntityFor(
        nameplate: ArmorStand,
        entities: Iterable<Entity> = ClientEntitySnapshot.entities(),
        isCandidate: (LivingEntity) -> Boolean = { true },
    ): LivingEntity? = nameplate.linkedPhysicalEntity(entities, isCandidate)

    internal fun detectedMobs(entities: List<Entity>): List<DetectedSkyBlockMob> {
        val nameplateMobs = entities.filterIsInstance<ArmorStand>()
            .mapNotNull { nameplate -> nameplate.detectedMob(entities) }
            .groupBy { mob -> mob.entity.uuid }
            .values
            .map { candidates ->
                candidates.minWith(
                    compareBy<DetectedSkyBlockMob> { mob -> if (mob.nameplate?.id == mob.entity.id + 1) 0 else 1 }
                        .thenBy { mob -> mob.nameplate?.let(mob.entity::distanceToSqr) ?: Double.MAX_VALUE },
                )
            }
        val pairedEntityUuids = nameplateMobs.mapTo(mutableSetOf()) { mob -> mob.entity.uuid }
        val standaloneMobs = entities.filterIsInstance<LivingEntity>()
            .filter { entity -> entity.uuid !in pairedEntityUuids }
            .mapNotNull { entity -> entity.detectedStandaloneMob() }
        return nameplateMobs + standaloneMobs
    }

    private fun ArmorStand.detectedMob(entities: Iterable<Entity>): DetectedSkyBlockMob? {
        if (!hasCustomName()) return null
        val text = cleanName()
        val health = SkyBlockMobTextParser.parseHealth(text) ?: return null
        val name = SkyBlockMobTextParser.parseName(text) ?: return null
        val linkedEntity = physicalEntityFor(this, entities) ?: return null
        return DetectedSkyBlockMob(
            name = name,
            location = linkedEntity.position().toWorldVec(),
            entity = linkedEntity,
            nameplate = this,
            health = health,
        )
    }

    private fun LivingEntity.detectedStandaloneMob(): DetectedSkyBlockMob? {
        if (!isStandaloneSignalEntity() || !hasCustomName()) return null
        return DetectedSkyBlockMob(
            name = cleanName(),
            location = position().toWorldVec(),
            entity = this,
            nameplate = null,
            health = null,
        )
    }

    private fun SkyBlockMob.signal(labels: List<String>): SkyBlockMobSignal? {
        val label = matchingPreparedMobLabel(name, labels) ?: return null
        return SkyBlockMobSignal(label, location, entity, nameplate, this)
    }

    private fun ArmorStand.linkedPhysicalEntity(
        entities: Iterable<Entity>,
        isCandidate: (LivingEntity) -> Boolean,
    ): LivingEntity? {
        val candidates = entities
            .filterIsInstance<LivingEntity>()
            .filter { entity -> entity.isPossibleSkyBlockMob() && isCandidate(entity) && entity.isTightPair(this) }
        return candidates.firstOrNull { entity -> entity.id == id - 1 } ?: candidates.singleOrNull()
    }

    private fun LivingEntity.isTightPair(nameplate: ArmorStand): Boolean {
        val dx = x - nameplate.x
        val dz = z - nameplate.z
        val verticalOffset = nameplate.y - y
        return dx * dx + dz * dz <= NAMEPLATE_PAIR_HORIZONTAL_DISTANCE_SQ &&
            verticalOffset >= 0.0 &&
            verticalOffset <= NAMEPLATE_PAIR_MAX_VERTICAL_DISTANCE
    }

    private fun LivingEntity.isStandaloneSignalEntity(): Boolean =
        isAlive && isPossibleSkyBlockMob() && this !is Player

    private const val NAMEPLATE_PAIR_HORIZONTAL_DISTANCE_SQ = 1.0
    private const val NAMEPLATE_PAIR_MAX_VERTICAL_DISTANCE = 4.0
}

internal fun LivingEntity.isPossibleSkyBlockMob(): Boolean {
    val player = Minecraft.getInstance().player
    if (this == player || this is ArmorStand) return false
    return this !is Player || uuid.version() != REAL_PLAYER_UUID_VERSION
}

private fun matchingPreparedMobLabel(name: String, labels: List<String>): String? {
    if (labels.none { label -> name.contains(label, ignoreCase = true) }) return null
    val normalizedName = normalizeMobName(SkyBlockMobTextParser.parseName(name) ?: name)
        .withoutSkyBlockMobModifierPrefix(ignoreCase = true)
    return labels.firstOrNull { label -> normalizedName.equals(label, ignoreCase = true) }
}

private fun prepareMobLabels(labels: Collection<String>): List<String> = labels.asSequence()
    .filter(String::isNotBlank)
    .distinctBy { label -> label.lowercase(Locale.ROOT) }
    .sortedByDescending(String::length)
    .toList()

private fun normalizeMobName(name: String): String = name.replace(TIER_SUFFIX, "").trim()

private val TIER_SUFFIX = Regex("""\s+[IVX]+$""")
private const val REAL_PLAYER_UUID_VERSION = 4
