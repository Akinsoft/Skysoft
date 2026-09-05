package com.skysoft.features.hunting

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SoundUtilities
import com.skysoft.utils.TextUtilities.formattedText
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand

object LassoDisplay {
    private val config get() = SkysoftConfigGui.config().hunting.lassoDisplay
    private val barPattern = Regex("(?:§[659aef]§l§m {1,20})?(?:§8§l§m {1,20})?")
    private const val BAR_LENGTH = 20
    private const val BAR_HORIZONTAL_RANGE_SQUARED = 3.0 * 3.0
    private const val REEL_RANGE_SQUARED = 2.0 * 2.0
    private const val REEL_TEXT = "§e§lREEL"
    private var bar: ArmorStand? = null
    private var reel: ArmorStand? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Lasso Display tick",
            isActive = { isEnabled() || bar != null || reel != null },
        ) { update() }
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "lasso_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ ->
                    if (!MinecraftClient.isGuiHidden(Minecraft.getInstance())) {
                        currentRenderable()?.let { config.position.renderRenderable(context, it) }
                    }
                },
            ),
            object : HudEditorElement {
                override val id = "lasso_display"
                override val label = "Lasso Display"
                override val position get() = config.position
                override val hasEditorBackground get() = !config.details.background
                override fun width(): Int = currentRenderable()?.width ?: 0
                override fun height(): Int = currentRenderable()?.height ?: 0
                override fun isVisible(): Boolean = currentRenderable() != null
                override fun renderEditor(context: GuiGraphicsExtractor) {
                    currentRenderable()?.render(context)
                }
                override fun openConfig() = SkysoftConfigGui.open("Lasso Display")
            },
        )
    }

    @JvmStatic
    fun adjustNameTag(entity: Entity, state: EntityRenderState) {
        if (!isEnabled() || entity !is ArmorStand || !entity.isMarker) return
        if (!entity.isStaminaBar() && entity.customName?.formattedText() != REEL_TEXT) return
        if (entity !== bar && entity !== reel) update()
        if (currentBar() != null && (entity === bar || entity === reel)) state.nameTag = null
    }

    private fun update() {
        if (!isEnabled()) {
            bar = null
            reel = null
            return
        }
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return
        if (currentBar() == null) {
            bar = null
            reel = null
        }
        reel = reel?.takeIf {
            it.isAlive && it.level() === level && it.customName?.formattedText() == REEL_TEXT
        }
        if (bar != null && reel != null) return
        val entities = level.entitiesForRendering().toList()
        val stands = entities.filterIsInstance<ArmorStand>().filter { it.isAlive && it.isMarker }
        val bars = stands.filter { it.isStaminaBar() }
        if (bar == null) {
            val bats = entities.filterIsInstance<Bat>().filter { it.isAlive && it.isInvisible && it.leashHolder != null }
            bar = bars.singleOrNull { candidate ->
                candidate.closestLassoBat(bats)?.leashHolder === player
            }
        }
        val currentBar = bar ?: return
        reel = stands.singleOrNull { candidate ->
            candidate.customName?.formattedText() == REEL_TEXT &&
                bars.filter { candidate.distanceToSqr(it) <= REEL_RANGE_SQUARED }
                    .minByOrNull { candidate.distanceToSqr(it) } === currentBar
        }
        if (reel != null && config.settings.reelSound) {
            SoundUtilities.playUiSound("block.note_block.pling", 1f, 1f)
        }
    }

    private fun ArmorStand.closestLassoBat(bats: List<Bat>): Bat? = bats
        .filter {
            val dx = x - it.x
            val dz = z - it.z
            dx * dx + dz * dz <= BAR_HORIZONTAL_RANGE_SQUARED && y - it.y in 0.0..3.0
        }
        .minByOrNull { distanceToSqr(it) }

    private fun currentBar(): ArmorStand? = bar?.takeIf {
        it.isAlive && it.level() === Minecraft.getInstance().level && it.isStaminaBar()
    }

    private fun currentRenderable(): GuiRenderable? {
        if (!isEnabled()) return null
        val barText = currentBar()?.customName?.formattedText() ?: return null
        val reelText = reel?.takeIf { it.isAlive }?.customName?.formattedText()?.takeIf { it == REEL_TEXT }.orEmpty()
        return verticalLayout(
            listOf(
                StringRenderable(reelText, horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER),
                StringRenderable(barText),
            ),
        ).withOverlayPanel(config.details.background)
    }

    private fun ArmorStand.isStaminaBar(): Boolean {
        val name = customName ?: return false
        return isMarker && name.string == " ".repeat(BAR_LENGTH) && barPattern.matches(name.formattedText())
    }

    private fun isEnabled(): Boolean = config.enabled && HypixelLocationState.inSkyBlock
}
