package com.skysoft.features.ravengard

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ClientEntitySnapshot
import com.skysoft.data.InteractionClick
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.ravengard.RAVENGARD_NAMESPACE
import com.skysoft.events.entity.EntityInteractionEvent
import com.skysoft.events.entity.EntityInteractionEvents
import com.skysoft.utils.ElapsedTimeMark
import com.skysoft.utils.SkysoftScreenEvents
import com.skysoft.utils.render.EntityLabelRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Interaction

object RavengardLootBagCheckmarks {
    fun register() {
        EntityInteractionEvents.register(
            "Ravengard loot bag interaction",
            ::isEnabled,
        ) { event ->
            trackInteraction(event)
            false
        }
        SkysoftScreenEvents.onBeforeInit(
            "Ravengard loot bag container opening",
            isActive = ::isEnabled,
            listener = ::onScreenOpened,
        )
        WorldRenderDispatcher.registerHandler("Ravengard loot bag checkmarks", ::isEnabled, ::renderWorld)
    }

    private fun isEnabled(): Boolean =
        HypixelLocationState.inRavengard && SkysoftConfigGui.config().ravengard.markOpenedLootBags

    private fun trackInteraction(event: EntityInteractionEvent) {
        if (event.clickType != InteractionClick.RIGHT_CLICK ||
            event.action != EntityInteractionEvent.ActionType.INTERACT_AT
        ) {
            return
        }
        val interaction = event.clickedEntity as? Interaction ?: return
        val level = Minecraft.getInstance().level ?: return
        syncLevel(level)
        val display = ClientEntitySnapshot.entities().asSequence()
            .filterIsInstance<Display.ItemDisplay>()
            .filter { candidate -> isRavengardLootBag(candidate.itemStack.get(DataComponents.ITEM_MODEL)) }
            .minByOrNull { candidate -> candidate.distanceToSqr(interaction) }
            ?.takeIf { candidate -> candidate.distanceToSqr(interaction) <= MAX_PAIR_DISTANCE_SQUARED }
            ?: return
        pendingOpen = PendingOpen(display.uuid, ElapsedTimeMark.now())
    }

    private fun onScreenOpened(minecraft: Minecraft, screen: Screen) {
        val container = screen as? AbstractContainerScreen<*> ?: return
        syncLevel(minecraft.level)
        val pending = pendingOpen ?: return
        pendingOpen = null
        if (pending.startedAt.passedSince() > OPEN_TIMEOUT) return
        if (minecraft.player?.containerMenu !== container.menu || container.title.string != DEAD_BODY_TITLE) return
        locallyOpenedBags += pending.displayUuid
    }

    private fun renderWorld(context: SkysoftRenderContext) {
        val level = Minecraft.getInstance().level ?: return
        syncLevel(level)
        ClientEntitySnapshot.entities().asSequence()
            .filterIsInstance<Display.ItemDisplay>()
            .filter { display -> display.uuid in locallyOpenedBags }
            .filter { display -> isOpenedRavengardLootBag(display.itemStack.get(DataComponents.ITEM_MODEL)) }
            .forEach { display -> EntityLabelRenderer.draw(context, display, listOf(CHECKMARK), CHECKMARK_STYLE) }
    }

    private fun syncLevel(level: ClientLevel?) {
        if (activeLevel === level) return
        clear()
        activeLevel = level
    }

    private fun clear() {
        pendingOpen = null
        locallyOpenedBags.clear()
    }

    private var activeLevel: ClientLevel? = null
    private var pendingOpen: PendingOpen? = null
    private val locallyOpenedBags = mutableSetOf<UUID>()
    private val CHECKMARK = Component.literal("\u2714").withStyle { style ->
        style.withColor(TextColor.fromRgb(CHECKMARK_COLOR)).withBold(true)
    }
    private val CHECKMARK_STYLE = WorldLabelStyle(displayMode = Font.DisplayMode.NORMAL)
    private val OPEN_TIMEOUT = 10.seconds
    private const val DEAD_BODY_TITLE = "Dead Body"
    private const val MAX_PAIR_DISTANCE_SQUARED = 1.0
    private const val CHECKMARK_COLOR = 0x55FF55

    private data class PendingOpen(
        val displayUuid: UUID,
        val startedAt: ElapsedTimeMark,
    )
}

internal fun isRavengardLootBag(model: Identifier?): Boolean =
    model?.namespace == RAVENGARD_NAMESPACE && model.path.startsWith(LOOT_BAG_MODEL_PREFIX)

internal fun isOpenedRavengardLootBag(model: Identifier?): Boolean =
    isRavengardLootBag(model) && model?.path?.endsWith(OPEN_MODEL_SUFFIX) == true

private const val LOOT_BAG_MODEL_PREFIX = "item/gameplay/lootbags/lootbag_"
private const val OPEN_MODEL_SUFFIX = "_open"
