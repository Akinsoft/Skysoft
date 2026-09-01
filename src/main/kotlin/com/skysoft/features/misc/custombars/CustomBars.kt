package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.CustomBarDisplayMode
import com.skysoft.config.CustomBarIconPosition
import com.skysoft.config.CustomBarsSettingsConfig
import com.skysoft.config.CustomElementDetailsConfig
import com.skysoft.config.CustomReadoutDetailsConfig
import com.skysoft.config.CustomResourceBarDetailsConfig
import com.skysoft.config.core.HudDimensions
import com.skysoft.config.core.HudPosition
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.TabListApi
import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.HudEditorSnapshot
import com.skysoft.gui.hudEditorSnapshot
import com.skysoft.features.inventory.InventoryHudLayout
import com.skysoft.features.misc.AbsorptionHeartLayout
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.features.misc.actionbar.NormalizedActionBar
import com.skysoft.features.misc.actionbar.actionBarSegmentRange
import com.skysoft.features.misc.actionbar.withoutRanges
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.DurationParts
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.formatInt
import com.skysoft.utils.NumberUtilities.shortFormat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.input.InputHandlingResult
import com.skysoft.utils.render.drawGlossyProgressBar
import com.skysoft.utils.render.fillGlossyRect
import com.skysoft.utils.render.fillGlossyRoundedRect
import com.skysoft.utils.render.fillRoundedRect
import com.skysoft.utils.render.roundedRectVerticalInset
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.renderRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.observer.Property
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.ceil
import kotlin.math.roundToInt

object CustomBars {
    private val config get() = SkysoftConfigGui.config().gui.customBars
    private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()
    private var health: BarValue? = null
    private var mana: BarValue? = null
    private var vitality: BarValue? = null
    private var defense: Int? = null
    private var riftDamage: Int? = null
    private var trackedRiftState: Boolean? = null
    private val textElements by lazy {
        CustomBarPart.entries.filter(CustomBarPart::isResource).map(::CustomBarTextEditorElement)
    }

    fun register() {
        ChatEvents.onActionBar("Custom Bars tracking", ::isActive) { message ->
            update(CustomBarsActionBarParser.parse(message.plainText))
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onActionBarModify("Custom Bars action bar", ::isActive) { message ->
            message.component.withoutRanges(
                CustomBarsActionBarParser.parse(message.plainText).ranges(
                    CustomBarStatus.hiddenBy(config.settings, inRift),
                ),
            )
        }
        SkysoftClientEvents.onDisconnect("Custom Bars reset", ::reset)
        TabListApi.onChange(
            "Custom Bars",
            isActive = {
                isActive() && inRift && config.settings.displays.defense == CustomBarDisplayMode.CUSTOM
            },
            listener = ::updateRiftDamage,
        )
        registerVanillaReplacements()
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "custom_bars",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                visible = { isActive() && !MinecraftClient.isGuiHidden(Minecraft.getInstance()) },
                render = { context, _ -> renderParts(context) },
            ),
        )
        CustomBarPart.entries.forEach { part ->
            HudEditorRegistry.register(object : HudEditorElement {
                override val id: String = "custom_bars_${part.name.lowercase()}"
                override val label: String = part.label
                override val position get() = part.position()
                override val snapGroup: String = id
                override val layoutOffsetX: Int get() = part.layoutOffsetX()
                override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
                override val hasEditorBackground: Boolean = false
                override val canScale: Boolean get() = !part.usesVanillaDisplay() && part.dimensions == null
                override val canResizeWidth: Boolean get() = part.editorDimensions() != null
                override val canResizeHeight: Boolean get() = part.editorDimensions() != null
                override fun width(): Int = part.width
                override fun height(): Int = part.height
                override fun isVisible(): Boolean = config.enabled && part.isEditorVisible()
                override fun renderEditor(context: GuiGraphicsExtractor) {
                    if (part.usesVanillaDisplay()) renderVanillaPreview(context, part)
                    else renderable(part, previewAir = part == CustomBarPart.AIR).render(context)
                }
                override fun resizeEditor(width: Int, height: Int) = part.resize(width, height)
                override fun minEditorWidth(): Int = MIN_RESOURCE_WIDTH
                override fun minEditorHeight(): Int = MIN_RESOURCE_HEIGHT
                override fun resetEditorState() {
                    super.resetEditorState()
                    part.editorDimensions()?.resetToDefault()
                }
                override fun captureEditorState(): HudEditorSnapshot {
                    val position = part.position()
                    val positionSnapshot = position.snapshot()
                    val dimensions = part.editorDimensions()
                    val dimensionsSnapshot = dimensions?.snapshot()
                    return hudEditorSnapshot(positionSnapshot to dimensionsSnapshot) {
                        position.restore(positionSnapshot)
                        if (dimensions != null && dimensionsSnapshot != null) dimensions.restore(dimensionsSnapshot)
                    }
                }
                override fun openConfig() = SkysoftConfigGui.open("Custom Bars")
            })
        }
        textElements.forEach(HudEditorRegistry::register)
    }

    private fun registerVanillaReplacements() {
        replaceVanilla(VanillaHudElements.HEALTH_BAR, CustomBarPart.HEALTH) {
            config.settings.displays.health == CustomBarDisplayMode.VANILLA
        }
        replaceVanilla(VanillaHudElements.EXPERIENCE_LEVEL, CustomBarPart.EXPERIENCE) {
            config.settings.displays.experience == CustomBarDisplayMode.VANILLA &&
                !config.settings.numbers.experience
        }
        replaceVanilla(VanillaHudElements.AIR_BAR, CustomBarPart.AIR) {
            config.settings.displays.air == CustomBarDisplayMode.VANILLA
        }
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR) { vanilla ->
            HudElement { context, tick ->
                if (!isActive()) vanilla.extractRenderState(context, tick)
            }
        }
    }

    private fun replaceVanilla(id: Identifier, part: CustomBarPart, shouldRender: () -> Boolean) {
        HudElementRegistry.replaceElement(id) { vanilla ->
            HudElement { context, tick ->
                renderVanillaDisplay(context, part, shouldRender) {
                    vanilla.extractRenderState(context, tick)
                }
            }
        }
    }

    internal fun renderVanillaExperienceBar(context: GuiGraphicsExtractor, render: () -> Unit) {
        renderVanillaDisplay(context, CustomBarPart.EXPERIENCE, {
            config.settings.displays.experience == CustomBarDisplayMode.VANILLA
        }, render)
    }

    private fun renderVanillaDisplay(
        context: GuiGraphicsExtractor,
        part: CustomBarPart,
        shouldRender: () -> Boolean,
        render: () -> Unit,
    ) {
        if (!isActive()) {
            render()
        } else if (shouldRender()) {
            renderPositionedVanillaDisplay(context, part, render)
        }
    }

    private fun renderPositionedVanillaDisplay(
        context: GuiGraphicsExtractor,
        part: CustomBarPart,
        render: () -> Unit,
    ) {
        val width = part.vanillaWidth()
        val height = part.vanillaHeight()
        val position = part.position()
        val scale = position.effectiveScale
        val targetX = position.getAbsX0AllowingOverflow((width * scale).roundToInt())
        val targetY = position.getAbsY0AllowingOverflow((height * scale).roundToInt())
        val sourceX: Int
        val sourceY: Int
        when (part) {
            CustomBarPart.HEALTH -> {
                sourceX = context.guiWidth() / 2 - VANILLA_HUD_HALF_WIDTH
                sourceY = context.guiHeight() - VANILLA_HEALTH_TOP_OFFSET - (height - ICON_SIZE)
            }
            CustomBarPart.EXPERIENCE -> {
                sourceX = (context.guiWidth() - VANILLA_EXPERIENCE_WIDTH) / 2
                sourceY = context.guiHeight() - VANILLA_EXPERIENCE_TOP_OFFSET
            }
            CustomBarPart.AIR -> {
                sourceX = context.guiWidth() / 2 + VANILLA_AIR_LEFT_OFFSET
                sourceY = context.guiHeight() - VANILLA_AIR_TOP_OFFSET
            }
            else -> error("${part.label} has no vanilla position")
        }
        context.withIsolatedPose {
            pose().translate(targetX.toFloat(), targetY.toFloat())
            pose().scale(scale, scale)
            pose().translate(-sourceX.toFloat(), -sourceY.toFloat())
            render()
        }
    }

    private fun renderVanillaPreview(context: GuiGraphicsExtractor, part: CustomBarPart) {
        when (part) {
            CustomBarPart.HEALTH -> renderVanillaHealthPreview(context)
            CustomBarPart.EXPERIENCE -> {
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    VANILLA_EXPERIENCE_BACKGROUND_SPRITE,
                    0,
                    VANILLA_EXPERIENCE_BAR_Y,
                    VANILLA_EXPERIENCE_WIDTH,
                    VANILLA_EXPERIENCE_BAR_HEIGHT,
                )
                SkyBlockLevelBar.renderVanillaExperienceProgress(
                    context,
                    0,
                    VANILLA_EXPERIENCE_BAR_Y,
                    VANILLA_EXPERIENCE_PREVIEW_PROGRESS,
                ) {
                    context.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        VANILLA_EXPERIENCE_PROGRESS_SPRITE,
                        VANILLA_EXPERIENCE_WIDTH,
                        VANILLA_EXPERIENCE_BAR_HEIGHT,
                        0,
                        0,
                        0,
                        VANILLA_EXPERIENCE_BAR_Y,
                        VANILLA_EXPERIENCE_PREVIEW_PROGRESS,
                        VANILLA_EXPERIENCE_BAR_HEIGHT,
                    )
                }
                if (!config.settings.numbers.experience) drawVanillaExperienceLevelPreview(context)
            }
            CustomBarPart.AIR -> repeat(VANILLA_STATUS_ICON_COUNT) { index ->
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    AIR_SPRITE,
                    index * VANILLA_STATUS_ICON_SPACING,
                    0,
                    ICON_SIZE,
                    ICON_SIZE,
                )
            }
            else -> error("${part.label} has no vanilla preview")
        }
    }

    private fun renderVanillaHealthPreview(context: GuiGraphicsExtractor) {
        val layout = vanillaHealthLayout()
        for (index in layout.totalContainers - 1 downTo 0) {
            val x = index % VANILLA_STATUS_ICON_COUNT * VANILLA_STATUS_ICON_SPACING
            val y = (layout.rowCount - 1 - index / VANILLA_STATUS_ICON_COUNT) * layout.rowHeight
            context.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_HEART_CONTAINER_SPRITE, x, y, ICON_SIZE, ICON_SIZE)
            val halves = index * 2
            val sprite = if (index >= layout.healthContainers) {
                val absorptionHalves = halves - layout.healthContainers * 2
                when {
                    absorptionHalves + 1 == layout.absorption -> VANILLA_ABSORPTION_HALF_SPRITE
                    absorptionHalves < layout.absorption -> VANILLA_ABSORPTION_FULL_SPRITE
                    else -> null
                }
            } else {
                when {
                    halves + 1 == layout.currentHealth -> VANILLA_HEART_HALF_SPRITE
                    halves < layout.currentHealth -> VANILLA_HEART_FULL_SPRITE
                    else -> null
                }
            }
            if (sprite != null) context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON_SIZE, ICON_SIZE)
        }
    }

    private fun drawVanillaExperienceLevelPreview(context: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font
        val x = (VANILLA_EXPERIENCE_WIDTH - font.width(VANILLA_EXPERIENCE_PREVIEW_LEVEL)) / 2
        context.text(font, VANILLA_EXPERIENCE_PREVIEW_LEVEL, x + 1, 0, TEXT_OUTLINE_RGB, false)
        context.text(font, VANILLA_EXPERIENCE_PREVIEW_LEVEL, x - 1, 0, TEXT_OUTLINE_RGB, false)
        context.text(font, VANILLA_EXPERIENCE_PREVIEW_LEVEL, x, 1, TEXT_OUTLINE_RGB, false)
        context.text(font, VANILLA_EXPERIENCE_PREVIEW_LEVEL, x, -1, TEXT_OUTLINE_RGB, false)
        context.text(
            font,
            VANILLA_EXPERIENCE_PREVIEW_LEVEL,
            x,
            0,
            SkyBlockLevelBar.displayedExperienceLevelColor(VANILLA_EXPERIENCE_LEVEL_RGB),
            false,
        )
    }

    private fun vanillaHealthLayout(): VanillaHealthLayout {
        val player = Minecraft.getInstance().player
        val absorption = ceil(player?.absorptionAmount?.toDouble() ?: 0.0).toInt()
        val vanillaCurrentHealth = ceil(player?.health?.toDouble() ?: VANILLA_DEFAULT_HEALTH.toDouble()).toInt()
        val vanillaMaximumHealth = maxOf(player?.maxHealth ?: VANILLA_DEFAULT_HEALTH, vanillaCurrentHealth.toFloat())
        val currentHealth = AbsorptionHeartLayout.resolveVisibleHealth(vanillaCurrentHealth, player)
        val maximumHealth = AbsorptionHeartLayout.resolveMaximumHealth(vanillaMaximumHealth, player)
        return VanillaHealthLayout.create(currentHealth, maximumHealth, absorption)
    }

    private fun isActive(): Boolean {
        if (!config.enabled || !HypixelLocationState.inSkyBlock) return false
        val currentRiftState = inRift
        if (trackedRiftState != currentRiftState) {
            reset()
            trackedRiftState = currentRiftState
        }
        return true
    }

    private fun update(parsed: ParsedCustomBarActionBar) {
        parsed.health?.let { health = it }
        parsed.mana?.let { mana = it }
        parsed.vitality?.let { vitality = it }
        parsed.defense?.let { defense = it }
    }

    private fun reset() {
        health = null
        mana = null
        vitality = null
        defense = null
        riftDamage = null
    }

    private fun displayedHealth(): BarValue? {
        if (!inRift) return health
        val player = Minecraft.getInstance().player ?: return null
        return BarValue(
            ceil(player.health.toDouble()).toInt(),
            ceil(player.maxHealth.toDouble()).toInt(),
        )
    }

    private fun updateRiftDamage() {
        riftDamage = RiftCustomBarValues.parseDamage(TabListApi.skyBlockLines.map { it.cleanSkyBlockText() })
    }

    private fun displayedDefense(): Int? = if (inRift) riftDamage else defense

    private enum class CustomBarPart(val label: String) {
        HEALTH("Health Bar"),
        MANA("Mana Bar"),
        VITALITY("Vitality Bar"),
        EXPERIENCE("Experience Bar"),
        DEFENSE("Defense"),
        SPEED("Speed"),
        AIR("Air"),
        ;

        val defaultWidth: Int
            get() = when (this) {
                HEALTH, MANA -> (resourceRowWidth() - BAR_GAP) / 2
                VITALITY -> vitalityResourceWidth()
                EXPERIENCE -> resourceRowWidth() - BAR_GAP - vitalityResourceWidth()
                DEFENSE, SPEED, AIR -> READOUT_WIDTH
            }

        val dimensions: HudDimensions?
            get() = when (this) {
                HEALTH -> config.healthDimensions
                MANA -> config.manaDimensions
                VITALITY -> config.vitalityDimensions
                EXPERIENCE -> config.experienceDimensions
                DEFENSE, SPEED, AIR -> null
            }

        val width: Int
            get() = if (usesVanillaDisplay()) vanillaWidth() else dimensions?.width(defaultWidth, MIN_RESOURCE_WIDTH)
                ?: defaultWidth
        val height: Int
            get() = if (usesVanillaDisplay()) vanillaHeight() else dimensions?.height(
                RESOURCE_HEIGHT,
                MIN_RESOURCE_HEIGHT,
            ) ?: READOUT_ELEMENT_HEIGHT
        val isResource: Boolean get() = dimensions != null
        val iconSlotWidth: Int
            get() = if (!usesVanillaDisplay() && visualDetails.showIcon) ICON_SLOT_WIDTH else 0
        val trackX: Int get() = if (config.details.icons == CustomBarIconPosition.LEFT) iconSlotWidth else 0
        val trackWidth: Int get() = width - iconSlotWidth
        val iconX: Int
            get() = if (config.details.icons == CustomBarIconPosition.RIGHT) width - ICON_SLOT_WIDTH else 0
        val visualDetails: CustomElementDetailsConfig
            get() = when (this) {
                HEALTH -> config.details.health
                MANA -> config.details.mana
                VITALITY -> config.details.vitality
                EXPERIENCE -> config.details.experience
                DEFENSE -> config.details.defense
                SPEED -> config.details.speed
                AIR -> config.details.air
            }

        fun isCustomVisible(): Boolean = isAvailable && when (this) {
            HEALTH -> config.settings.displays.health == CustomBarDisplayMode.CUSTOM
            MANA -> config.settings.displays.mana == CustomBarDisplayMode.CUSTOM
            VITALITY -> config.settings.displays.vitality == CustomBarDisplayMode.CUSTOM
            EXPERIENCE -> config.settings.displays.experience == CustomBarDisplayMode.CUSTOM
            DEFENSE -> config.settings.displays.defense == CustomBarDisplayMode.CUSTOM
            SPEED -> config.settings.displays.speed
            AIR -> config.settings.displays.air == CustomBarDisplayMode.CUSTOM
        }

        fun isEditorVisible(): Boolean = isCustomVisible() || usesVanillaDisplay()

        fun usesVanillaDisplay(): Boolean = when (this) {
            HEALTH -> config.settings.displays.health == CustomBarDisplayMode.VANILLA
            EXPERIENCE -> config.settings.displays.experience == CustomBarDisplayMode.VANILLA
            AIR -> config.settings.displays.air == CustomBarDisplayMode.VANILLA
            MANA, VITALITY, DEFENSE, SPEED -> false
        }

        fun isNumberVisible(): Boolean = isAvailable && when (this) {
            HEALTH -> config.settings.numbers.health
            MANA -> config.settings.numbers.mana
            VITALITY -> config.settings.numbers.vitality
            EXPERIENCE -> config.settings.numbers.experience
            DEFENSE, SPEED, AIR -> error("$label does not have separate number text")
        }

        private val isAvailable: Boolean get() = !inRift || this != VITALITY

        fun position(): HudPosition = if (usesVanillaDisplay()) {
            when (this) {
                HEALTH -> config.vanillaHealthPosition
                EXPERIENCE -> config.vanillaExperiencePosition
                AIR -> config.vanillaAirPosition
                MANA, VITALITY, DEFENSE, SPEED -> error("$label has no vanilla position")
            }
        } else {
            when (this) {
                HEALTH -> config.healthPosition
                MANA -> config.manaPosition
                VITALITY -> config.vitalityPosition
                EXPERIENCE -> config.experiencePosition
                DEFENSE -> config.defensePosition
                SPEED -> config.speedPosition
                AIR -> config.airPosition
            }
        }

        fun textPosition(): HudPosition = when (this) {
            HEALTH -> config.healthTextPosition
            MANA -> config.manaTextPosition
            VITALITY -> config.vitalityTextPosition
            EXPERIENCE -> config.experienceTextPosition
            DEFENSE, SPEED, AIR -> error("$label does not have movable bar text")
        }

        fun editorDimensions(): HudDimensions? = dimensions.takeUnless { usesVanillaDisplay() }

        fun vanillaWidth(): Int = when (this) {
            HEALTH, AIR -> VANILLA_STATUS_WIDTH
            EXPERIENCE -> VANILLA_EXPERIENCE_WIDTH
            MANA, VITALITY, DEFENSE, SPEED -> error("$label has no vanilla width")
        }

        fun vanillaHeight(): Int = when (this) {
            HEALTH -> vanillaHealthLayout().height
            AIR -> ICON_SIZE
            EXPERIENCE -> VANILLA_EXPERIENCE_HEIGHT
            MANA, VITALITY, DEFENSE, SPEED -> error("$label has no vanilla height")
        }

        fun resize(width: Int, height: Int) {
            editorDimensions()?.resize(width, height, defaultWidth, RESOURCE_HEIGHT)
        }

        fun layoutOffsetX(): Int {
            if (usesVanillaDisplay() || !usesInventoryHudWidth()) return 0
            val oldWidth = when (this) {
                HEALTH, MANA -> HALF_RESOURCE_WIDTH
                VITALITY -> VITALITY_RESOURCE_WIDTH
                EXPERIENCE -> EXPERIENCE_RESOURCE_WIDTH
                DEFENSE, SPEED, AIR -> return 0
            }
            val widthChange = halfRoundedUp(width) - halfRoundedUp(oldWidth)
            val rowInset = (HOTBAR_WIDTH - resourceRowWidth()) / 2
            return when (this) {
                HEALTH, VITALITY -> rowInset + widthChange
                MANA, EXPERIENCE -> -rowInset - widthChange
                DEFENSE, SPEED, AIR -> 0
            }
        }
    }

    private fun renderParts(context: GuiGraphicsExtractor) {
        context.withIsolatedPose {
            pose().translate(0f, -BottomHudLayout.reservedHeight().toFloat())
            for (part in CustomBarPart.entries) {
                if (part.isCustomVisible() &&
                    (part != CustomBarPart.AIR || Minecraft.getInstance().player?.isUnderWater == true)
                ) {
                    context.withIsolatedPose {
                        pose().translate(part.layoutOffsetX().toFloat(), 0f)
                        part.position().renderRenderable(context, renderable(part))
                    }
                }
            }
            textElements.filter(CustomBarTextEditorElement::isVisible).forEach { it.renderLive(context) }
        }
    }

    private fun renderable(part: CustomBarPart, previewAir: Boolean = false): GuiRenderable =
        CustomBarsRenderable(part, displayedHealth(), mana, vitality, displayedDefense(), previewAir)

    private class CustomBarsRenderable(
        private val part: CustomBarPart,
        private val health: BarValue?,
        private val mana: BarValue?,
        private val vitality: BarValue?,
        private val defense: Int?,
        private val previewAir: Boolean,
    ) : GuiRenderable {
        override val width: Int = part.width
        override val height: Int = part.height

        override fun render(context: GuiGraphicsExtractor) {
            val player = Minecraft.getInstance().player
            when (part) {
                CustomBarPart.HEALTH -> drawBar(
                    context,
                    health,
                    config.details.health,
                    if (inRift) SkyBlockStatGlyph.HEARTS.toString() else SkyBlockStatGlyph.HEALTH.toString(),
                )
                CustomBarPart.MANA -> drawBar(
                    context,
                    mana,
                    config.details.mana,
                    SkyBlockStatGlyph.INTELLIGENCE.toString(),
                )
                CustomBarPart.VITALITY -> drawBar(
                    context,
                    vitality,
                    config.details.vitality,
                    SkyBlockStatGlyph.VITALITY.toString(),
                )
                CustomBarPart.EXPERIENCE -> {
                    val details = config.details.experience
                    drawExperienceIcon(context, part.iconX, resourceBarHeight())
                    context.drawGlossyProgressBar(
                        part.trackX,
                        RESOURCE_BAR_Y,
                        part.trackWidth,
                        resourceBarHeight(),
                        SkyBlockLevelBar.displayedExperienceProgress(player?.experienceProgress ?: 0f),
                        SkyBlockLevelBar.displayedExperienceBarColor(details.barColor.rgb()),
                        details.backgroundColor.rgb(),
                    )
                }
                CustomBarPart.DEFENSE -> drawReadout(
                    context,
                    if (inRift) SkyBlockStatGlyph.RIFT_DAMAGE.toString() else SkyBlockStatGlyph.DEFENSE.toString(),
                    defense?.addSeparators() ?: "---",
                    config.details.defense,
                )
                CustomBarPart.SPEED -> drawReadout(
                    context,
                    SkyBlockStatGlyph.SPEED.toString(),
                    player?.skyBlockSpeed()?.addSeparators() ?: "---",
                    config.details.speed,
                )
                CustomBarPart.AIR -> {
                    val remainingTicks = if (previewAir && player?.isUnderWater != true) {
                        PREVIEW_AIR_TICKS
                    } else {
                        player?.airSupply ?: 0
                    }
                    drawAirReadout(
                        context,
                        remainingTicks.coerceAtLeast(0) / TICKS_PER_SECOND,
                        config.details.air,
                    )
                }
            }
        }

        private fun drawBar(
            context: GuiGraphicsExtractor,
            value: BarValue?,
            details: CustomResourceBarDetailsConfig,
            icon: String,
        ) {
            if (part.iconSlotWidth > 0) {
                val iconY = RESOURCE_BAR_Y + (resourceBarHeight() - Minecraft.getInstance().font.lineHeight) / 2
                drawIcon(context, icon, part.iconX, iconY, details.iconColor.rgb())
            }
            drawResourceBar(
                context,
                part.trackX,
                RESOURCE_BAR_Y,
                part.trackWidth,
                resourceBarHeight(),
                value,
                details.barColor.rgb(),
                details.overflowColor.rgb(),
                details.backgroundColor.rgb(),
            )
        }

        private fun drawResourceBar(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            value: BarValue?,
            color: Int,
            overflowColor: Int,
            backgroundColor: Int,
        ) {
            context.fillRoundedRect(x, y, width, height, backgroundColor)
            if (value == null) return
            val innerWidth = width - INNER_PADDING * 2
            val capacity = value.maximum + value.displayOverflow
            val totalWidth = (innerWidth * value.displayedCurrent.toFloat() / capacity.coerceAtLeast(1))
                .roundToInt()
                .coerceIn(0, innerWidth)
            if (totalWidth == 0) return
            val baseWidth = (totalWidth * value.regularCurrent.toFloat() / value.displayedCurrent.coerceAtLeast(1))
                .roundToInt()
                .coerceIn(0, totalWidth)
            val overflowWidth = totalWidth - baseWidth
            val fillX = x + INNER_PADDING
            val fillY = y + INNER_PADDING
            val fillHeight = height - INNER_PADDING * 2
            if (baseWidth > 0) context.fillGlossyRoundedRect(fillX, fillY, baseWidth, fillHeight, color)
            if (overflowWidth > 0) {
                context.fillGlossyRoundedRect(fillX + baseWidth, fillY, overflowWidth, fillHeight, overflowColor)
            }
            if (baseWidth > 0 && overflowWidth > 0) {
                val baseTransitionWidth = OVERFLOW_TRANSITION_HALF_WIDTH.coerceAtMost(baseWidth)
                val overflowTransitionWidth = OVERFLOW_TRANSITION_HALF_WIDTH.coerceAtMost(overflowWidth)
                val transitionWidth = baseTransitionWidth + overflowTransitionWidth
                val transitionX = fillX + baseWidth - baseTransitionWidth
                repeat(transitionWidth) { offset ->
                    val colorProgress = offset.toFloat() / (transitionWidth - 1)
                    val barOffset = baseWidth - baseTransitionWidth + offset
                    val verticalInset = roundedRectVerticalInset(barOffset, totalWidth, fillHeight)
                    context.fillGlossyRect(
                        transitionX + offset,
                        fillY + verticalInset,
                        1,
                        fillHeight - verticalInset * 2,
                        ARGB.srgbLerp(colorProgress, color, overflowColor),
                    )
                }
            }
        }

        private fun drawIcon(
            context: GuiGraphicsExtractor,
            icon: String,
            x: Int,
            y: Int,
            color: Int,
        ) {
            drawText(context, icon, x, y, color)
        }

        private fun drawExperienceIcon(context: GuiGraphicsExtractor, x: Int, barHeight: Int) {
            if (part.iconSlotWidth == 0) return
            if (inRift && !SkyBlockLevelBar.isReplacingExperience) {
                val y = RESOURCE_BAR_Y + (barHeight - Minecraft.getInstance().font.lineHeight) / 2
                drawIcon(
                    context,
                    SkyBlockStatGlyph.RIFT_TIME.toString(),
                    x,
                    y,
                    config.details.experience.barColor.rgb(),
                )
            } else {
                val y = RESOURCE_BAR_Y + (barHeight - EXPERIENCE_ICON_SIZE) / 2
                EXPERIENCE_ICON.renderAt(context, x + EXPERIENCE_ICON_X, y)
            }
        }

        private fun drawText(context: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
            drawCustomBarText(context, text, x, y, color)
        }

        private fun resourceBarHeight(): Int =
            (height - RESOURCE_BAR_Y - RESOURCE_BOTTOM_PADDING).coerceAtLeast(MIN_TRACK_HEIGHT)

        private fun drawReadout(
            context: GuiGraphicsExtractor,
            icon: String,
            value: String,
            details: CustomReadoutDetailsConfig,
        ) {
            val font = Minecraft.getInstance().font
            val iconWidth = if (details.showIcon) font.width(icon) + READOUT_CONTENT_GAP else 0
            val contentWidth = iconWidth + font.width(value)
            val contentX = centeredReadoutX(contentWidth)
            context.fillRoundedRect(
                0,
                READOUT_BACKGROUND_Y,
                READOUT_WIDTH,
                READOUT_HEIGHT,
                details.backgroundColor.rgb(),
            )
            if (details.showIcon) drawIcon(context, icon, contentX, READOUT_CONTENT_Y, details.iconColor.rgb())
            drawText(
                context,
                value,
                contentX + iconWidth,
                READOUT_CONTENT_Y,
                details.textColor.rgb(),
            )
        }

        private fun drawAirReadout(
            context: GuiGraphicsExtractor,
            seconds: Int,
            details: CustomReadoutDetailsConfig,
        ) {
            val text = "${seconds}s"
            val font = Minecraft.getInstance().font
            val iconWidth = if (details.showIcon) ICON_SIZE + READOUT_CONTENT_GAP else 0
            val contentWidth = iconWidth + font.width(text)
            val contentX = centeredReadoutX(contentWidth)
            context.fillRoundedRect(
                0,
                READOUT_BACKGROUND_Y,
                READOUT_WIDTH,
                READOUT_HEIGHT,
                details.backgroundColor.rgb(),
            )
            if (details.showIcon) {
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    AIR_SPRITE,
                    contentX,
                    READOUT_CONTENT_Y,
                    ICON_SIZE,
                    ICON_SIZE,
                    details.iconColor.rgb(),
                )
            }
            drawText(
                context,
                text,
                contentX + iconWidth,
                READOUT_CONTENT_Y,
                details.textColor.rgb(),
            )
        }

        private fun centeredReadoutX(contentWidth: Int): Int =
            ((READOUT_WIDTH - contentWidth) / 2f).roundToInt()
    }

    private class CustomBarTextEditorElement(private val part: CustomBarPart) : HudEditorElement {
        override val id: String = "custom_bars_${part.name.lowercase()}_text"
        override val label: String = "${part.label} Text"
        override val position: HudPosition get() = part.textPosition()
        override val snapGroup: String = "custom_bars_${part.name.lowercase()}"
        override val canMove: Boolean = false
        override val canScale: Boolean = false
        override val hasEditorBackground: Boolean = false

        override fun width(): Int = Minecraft.getInstance().font.width(text())
        override fun height(): Int = Minecraft.getInstance().font.lineHeight
        override fun isVisible(): Boolean = config.enabled && part.isNumberVisible()

        override fun absoluteX(width: Int): Int {
            val barScale = part.position().effectiveScale
            val barWidth = (part.width * barScale).roundToInt()
            val barX = part.layoutOffsetX() + part.position().getAbsX0AllowingOverflow(barWidth)
            val trackCenter = ((part.trackX + part.trackWidth / 2f) * barScale).roundToInt()
            return barX + trackCenter - width / 2 + position.x
        }

        override fun absoluteY(height: Int): Int =
            barY(includeBottomLayout = true) + (RESOURCE_TEXT_Y * part.position().effectiveScale).roundToInt() + position.y

        override fun renderEditor(context: GuiGraphicsExtractor) {
            val color = if (part == CustomBarPart.EXPERIENCE) {
                SkyBlockLevelBar.displayedExperienceLevelColor(part.visualDetails.textColor.rgb())
            } else {
                part.visualDetails.textColor.rgb()
            }
            drawCustomBarText(context, text(), 0, 0, color)
        }

        override fun applyEditorDrag(deltaX: Int, deltaY: Int): InputHandlingResult {
            position.moveBy(deltaX, deltaY)
            return InputHandlingResult.CONSUMED
        }

        override fun applyEditorScroll(scrollY: Double): InputHandlingResult {
            position.scale += if (scrollY > 0.0) TEXT_SCALE_STEP else -TEXT_SCALE_STEP
            return InputHandlingResult.CONSUMED
        }

        override fun editorDetailsLines(): List<String> = listOf(
            "§7Offset x: §e${position.x}§7, y: §e${position.y}§7, scale: §e${
                "%.2f".format(java.util.Locale.US, position.scale)
            }",
        )

        override fun editorActionLines(): List<String> = listOf(
            "§eLeft-click drag §7to move",
            "§eScroll-Wheel §7to resize",
            "§eHold Shift §7to snap",
            "§eRight-click §7to open settings",
            "§eR §7to reset",
        )

        override fun openConfig() = SkysoftConfigGui.open("Custom Bars")

        fun renderLive(context: GuiGraphicsExtractor) {
            val scaledWidth = (width() * position.effectiveScale).roundToInt()
            val x = absoluteX(scaledWidth)
            val y = barY(includeBottomLayout = false) +
                (RESOURCE_TEXT_Y * part.position().effectiveScale).roundToInt() +
                position.y
            context.withIsolatedPose {
                pose().translate(x.toFloat(), y.toFloat())
                pose().scale(position.effectiveScale, position.effectiveScale)
                renderEditor(context)
            }
        }

        private fun barY(includeBottomLayout: Boolean): Int {
            val barHeight = (part.height * part.position().effectiveScale).roundToInt()
            val y = part.position().getAbsY0AllowingOverflow(barHeight)
            return if (includeBottomLayout) y - BottomHudLayout.reservedHeight() else y
        }

        private fun text(): String = when (part) {
            CustomBarPart.HEALTH -> if (inRift) RiftCustomBarValues.formatHearts(displayedHealth()) else resourceText(
                health,
                part.trackWidth,
            )
            CustomBarPart.MANA -> resourceText(mana, part.trackWidth)
            CustomBarPart.VITALITY -> resourceText(vitality, part.trackWidth)
            CustomBarPart.EXPERIENCE -> if (inRift && !SkyBlockLevelBar.isReplacingExperience) {
                RiftCustomBarValues.formatTime(Minecraft.getInstance().player?.experienceLevel ?: 0)
            } else {
                SkyBlockLevelBar.displayedExperienceLevel(
                    Minecraft.getInstance().player?.experienceLevel ?: 0,
                ).toString()
            }
            CustomBarPart.DEFENSE, CustomBarPart.SPEED, CustomBarPart.AIR -> error("${part.label} has no bar text")
        }

    }

    private fun resourceText(value: BarValue?, width: Int): String {
        if (value == null) return "---/---"
        val exact = "${value.displayedCurrent.addSeparators()}/${value.maximum.addSeparators()}"
        if (Minecraft.getInstance().font.width(exact) <= width - TEXT_PADDING * 2) return exact
        return "${value.displayedCurrent.toLong().shortFormat()}/${value.maximum.toLong().shortFormat()}"
    }

    private fun drawCustomBarText(context: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
        val font = Minecraft.getInstance().font
        if (config.details.textOutline) {
            val outlineColor = config.details.textOutlineColor.rgb()
            context.text(font, text, x + 1, y, outlineColor, false)
            context.text(font, text, x - 1, y, outlineColor, false)
            context.text(font, text, x, y + 1, outlineColor, false)
            context.text(font, text, x, y - 1, outlineColor, false)
        }
        context.text(font, text, x, y, color, false)
    }
}

private fun Property<ChromaColour>.rgb(): Int = get().toColor().rgb

internal data class VanillaHealthLayout(
    val currentHealth: Int,
    val absorption: Int,
    val healthContainers: Int,
    val totalContainers: Int,
    val rowCount: Int,
    val rowHeight: Int,
) {
    val height: Int = ICON_SIZE + (rowCount - 1) * rowHeight

    companion object {
        fun create(currentHealth: Int, maximumHealth: Float, absorption: Int): VanillaHealthLayout {
            val healthContainers = ceil(maximumHealth / 2.0).toInt()
            val absorptionContainers = (absorption + 1) / 2
            val totalContainers = healthContainers + absorptionContainers
            val vanillaRowCount = ceil((maximumHealth + absorption) / VANILLA_HEALTH_POINTS_PER_ROW)
                .toInt()
                .coerceAtLeast(1)
            val rowHeight = maxOf(
                VANILLA_HEART_ROW_HEIGHT - (vanillaRowCount - 2),
                VANILLA_MIN_HEART_ROW_HEIGHT,
            )
            return VanillaHealthLayout(
                currentHealth,
                absorption,
                healthContainers,
                totalContainers,
                ((totalContainers + VANILLA_STATUS_ICON_COUNT - 1) / VANILLA_STATUS_ICON_COUNT).coerceAtLeast(1),
                rowHeight,
            )
        }
    }
}

internal object RiftCustomBarValues {
    private const val HEALTH_POINTS_PER_HEART = 2
    private const val SECONDS_PER_MINUTE = 60
    private const val SECOND_DIGITS = 2
    private val damagePattern =
        Regex("^Rift Damage:\\s*${SkyBlockStatGlyph.RIFT_DAMAGE}(?<damage>[\\d,]+)$")

    fun parseDamage(lines: Iterable<String>): Int? = lines.firstNotNullOfOrNull { line ->
        damagePattern.matchEntire(line.trim())?.groups?.get("damage")?.value?.formatInt()
    }

    fun formatHearts(value: BarValue?): String = value?.let {
        "${it.displayedCurrent.toHearts()}/${it.maximum.toHearts()}"
    } ?: "---/---"

    fun formatTime(seconds: Int): String {
        val duration = DurationParts.fromSeconds(seconds.toLong())
        return if (duration.totalMinutes == 0L) {
            "${duration.seconds}s"
        } else {
            "${duration.totalMinutes}m${duration.seconds.toString().padStart(SECOND_DIGITS, '0')}s"
        }
    }

    private fun Int.toHearts(): String = if (this % HEALTH_POINTS_PER_HEART == 0) {
        (this / HEALTH_POINTS_PER_HEART).toString()
    } else {
        "${this / HEALTH_POINTS_PER_HEART}.5"
    }
}

internal object CustomBarsActionBarParser {
    private val healthPattern =
        Regex("(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*[❤${SkyBlockStatGlyph.HEALTH}]")
    private val manaPattern =
        Regex(
            "(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*[✎${SkyBlockStatGlyph.INTELLIGENCE}]" +
                "(?:\\s+Mana)?(?:\\s+(?<overflow>[\\d,]+)\\s*[ʬ${SkyBlockStatGlyph.OVERFLOW_MANA}])?",
        )
    private val defensePattern =
        Regex("(?<defense>[\\d,]+)\\s*[❈${SkyBlockStatGlyph.DEFENSE}](?:\\s+Defense)?")
    private val vitalityPattern =
        Regex("(?<current>[\\d,]+)/(?<maximum>[\\d,]+)\\s*${SkyBlockStatGlyph.VITALITY}")
    private val riftTimePattern =
        Regex("(?:[\\d,]+m)?\\d{1,2}s\\s*${SkyBlockStatGlyph.RIFT_TIME}\\s+Left")

    fun parse(text: String): ParsedCustomBarActionBar {
        val normalized = NormalizedActionBar(text)
        val healthMatch = healthPattern.find(normalized.text)
        val manaMatch = manaPattern.find(normalized.text)
        val vitalityMatch = vitalityPattern.find(normalized.text)
        val defenseMatch = defensePattern.find(normalized.text)
        val riftTimeMatch = riftTimePattern.find(normalized.text)
        return ParsedCustomBarActionBar(
            health = healthMatch?.let {
                BarValue(it.value("current"), it.value("maximum"))
            },
            mana = manaMatch?.let {
                BarValue(it.value("current"), it.value("maximum"), it.groups["overflow"]?.value?.skyBlockInt() ?: 0)
            },
            vitality = vitalityMatch?.let {
                BarValue(it.value("current"), it.value("maximum"))
            },
            defense = defenseMatch?.groups?.get("defense")?.value?.skyBlockInt(),
            removals = buildList {
                healthMatch?.let {
                    val healingFollows = normalized.text.getOrNull(it.range.last + 1) == '+'
                    val range = normalized.rawRange(it.range, preserveLastCharacter = healingFollows)
                    add(
                        StatusRemoval(
                            CustomBarStatus.HEALTH,
                            if (healingFollows) range else text.actionBarSegmentRange(range),
                        ),
                    )
                }
                manaMatch?.let {
                    add(StatusRemoval(CustomBarStatus.MANA, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                vitalityMatch?.let {
                    add(StatusRemoval(CustomBarStatus.VITALITY, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                defenseMatch?.let {
                    add(StatusRemoval(CustomBarStatus.DEFENSE, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
                riftTimeMatch?.let {
                    add(StatusRemoval(CustomBarStatus.RIFT_TIME, text.actionBarSegmentRange(normalized.rawRange(it.range))))
                }
            },
        )
    }

    fun filter(text: String, hidden: Set<CustomBarStatus>): String {
        val ranges = parse(text).ranges(hidden)
        return text.filterIndexed { index, _ -> ranges.none { index in it } }
    }

    private fun MatchResult.value(name: String): Int = groups[name]!!.value.skyBlockInt()

    private fun String.skyBlockInt(): Int = formatInt()
}

internal enum class CustomBarStatus {
    HEALTH,
    MANA,
    VITALITY,
    DEFENSE,
    RIFT_TIME,
    ;

    companion object {
        fun hiddenBy(settings: CustomBarsSettingsConfig, inRift: Boolean): Set<CustomBarStatus> = buildSet {
            val displays = settings.displays
            val numbers = settings.numbers
            if (displays.health != CustomBarDisplayMode.VANILLA || numbers.health) add(HEALTH)
            if (displays.mana != CustomBarDisplayMode.VANILLA || numbers.mana) add(MANA)
            if (displays.vitality != CustomBarDisplayMode.VANILLA || numbers.vitality) add(VITALITY)
            if (displays.defense != CustomBarDisplayMode.VANILLA) add(DEFENSE)
            if (inRift && (displays.experience != CustomBarDisplayMode.VANILLA || numbers.experience)) add(RIFT_TIME)
        }
    }
}

internal data class BarValue(val current: Int, val maximum: Int, val overflow: Int = 0) {
    val displayOverflow: Int get() = overflow.coerceAtLeast((current - maximum).coerceAtLeast(0))
    val regularCurrent: Int get() = current.coerceAtMost(maximum)
    val displayedCurrent: Int get() = regularCurrent + displayOverflow
}

internal data class ParsedCustomBarActionBar(
    val health: BarValue?,
    val mana: BarValue?,
    val vitality: BarValue?,
    val defense: Int?,
    private val removals: List<StatusRemoval>,
) {
    fun ranges(hidden: Set<CustomBarStatus>): List<IntRange> =
        removals.filter { it.status in hidden }.map(StatusRemoval::range)
}

internal data class StatusRemoval(val status: CustomBarStatus, val range: IntRange)

private fun net.minecraft.world.entity.player.Player.skyBlockSpeed(): Int =
    ((if (isSprinting) speed / SPRINT_SPEED_MULTIPLIER else speed) * SPEED_SCALE).roundToInt()

private const val PREVIEW_AIR_TICKS = 220
private const val HOTBAR_WIDTH = 182
private const val VANILLA_HUD_HALF_WIDTH = HOTBAR_WIDTH / 2
private const val VANILLA_STATUS_ICON_COUNT = 10
private const val VANILLA_STATUS_ICON_SPACING = 8
private const val VANILLA_STATUS_WIDTH = 82
private const val VANILLA_DEFAULT_HEALTH = 20f
private const val VANILLA_HEALTH_POINTS_PER_ROW = 20f
private const val VANILLA_HEART_ROW_HEIGHT = 10
private const val VANILLA_MIN_HEART_ROW_HEIGHT = 3
private const val VANILLA_HEALTH_TOP_OFFSET = 39
private const val VANILLA_AIR_LEFT_OFFSET = 10
private const val VANILLA_AIR_TOP_OFFSET = 49
private const val VANILLA_EXPERIENCE_WIDTH = HOTBAR_WIDTH
private const val VANILLA_EXPERIENCE_HEIGHT = 11
private const val VANILLA_EXPERIENCE_TOP_OFFSET = 35
private const val VANILLA_EXPERIENCE_BAR_Y = 6
private const val VANILLA_EXPERIENCE_BAR_HEIGHT = 5
private const val VANILLA_EXPERIENCE_PREVIEW_PROGRESS = 120
private const val VANILLA_EXPERIENCE_PREVIEW_LEVEL = "10"
private const val BAR_GAP = 4
private const val ICON_SLOT_WIDTH = 10
private const val HALF_BAR_WIDTH = (HOTBAR_WIDTH - BAR_GAP - ICON_SLOT_WIDTH * 2) / 2
private const val HALF_RESOURCE_WIDTH = ICON_SLOT_WIDTH + HALF_BAR_WIDTH
private const val VITALITY_RESOURCE_WIDTH = 54
private const val EXPERIENCE_RESOURCE_WIDTH = HOTBAR_WIDTH - BAR_GAP - VITALITY_RESOURCE_WIDTH
private const val RESOURCE_HEIGHT = 14
private const val RESOURCE_BAR_Y = 5
private const val RESOURCE_BOTTOM_PADDING = 2
private const val RESOURCE_TEXT_Y = 1
private const val MIN_TRACK_HEIGHT = 3
private const val MIN_RESOURCE_WIDTH = 24
private const val MIN_RESOURCE_HEIGHT = RESOURCE_BAR_Y + RESOURCE_BOTTOM_PADDING + MIN_TRACK_HEIGHT
private const val READOUT_WIDTH = 44
private const val READOUT_HEIGHT = 9
private const val READOUT_ELEMENT_HEIGHT = 11
private const val READOUT_BACKGROUND_Y = 1
private const val READOUT_CONTENT_Y = 2
private const val READOUT_CONTENT_GAP = 1
private const val INNER_PADDING = 1
private const val TEXT_PADDING = 2
private const val ICON_SIZE = 9
private const val EXPERIENCE_ICON_SIZE = 11
private const val EXPERIENCE_ICON_X = -2
private const val OVERFLOW_TRANSITION_HALF_WIDTH = 4
private const val ALPHA_MASK = 0xFF000000.toInt()
private const val TEXT_OUTLINE_RGB = ALPHA_MASK
private const val VANILLA_EXPERIENCE_LEVEL_RGB = 0xFF80FF20.toInt()
private const val TICKS_PER_SECOND = 20
private const val SPEED_SCALE = 1_000f
private const val SPRINT_SPEED_MULTIPLIER = 1.3f
private const val TEXT_SCALE_STEP = 0.1f
private val AIR_SPRITE = Identifier.withDefaultNamespace("hud/air")
private val VANILLA_HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container")
private val VANILLA_HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full")
private val VANILLA_HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half")
private val VANILLA_ABSORPTION_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full")
private val VANILLA_ABSORPTION_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half")
private val VANILLA_EXPERIENCE_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_background")
private val VANILLA_EXPERIENCE_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_progress")
private val EXPERIENCE_ICON = ItemIconRenderable(ItemStack(Items.EXPERIENCE_BOTTLE), EXPERIENCE_ICON_SIZE / 16.0)

private fun usesInventoryHudWidth(): Boolean = SkysoftConfigGui.config().gui.inventoryHud.enabled

private fun resourceRowWidth(): Int =
    if (usesInventoryHudWidth()) InventoryHudLayout.MAIN_PANEL_WIDTH else HOTBAR_WIDTH

private fun vitalityResourceWidth(): Int =
    (resourceRowWidth() * VITALITY_RESOURCE_WIDTH.toFloat() / HOTBAR_WIDTH).roundToInt()

private fun halfRoundedUp(value: Int): Int = (value + 1) / 2
