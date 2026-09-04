package com.skysoft.features.farming

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.ItemListEntryKind
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.data.skyblock.SkyBlockItemUtilities.playerHeadTexture
import com.skysoft.data.skyblock.pets.PetSkins
import com.skysoft.features.combat.SkyBlockMobEntityMatcher
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import java.awt.Color
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

object HighlightPests {
    private val config get() = SkysoftConfigGui.config().farming
    private val highlightedEntities = EntityHighlightTracker<ArmorStand>(this)
    private var ticks = 0

    fun register() {
        SkyBlockDataRepository.Demand.register("Highlight Pests") { config.highlightPests }
        SkysoftClientEvents.onEndTick(
            "Highlight Pests tick",
            isActive = { isEnabled() || highlightedEntities.isNotEmpty() },
        ) { updateHighlights() }
        SkysoftClientEvents.onDisconnect("Highlight Pests disconnect reset", ::clear)
    }

    private fun updateHighlights() {
        if (!isEnabled()) {
            clear()
            return
        }
        if (++ticks % SCAN_INTERVAL_TICKS != 0) return

        val pestTextures = pestTextureIdentities()
        val pests = SkyBlockMobEntityMatcher.allEntities().asSequence()
            .filterIsInstance<ArmorStand>()
            .filterTo(mutableSetOf()) { armorStand ->
                armorStand.isAlive && armorStand.isInvisible && !armorStand.isMarker &&
                    armorStand.getItemBySlot(EquipmentSlot.HEAD).playerHeadTexture()
                        ?.let { texture -> PetSkins.textureIdentity(texture) in pestTextures } == true
            }
        highlightedEntities.replaceWith(pests).forEach { pest ->
            EntityHighlightRenderer.setEntityColor(pest, HIGHLIGHT_COLOR, source = this) {
                isEnabled() && pest in highlightedEntities
            }
        }
    }

    private fun pestTextureIdentities(): Set<String> = SkyBlockDataRepository.entries.asSequence()
        .filter { entry -> entry.key.kind == ItemListEntryKind.ENTITY && PEST_TAG in entry.tags }
        .mapNotNull { entry -> SkyBlockDataRepository.entity(entry.key.id)?.texture }
        .map(PetSkins::textureIdentity)
        .toSet()

    private fun clear() {
        highlightedEntities.clear()
        ticks = 0
    }

    private fun isEnabled(): Boolean = config.highlightPests && SkyBlockIsland.GARDEN.isInIsland()

    private const val PEST_TAG = "Pest"
    private const val SCAN_INTERVAL_TICKS = 4
    private val HIGHLIGHT_COLOR = Color(85, 255, 85)
}
