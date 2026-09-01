package com.skysoft.features.combat

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
    val health: SkyBlockMobHealth?,
)

internal object SkyBlockMobEntityMatcher {
    fun visibleSignals(
        labels: Collection<String>,
        entities: List<Entity> = allEntities(),
    ): List<SkyBlockMobSignal> {
        val preparedLabels = prepareMobLabels(labels)
        if (preparedLabels.isEmpty()) return emptyList()
        val nameplateSignals = entities.filterIsInstance<ArmorStand>()
            .mapNotNull { armorStand -> armorStand.signal(entities, preparedLabels) }
        val nameplateEntityIds = nameplateSignals.mapNotNullTo(mutableSetOf()) { it.entity?.id }
        val physicalSignals = entities.filterIsInstance<LivingEntity>()
            .filter { entity -> entity.id !in nameplateEntityIds }
            .mapNotNull { entity -> entity.physicalSignal(preparedLabels) }
        return nameplateSignals + physicalSignals
    }

    fun hasVisibleNameplateFor(
        entity: LivingEntity,
        labels: Collection<String>,
        entities: Iterable<Entity> = Minecraft.getInstance().level?.entitiesForRendering() ?: emptyList(),
    ): Boolean {
        val preparedLabels = prepareMobLabels(labels)
        if (preparedLabels.isEmpty()) return false
        for (candidate in entities) {
            val nameplate = candidate as? ArmorStand ?: continue
            if (
                canPairWithNameplate(entity, nameplate) &&
                nameplate.signal(entities, preparedLabels)?.entity?.id == entity.id
            ) {
                return true
            }
        }
        return false
    }

    fun canPairWithNameplate(entity: LivingEntity, nameplate: ArmorStand): Boolean =
        entity.isTightPair(nameplate)

    fun allEntities(): List<Entity> =
        Minecraft.getInstance().level?.entitiesForRendering()?.toList().orEmpty()

    fun physicalEntityFor(
        nameplate: ArmorStand,
        entities: Iterable<Entity> = Minecraft.getInstance().level?.entitiesForRendering() ?: emptyList(),
        isCandidate: (LivingEntity) -> Boolean = { true },
    ): LivingEntity? = nameplate.linkedPhysicalEntity(entities, isCandidate)

    private fun ArmorStand.signal(entities: Iterable<Entity>, labels: List<String>): SkyBlockMobSignal? {
        if (!hasCustomName()) return null
        val name = cleanName()
        val label = matchingPreparedMobLabel(name, labels) ?: return null
        val linkedEntity = physicalEntityFor(this, entities)
        if (linkedEntity?.isDeadOrDying == true) return null
        return SkyBlockMobSignal(
            label = label,
            location = linkedEntity?.position()?.toWorldVec() ?: position().toWorldVec(),
            entity = linkedEntity,
            nameplate = this,
            health = SkyBlockMobTextParser.parseHealth(name),
        )
    }

    private fun LivingEntity.physicalSignal(labels: List<String>): SkyBlockMobSignal? {
        if (!isStandaloneSignalEntity() || !hasCustomName()) return null
        val label = matchingPreparedMobLabel(cleanName(), labels) ?: return null
        return SkyBlockMobSignal(
            label = label,
            location = position().toWorldVec(),
            entity = this,
            nameplate = null,
            health = null,
        )
    }

    private fun ArmorStand.linkedPhysicalEntity(
        entities: Iterable<Entity>,
        isCandidate: (LivingEntity) -> Boolean,
    ): LivingEntity? {
        var closest: LivingEntity? = null
        var closestDistance = Double.MAX_VALUE
        for (candidate in entities) {
            val entity = candidate as? LivingEntity ?: continue
            if (!entity.isPossibleSkyBlockMob() || !isCandidate(entity) || !entity.isTightPair(this)) continue
            if (entity.id == id - 1) return entity
            val distance = entity.distanceToSqr(this)
            if (distance < closestDistance) {
                closest = entity
                closestDistance = distance
            }
        }
        return closest
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
    val normalizedName = normalizeMobName(SkyBlockMobTextParser.parseName(name) ?: name).withoutMobPrefix()
    return labels.firstOrNull { label -> normalizedName.equals(label, ignoreCase = true) }
}

private fun prepareMobLabels(labels: Collection<String>): List<String> = labels.asSequence()
    .filter(String::isNotBlank)
    .distinctBy { label -> label.lowercase(Locale.ROOT) }
    .sortedByDescending(String::length)
    .toList()

private fun normalizeMobName(name: String): String = name.replace(TIER_SUFFIX, "").trim()

private fun String.withoutMobPrefix(): String {
    val prefix = MOB_PREFIXES.firstOrNull { prefix -> startsWith("$prefix ", ignoreCase = true) } ?: return this
    return substring(prefix.length + 1)
}

private val TIER_SUFFIX = Regex("""\s+[IVX]+$""")
private val MOB_PREFIXES = setOf("Empyrean", "Exalted", "Runic", "Venerable", "Stalwart", "Blessed")
private const val REAL_PLAYER_UUID_VERSION = 4
