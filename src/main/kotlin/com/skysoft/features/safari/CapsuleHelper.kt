package com.skysoft.features.safari

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.skyblock.SkyBlockItemId.skyBlockId
import com.skysoft.data.skyblock.SkyBlockRarity
import com.skysoft.events.entity.ClientEntityMetadataEvents
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.render.LineBoxRenderer
import com.skysoft.utils.render.SkysoftRenderContext
import com.skysoft.utils.render.WorldLabelRenderer
import com.skysoft.utils.render.WorldLabelStyle
import com.skysoft.utils.render.WorldRenderDispatcher
import com.skysoft.utils.toWorldVec
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.LivingEntity

object CapsuleHelper {
    private val config get() = SkysoftConfigGui.config().safari.capsuleHelper
    private val activeCaptures = mutableListOf<ActiveCapture>()
    private val pendingImpacts = mutableListOf<PendingImpact>()
    private val pendingCapsuleImpacts = mutableListOf<PendingImpact>()

    fun register() {
        ClientEntityMetadataEvents.register(
            "Capsule Helper critter impact",
            isActive = ::isEnabled,
        ) { event ->
            if (event.packedItems.isEmpty()) recordImpact(event.entityId)
        }
        ChatEvents.onVisibleMessage("Capsule Helper chat", ::isEnabled) { message ->
            if (message.isSystemLike) handleMessage(message.cleanText, message.component)
            ChatMessageVisibility.SHOW
        }
        SkysoftClientEvents.onEndTick(
            "Capsule Helper tracking",
            isActive = {
                config.enabled || pendingImpacts.isNotEmpty() || pendingCapsuleImpacts.isNotEmpty() || activeCaptures.isNotEmpty()
            },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Capsule Helper disconnect reset", ::clear)
        WorldRenderDispatcher.registerHandler("Capsule Helper rendering", ::isEnabled, ::render)
    }

    private fun recordImpact(entityId: Int) {
        val entity = Minecraft.getInstance().level?.getEntity(entityId) ?: return
        when (entity) {
            is LivingEntity -> SafariCritterDetector.critterFor(entity)?.let { critter ->
                pendingImpacts += PendingImpact(critter.name, entity.position().toWorldVec())
            }
            is Display.ItemDisplay -> {
                if (entity.itemStack.skyBlockId() != CRITTER_CAPSULE_ID) return
                SafariCritterDetector.critterNear(entity)?.let { critter ->
                    val captureEntity = critter.captureEntity ?: return@let
                    pendingCapsuleImpacts += PendingImpact(critter.name, captureEntity.position().toWorldVec())
                }
            }
        }
    }

    private fun handleMessage(text: String, component: Component) {
        val throwMatch = THROW_MESSAGE.matchEntire(text)
        if (throwMatch != null) {
            val critter = throwMatch.groups[CRITTER_GROUP]?.value ?: return
            val rarity = SkyBlockRarity.getByComponent(component, critter) ?: return
            val impactIndex = pendingImpacts.indexOfFirst { impact -> impact.critter == critter }
            val impact = if (impactIndex >= 0) {
                pendingCapsuleImpacts.indexOfFirst { candidate -> candidate.critter == critter }
                    .takeIf { index -> index >= 0 }
                    ?.let(pendingCapsuleImpacts::removeAt)
                pendingImpacts.removeAt(impactIndex)
            } else {
                val capsuleImpactIndex = pendingCapsuleImpacts.indexOfFirst { candidate -> candidate.critter == critter }
                if (capsuleImpactIndex < 0) return
                pendingCapsuleImpacts.removeAt(capsuleImpactIndex)
            }
            if (rarity != SkyBlockRarity.COMMON) {
                activeCaptures += ActiveCapture(critter, impact.location, critterLabel(critter, rarity))
            }
            return
        }

        val completedCritter = ESCAPE_MESSAGE.matchEntire(text)?.groups?.get(CRITTER_GROUP)?.value
            ?: CAPTURE_MESSAGE.matchEntire(text)?.groups?.get(CRITTER_GROUP)?.value
            ?: return
        activeCaptures.indexOfFirst { capture -> capture.critter == completedCritter }
            .takeIf { index -> index >= 0 }
            ?.let(activeCaptures::removeAt)
    }

    private fun tick() {
        if (!isEnabled()) {
            clear()
            return
        }
        pendingImpacts.clear()
        pendingCapsuleImpacts.clear()
    }

    private fun render(context: SkysoftRenderContext) {
        activeCaptures.forEach { capture ->
            val location = capture.location
            LineBoxRenderer.draw3D(context, AIM_LINE_WIDTH, depth = true) {
                drawBox(location - AIM_RADIUS, location + AIM_RADIUS, AIM_COLOR)
                draw3DLine(location - AIM_X, location + AIM_X, AIM_COLOR)
                draw3DLine(location - AIM_Y, location + AIM_Y, AIM_COLOR)
                draw3DLine(location - AIM_Z, location + AIM_Z, AIM_COLOR)
            }
            WorldLabelRenderer.draw(context, location + LABEL_OFFSET, listOf(capture.label), LABEL_STYLE)
            if (config.details.crosshairLine) {
                context.drawLineToCrosshair(location, AIM_COLOR, AIM_LINE_WIDTH, depth = true)
            }
        }
    }

    private fun clear() {
        pendingImpacts.clear()
        pendingCapsuleImpacts.clear()
        activeCaptures.clear()
    }

    private fun isEnabled(): Boolean = config.enabled && SkyBlockIsland.SAFARI.isInIsland()

    private data class PendingImpact(
        val critter: String,
        val location: WorldVec,
    )

    private data class ActiveCapture(
        val critter: String,
        val location: WorldVec,
        val label: Component,
    )

    private fun critterLabel(critter: String, rarity: SkyBlockRarity): Component =
        Component.literal(critter).withStyle { style ->
            style.withColor(TextColor.fromRgb(rarity.color.rgb)).withBold(true)
        }

    private const val CRITTER_GROUP = "critter"
    private const val CRITTER_CAPSULE_ID = "CRITTER_CAPSULE"
    private const val AIM_LINE_WIDTH = 3
    private val THROW_MESSAGE = Regex("""^You threw a Critter Capsule at the (?<critter>.+)!$""")
    private val ESCAPE_MESSAGE = Regex("""^The (?<critter>.+) escaped your Critter Capsule!$""")
    private val CAPTURE_MESSAGE = Regex("""^CAPTURE! You caught an? (?<critter>.+) and gained .+!$""")
    private val AIM_COLOR = Color(255, 85, 85, 235)
    private val AIM_RADIUS = WorldVec(0.18, 0.18, 0.18)
    private val AIM_X = WorldVec(0.38, 0.0, 0.0)
    private val AIM_Y = WorldVec(0.0, 0.38, 0.0)
    private val AIM_Z = WorldVec(0.0, 0.0, 0.38)
    private val LABEL_OFFSET = WorldVec(0.0, 0.55, 0.0)
    private val LABEL_STYLE = WorldLabelStyle(displayMode = Font.DisplayMode.NORMAL)
}
