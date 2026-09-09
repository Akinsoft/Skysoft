package com.skysoft.features.combat

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockBestiaryFamilies
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.render.EntityHighlightRenderer
import com.skysoft.utils.render.EntityHighlightTracker
import com.skysoft.utils.render.EntityLabelRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity

internal object BestiaryWorldHighlights {
    private val config get() = SkysoftConfigGui.config().combat.bestiaryHelper
    private val highlightedEntities = EntityHighlightTracker<Entity>(this)
    private var ticks = 0

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Bestiary Helper highlighting",
            isActive = { config.enabled || highlightedEntities.isNotEmpty() },
        ) { updateHighlights() }
        SkysoftClientEvents.onDisconnect("Bestiary Helper highlight reset", ::clearHighlights)
        WorldRenderDispatcher.registerHandler(
            "Bestiary Helper target labels",
            isActive = {
                config.enabled && HypixelLocationState.inSkyBlock && config.selectedMobs.isNotEmpty()
            },
            handler = ::renderWorld,
        )
    }

    private fun updateHighlights() {
        if (!config.enabled || !HypixelLocationState.inSkyBlock || config.selectedMobs.isEmpty()) {
            clearHighlights()
            return
        }
        if (++ticks % HIGHLIGHT_SCAN_INTERVAL_TICKS != 0) return
        val highlights = SkyBlockMobEntityMatcher.visibleSignals(SkyBlockBestiaryFamilies.mobNames(config.selectedMobs))
            .flatMap { signal ->
                val parts = signal.nameplate?.let { nameplate ->
                    SegmentedMobHighlights.parts(nameplate, ClientEntitySnapshot.entities())
                }.orEmpty()
                parts.ifEmpty { listOfNotNull(signal.entity?.let { SkyBlockMobHighlight(it, it) }) }
            }
        highlightedEntities.replaceWith(highlights.mapTo(mutableSetOf()) { it.entity })
        val color = config.details.highlightColor.get().toColor()
        highlights.forEach { highlight ->
            EntityHighlightRenderer.setEntityColor(
                highlight.entity,
                color,
                source = this,
                visibilityEntity = highlight.visibilityEntity,
            ) {
                config.enabled && highlight.entity in highlightedEntities
            }
        }
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val lines = buildList {
            config.details.targetText.takeIf(String::isNotBlank)?.let { text -> add(Component.literal(text)) }
            add(Component.literal(TARGET_MARKER))
        }
        val style = WorldLabelStyle(
            textColor = config.details.textColor.get().toColor().rgb,
            displayMode = Font.DisplayMode.NORMAL,
        )
        SkyBlockMobEntityMatcher.visibleSignals(SkyBlockBestiaryFamilies.mobNames(config.selectedMobs)).forEach { signal ->
            val anchor = signal.nameplate ?: signal.entity ?: return@forEach
            EntityLabelRenderer.drawAboveNameTag(context, anchor, lines, style)
        }
    }

    private fun clearHighlights() {
        highlightedEntities.clear()
        ticks = 0
    }

    private const val HIGHLIGHT_SCAN_INTERVAL_TICKS = 4
    private const val TARGET_MARKER = "▾"
}
