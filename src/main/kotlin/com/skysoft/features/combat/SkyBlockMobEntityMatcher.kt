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

    fun allEntities(): List<Entity> =
        Minecraft.getInstance().level?.entitiesForRendering()?.toList().orEmpty()

    private fun ArmorStand.signal(entities: List<Entity>, labels: List<String>): SkyBlockMobSignal? {
        val name = cleanName()
        val label = matchingPreparedMobLabel(name, labels) ?: return null
        val linkedEntity = linkedPhysicalEntity(entities)
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

    private fun ArmorStand.linkedPhysicalEntity(entities: List<Entity>): LivingEntity? {
        val byId = entities.firstOrNull { entity -> entity.id == id - 1 && entity is LivingEntity } as? LivingEntity
        if (byId != null && byId.isPossiblePhysicalEntity() && byId.isTightPair(this)) return byId
        return entities.filterIsInstance<LivingEntity>()
            .filter { entity -> entity.isPossiblePhysicalEntity() && entity.isTightPair(this) }
            .minByOrNull { entity -> entity.distanceToSqr(this) }
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
        isAlive && isPossiblePhysicalEntity() && this !is Player

    private fun LivingEntity.isPossiblePhysicalEntity(): Boolean {
        val player = Minecraft.getInstance().player
        if (this == player || this is ArmorStand) return false
        return this !is Player || !isRealPlayer()
    }

    private fun Player.isRealPlayer(): Boolean =
        uuid.version() == REAL_PLAYER_UUID_VERSION

    private const val NAMEPLATE_PAIR_HORIZONTAL_DISTANCE_SQ = 1.0
    private const val NAMEPLATE_PAIR_MAX_VERTICAL_DISTANCE = 4.0
    private const val REAL_PLAYER_UUID_VERSION = 4
}

internal fun matchingMobLabel(name: String, labels: Collection<String>): String? =
    matchingPreparedMobLabel(name, prepareMobLabels(labels))

private fun matchingPreparedMobLabel(name: String, labels: List<String>): String? {
    val normalizedName = normalizeMobName(SkyBlockMobTextParser.parseName(name) ?: name).withoutEmpyreanPrefix()
    return labels.firstOrNull { label -> normalizedName.equals(label, ignoreCase = true) }
}

private fun prepareMobLabels(labels: Collection<String>): List<String> = labels.asSequence()
    .filter(String::isNotBlank)
    .distinctBy { label -> label.lowercase(Locale.ROOT) }
    .sortedByDescending(String::length)
    .toList()

private fun normalizeMobName(name: String): String = name.replace(TIER_SUFFIX, "").trim()

private fun String.withoutEmpyreanPrefix(): String =
    if (startsWith(EMPYREAN_PREFIX, ignoreCase = true)) substring(EMPYREAN_PREFIX.length) else this

private val TIER_SUFFIX = Regex("""\s+[IVX]+$""")
private const val EMPYREAN_PREFIX = "Empyrean "
