package com.skysoft.features.safari

import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.features.combat.SegmentedMobHighlights
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.features.combat.SkyBlockMobHighlight
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

internal data class SafariCritter(
    val name: String,
    val rarity: SkyBlockRarity,
    val nameplate: ArmorStand,
    val captureEntity: LivingEntity?,
    val highlights: List<SkyBlockMobHighlight>,
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
        val highlights = SegmentedMobHighlights.parts(nameplate, entities).ifEmpty {
            matchedEntities
                .filter { entity -> entity is Display || entity is ArmorStand && !entity.isMarker }
                .ifEmpty { listOfNotNull(captureEntity) }
                .map { entity -> SkyBlockMobHighlight(entity, entity) }
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

    private data class CritterKind(
        val name: String,
        val rarity: SkyBlockRarity,
    )

    private fun critters(rarity: SkyBlockRarity, vararg names: String): List<CritterKind> =
        names.map { name -> CritterKind(name, rarity) }

    private const val CRITTER_ICON = ""
    private const val DISPLAY_PAIR_HORIZONTAL_DISTANCE_SQ = 1.0
    private const val DISPLAY_PAIR_MIN_VERTICAL_DISTANCE = -0.5
    private const val DISPLAY_PAIR_MAX_VERTICAL_DISTANCE = 4.0
    private const val CAPSULE_TARGET_DISTANCE_SQ = 9.0
    private val CRITTER_KINDS = buildList {
        addAll(critters(SkyBlockRarity.COMMON, "Cavernfish", "Flitter", "Foxtrot", "Shyworm", "Strongarm", "Tepid"))
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
