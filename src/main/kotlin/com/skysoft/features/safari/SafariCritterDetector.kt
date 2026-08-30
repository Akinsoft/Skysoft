package com.skysoft.features.safari

import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items

internal data class SafariCritter(
    val name: String,
    val rarity: SkyBlockRarity,
    val nameplate: ArmorStand,
    val captureEntity: LivingEntity?,
    val highlights: List<SafariCritterHighlight>,
)

internal data class SafariCritterHighlight(
    val entity: Entity,
    val visibilityEntity: Entity,
)

internal object SafariCritterDetector {
    fun detectedCritters(
        entities: List<Entity> = SkyBlockMobEntityMatcher.allEntities(),
    ): List<SafariCritter> {
        val nameplates = critterNameplates(entities)
        return nameplates.map { named -> named.toCritter(entities, nameplates) }
    }

    fun critterFor(entity: LivingEntity): SafariCritter? {
        val entities = SkyBlockMobEntityMatcher.allEntities()
        val nameplates = critterNameplates(entities)
        return nameplates
            .asSequence()
            .filter { named -> SkyBlockMobEntityMatcher.canPairWithNameplate(entity, named.nameplate) }
            .minByOrNull { named -> entity.distanceToSqr(named.nameplate) }
            ?.toCritter(entities, nameplates)
    }

    fun critterNear(entity: Entity): SafariCritter? {
        val entities = SkyBlockMobEntityMatcher.allEntities()
        val nameplates = critterNameplates(entities)
        return nameplates
            .asSequence()
            .filter { named -> entity.distanceToSqr(named.nameplate) <= CAPSULE_TARGET_DISTANCE_SQ }
            .minByOrNull { named -> entity.distanceToSqr(named.nameplate) }
            ?.toCritter(entities, nameplates)
    }

    private fun NamedCritter.toCritter(
        entities: List<Entity>,
        nameplates: List<NamedCritter>,
    ): SafariCritter {
        val matchedEntities = entities
            .asSequence()
            .filter { entity -> entity !== nameplate && entity.isAlive }
            .filter { entity -> entity.nearestNameplate(nameplates)?.nameplate === nameplate }
            .toList()
        val captureEntity = SkyBlockMobEntityMatcher.physicalEntityFor(nameplate, entities)
            ?: matchedEntities.filterIsInstance<ArmorStand>().firstOrNull { stand -> !stand.isMarker }
        val highlights = when (kind.model) {
            CritterModel.STANDARD ->
                matchedEntities
                    .filter { entity -> entity is Display || entity is ArmorStand && !entity.isMarker }
                    .ifEmpty { listOfNotNull(captureEntity) }
                    .map { entity -> SafariCritterHighlight(entity, entity) }
            CritterModel.SHYWORM -> shywormHighlights(nameplate, entities)
        }
        return SafariCritter(
            name = kind.name,
            rarity = kind.rarity,
            nameplate = nameplate,
            captureEntity = captureEntity,
            highlights = highlights,
        )
    }

    private fun Entity.nearestNameplate(nameplates: List<NamedCritter>): NamedCritter? = nameplates
        .asSequence()
        .filter { named -> isNear(named.nameplate) }
        .minByOrNull { named -> distanceToSqr(named.nameplate) }

    private fun Entity.isNear(nameplate: ArmorStand): Boolean {
        val dx = x - nameplate.x
        val dz = z - nameplate.z
        val verticalOffset = nameplate.y - y
        return dx * dx + dz * dz <= DISPLAY_PAIR_HORIZONTAL_DISTANCE_SQ &&
            verticalOffset in DISPLAY_PAIR_MIN_VERTICAL_DISTANCE..DISPLAY_PAIR_MAX_VERTICAL_DISTANCE
    }

    private fun critterNameplates(entities: List<Entity>): List<NamedCritter> = entities
        .asSequence()
        .filterIsInstance<ArmorStand>()
        .filter(ArmorStand::isAlive)
        .mapNotNull { nameplate ->
            val text = nameplate.customName?.string ?: return@mapNotNull null
            if (!text.startsWith(CRITTER_ICON)) return@mapNotNull null
            CRITTER_KINDS.firstOrNull { kind -> text.endsWith(" ${kind.name}") }
                ?.let { kind -> NamedCritter(kind, nameplate) }
        }
        .toList()

    private data class NamedCritter(
        val kind: CritterKind,
        val nameplate: ArmorStand,
    )

    private fun shywormHighlights(nameplate: ArmorStand, entities: List<Entity>): List<SafariCritterHighlight> {
        val entitiesById = entities.associateBy(Entity::getId)
        val head = entitiesById[nameplate.id - SHYWORM_HEAD_OFFSET]
        if (!head.isShywormPhysicalPart(ZOMBIE_ENTITY_TYPE)) return emptyList()
        if (
            SHYWORM_BODY_OFFSETS.any { offset ->
                !entitiesById[nameplate.id - offset].isShywormPhysicalPart(SLIME_ENTITY_TYPE)
            }
        ) {
            return emptyList()
        }
        return SHYWORM_MODEL_OFFSETS.map { offset ->
            val stand = entitiesById[nameplate.id - offset] as? ArmorStand ?: return emptyList()
            if (
                !stand.isAlive || !stand.isInvisible || !stand.isMarker ||
                stand.getItemBySlot(EquipmentSlot.HEAD).item != Items.PLAYER_HEAD
            ) {
                return emptyList()
            }
            SafariCritterHighlight(stand, entitiesById.getValue(stand.id - 1))
        }
    }

    private fun Entity?.isShywormPhysicalPart(typePath: String): Boolean =
        this is LivingEntity && BuiltInRegistries.ENTITY_TYPE.getKey(type).path == typePath && isAlive && isInvisible

    private data class CritterKind(
        val name: String,
        val rarity: SkyBlockRarity,
        val model: CritterModel = CritterModel.STANDARD,
    )

    private enum class CritterModel {
        STANDARD,
        SHYWORM,
    }

    private fun critters(rarity: SkyBlockRarity, vararg names: String): List<CritterKind> =
        names.map { name -> CritterKind(name, rarity) }

    private const val CRITTER_ICON = ""
    private const val DISPLAY_PAIR_HORIZONTAL_DISTANCE_SQ = 1.0
    private const val DISPLAY_PAIR_MIN_VERTICAL_DISTANCE = -0.5
    private const val DISPLAY_PAIR_MAX_VERTICAL_DISTANCE = 4.0
    private const val CAPSULE_TARGET_DISTANCE_SQ = 9.0
    private const val ZOMBIE_ENTITY_TYPE = "zombie"
    private const val SLIME_ENTITY_TYPE = "slime"
    private const val SHYWORM_HEAD_OFFSET = 17
    private val SHYWORM_BODY_OFFSETS = 1..15 step 2
    private val SHYWORM_MODEL_OFFSETS = 2..16 step 2
    private val CRITTER_KINDS = buildList {
        addAll(critters(SkyBlockRarity.COMMON, "Cavernfish", "Flitter", "Foxtrot", "Strongarm", "Tepid"))
        add(CritterKind("Shyworm", SkyBlockRarity.COMMON, CritterModel.SHYWORM))
        addAll(
            critters(
                SkyBlockRarity.UNCOMMON,
                "Driftling",
                "Bluebird",
                "Honeybug",
                "Treefrog",
                "Woodchucker",
                "Areita",
                "Bloodbat",
                "Duplico",
                "Gazer",
                "Litterbug",
                "Solsnatcher",
                "Polaris",
                "Shuddersquid",
            ),
        )
        addAll(
            critters(
                SkyBlockRarity.RARE,
                "Chuckwalla",
                "Rockmite",
                "Scrappy",
                "Snoozle",
                "Fluffling",
                "Hideonfloor",
                "Parakeet",
                "Gimmiegold",
                "Hideonwall",
                "Hideyho",
                "Billygoat",
                "Mantis Shrimp",
                "Nozzlenose",
                "Troodon",
            ),
        )
        addAll(critters(SkyBlockRarity.EPIC, "Gemzie"))
        addAll(critters(SkyBlockRarity.LEGENDARY, "Macaw", "Doomspiral", "Wumpa"))
    }.sortedByDescending { kind -> kind.name.length }
}
