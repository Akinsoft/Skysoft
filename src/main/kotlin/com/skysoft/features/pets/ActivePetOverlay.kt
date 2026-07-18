package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.features.pets.display.text.PetTextDisplaySettings
import com.skysoft.config.features.pets.display.text.PetTextConfig
import com.skysoft.config.features.pets.display.visual.SharedPetLayoutConfig
import com.skysoft.config.features.pets.display.visual.PetIconConfig
import com.skysoft.config.features.pets.display.visual.PetItemLayerConfig
import com.skysoft.config.features.pets.display.visual.PetRarityBackgroundConfig
import com.skysoft.config.features.pets.display.visual.RingStyleConfig
import com.skysoft.config.features.pets.display.visual.PetVisualConfig
import com.skysoft.data.StoredPetData
import com.skysoft.data.ProfileStorageApi
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.ColorUtilities.ARGB_ALPHA_SHIFT
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MIN
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withOpacity
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.SkysoftErrorBoundary
import com.skysoft.utils.TextUtilities.cleanSkyBlockText
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.renderables.AnchoredRenderable
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.anchorToSelf
import com.skysoft.utils.renderables.animated.OrbitLayoutRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.decorators.CircularLayoutRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.renderRenderable
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.roundToInt

private typealias TextLocation = PetTextConfig.TextLocationOption
private typealias ExpShareTextMode = PetTextConfig.ExpSharePetTextConfig.TextMode
private typealias TextCenter = PetTextConfig.EquippedPetTextConfig.CenterTarget
private typealias ExpShareTextLocation = PetTextConfig.ExpSharePetTextConfig.BundledTextLocation
private typealias ExpSharePlacement = SharedPetLayoutConfig.ExpShareLocationOption
private typealias ExpShareOrientation = SharedPetLayoutConfig.GroupOrientation

object ActivePetOverlay {
    private val config get() = SkysoftConfigGui.config().pets.display
    private val equippedVisualConfig get() = config.visual.equippedPet
    private val expShareConfig get() = config.visual.expSharePets
    private val xpAnimations = PetXpAnimationState()
    private val orbitStartedAtNanos = System.nanoTime()
    private var animatedPetKey: Any? = null
    private var lastDisplayState: PetDisplayState? = null

    private val previewPet: StoredPetData by lazy {
        StoredPetData(
            petInternalName = "BEE;4",
            skinInternalName = "PET_SKIN_BEE_RGBEE",
            heldItemInternalName = EXP_SHARE,
            exp = 25_353_230.0,
        )
    }

    fun register() {
        PetRepository.registerConsumer("Pet Features", PetFeatureDemand::isActive)
        ActivePetEntityTracker.registerConsumer("Pet Display", PetFeatureDemand::isDisplayActive)
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "pet_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ -> renderHud(context) },
            ),
        )
        HudEditorRegistry.register(object : HudEditorElement {
            override val id: String = "pet_display"
            override val label: String = "Pet Display"
            override val position get() = config.general.position
            override val hasEditorBackground: Boolean get() = !config.general.settings.background.get()
            override fun width(): Int = previewRenderable()?.width ?: PREVIEW_WIDTH
            override fun height(): Int = previewRenderable()?.height ?: PREVIEW_HEIGHT
            override fun isVisible(): Boolean = SkysoftConfigGui.config().pets.petDisplay.enabled.get()
            override fun renderDummy(context: GuiGraphicsExtractor) {
                previewRenderable()?.render(context)
            }
            override fun openConfig() = PetOverlayConfigScreen.open()
        })
        ActivePetTracker.onChange("Active Pet Overlay state change", PetFeatureDemand::isDisplayActive) { petData ->
            if (petData == null) lastDisplayState = null
            val petKey = petData?.uuid ?: petData?.fauxInternalName
            if (petKey != animatedPetKey) {
                animatedPetKey = petKey
                invalidateAnimations()
            }
        }
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            SkysoftErrorBoundary.run("Active Pet Tooltip rendering") tooltip@{
                val screen = MinecraftClient.screen() as? AbstractContainerScreen<*> ?: return@tooltip
                if (!PetStorageService.isExpSharingInventory(screen.title.cleanSkyBlockText())) return@tooltip
                val slot = screen.menu.slots.firstOrNull { it.item === stack }
                    ?: screen.menu.slots.firstOrNull {
                        it.item == stack && PetStorageService.isExpShareSlotDisabled(it.containerSlot)
                    }
                    ?: return@tooltip
                if (!PetStorageService.isExpShareSlotDisabled(slot.containerSlot)) return@tooltip

                tooltip.add(Component.literal(""))
                tooltip.add(Component.literal("This Exp Share slot is disabled.").withStyle(ChatFormatting.RED))
                tooltip.add(
                    Component.literal("Diana's ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Sharing is Caring").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" perk is not active.").withStyle(ChatFormatting.GRAY)),
                )
            }
        }
    }

    fun previewRenderable(): GuiRenderable? =
        buildDisplayRenderable(displayState)
            ?: xpAnimations.withAnimatedEquipped(previewPet).buildRenderable(emptyList())
                ?.withOverlayPanel(config.general.settings.background.get())

    private fun renderHud(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        if (
            MinecraftClient.isGuiHidden(minecraft) ||
            !SkysoftConfigGui.config().pets.petDisplay.enabled.get() ||
            config.general.settings.hideInMenus.get() && MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
        ) return
        context.nextStratum()
        val renderable = buildDisplayRenderable(displayState)
        renderable?.also {
            config.general.position.renderRenderable(context, it)
        }
    }

    private fun buildDisplayRenderable(state: PetDisplayState?): GuiRenderable? {
        val renderable = state?.messageLines?.let(::buildWidgetMessageRenderable)
            ?: state?.currentPet?.let { currentPet ->
                xpAnimations.withAnimatedEquipped(currentPet).buildRenderable(state.expSharePets.withAnimatedExpShare())
            }
        return renderable?.withOverlayPanel(config.general.settings.background.get())
    }

    private val displayState: PetDisplayState?
        get() {
            if (!TabDataOverlays.hasStableData) return lastDisplayState
            PetStorageService.petWidgetDisplayMessage?.let { lines -> return PetDisplayState(messageLines = lines) }
            if (!PetStorageService.isPetWidgetReadyForDisplay) return lastDisplayState
            val currentPet = ActivePetTracker.currentPet ?: return lastDisplayState
            val observedTexture = ActivePetEntityTracker.current()
                ?.takeIf { currentPet.skinInternalName != null && it.matches(currentPet) }
                ?.texture
            return PetDisplayState(
                currentPet = currentPet.copy(displayIconTexture = observedTexture ?: currentPet.displayIconTexture),
                expSharePets = getVisibleExpSharePetStates(currentPet.uuid).map { it.copyState() },
            ).also { lastDisplayState = it }
        }

    private fun StoredPetData.buildRenderable(expSharePets: List<ExpSharePetState>): GuiRenderable? {
        val itemRenderable = buildMainIconRenderableOrNull()
            ?.wrapInExpShareIconsOrSelf(expSharePets)
        val mainTextRenderable = buildTextRenderableOrNull(config.text.equippedPet)
        val expShareTextRenderables = expSharePets.buildBundledExpShareTextRenderables()
        val textRenderable = combineMainAndExpShareTextRenderables(mainTextRenderable, expShareTextRenderables)
        return combineVisualAndTextRenderables(
            itemRenderable,
            textRenderable,
            config.text.equippedPet.textLocation.get(),
            config.text.equippedPet.centerTarget.get(),
        )
    }

    private fun StoredPetData.buildMainIconRenderableOrNull(): GuiRenderable? = buildIconRenderable(
        icon = equippedVisualConfig.icon,
        petItem = equippedVisualConfig.petItem,
        rarityBackground = equippedVisualConfig.rarityBackground,
    )

    private fun getVisibleExpSharePetStates(currentPetUuid: UUID?): List<ExpSharePetState> {
        val activeExpSharePets = PetStorageService.getActiveExpSharePetUuids()
        val disabledExpSharePets = PetStorageService.getDisabledExpSharePetUuids()
        return ProfileStorageApi.storage.pets.mapNotNull {
            it.visibleExpShareStateOrNull(currentPetUuid, activeExpSharePets, disabledExpSharePets)
        }
    }

    private fun StoredPetData.visibleExpShareStateOrNull(
        currentPetUuid: UUID?,
        activeExpSharePets: Set<UUID>,
        disabledExpSharePets: Set<UUID>,
    ): ExpSharePetState? {
        val petUuid = uuid ?: return null
        if (petUuid == currentPetUuid) return null
        if (petUuid in activeExpSharePets) return ExpSharePetState(this, disabled = false)
        if (petUuid in disabledExpSharePets && !expShareConfig.activeSlotsOnly.get()) return ExpSharePetState(this, disabled = true)
        return null
    }

    private fun GuiRenderable.wrapInExpShareIconsOrSelf(expSharePets: List<ExpSharePetState>): AnchoredRenderable {
        if (!expShareConfig.enabled.get()) return anchorToSelf()
        val expShareRenderables = expSharePets.mapNotNull { it.buildExpShareRenderable() }
        if (expShareRenderables.isEmpty()) return anchorToSelf()

        val organization = expShareConfig.organization
        val placement = organization.placement.get()
        return if (placement == ExpSharePlacement.ORBIT) {
            val subBodySpacing = organization.subOrbit.orbitDistance.get().roundToInt()
            val subBodyWidth = expShareRenderables.maxOf { it.width }
            val subBodyHeight = expShareRenderables.maxOf { it.height }
            val renderable = OrbitLayoutRenderable(
                this,
                subBodies = expShareRenderables,
                subBodySpacing = subBodySpacing,
                orbitSpeed = organization.subOrbit.orbitSpeed.get().toInt(),
                orbitDirection = organization.subOrbit.orbitDirection.get(),
                startedAtNanos = orbitStartedAtNanos,
            )
            AnchoredRenderable(
                renderable = renderable,
                anchorX = subBodyWidth + subBodySpacing,
                anchorY = subBodyHeight + subBodySpacing,
                anchorWidth = width,
                anchorHeight = height,
            )
        } else {
            val iconSpacing = expShareConfig.icon.iconSpacing.get().roundToInt()
            val expShareContainer = when (organization.groupOrientation.get()) {
                ExpShareOrientation.VERTICAL -> verticalLayout(
                    expShareRenderables,
                    spacing = iconSpacing,
                    horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER,
                    verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
                )

                ExpShareOrientation.HORIZONTAL -> horizontalLayout(
                    expShareRenderables,
                    spacing = iconSpacing,
                    horizontalAlign = GuiAlignment.HorizontalAlignment.CENTER,
                    verticalAlign = GuiAlignment.VerticalAlignment.CENTER,
                )
            }
            val beforeMain = placement == ExpSharePlacement.TOP || placement == ExpSharePlacement.LEFT
            val verticalLayout = placement == ExpSharePlacement.TOP || placement == ExpSharePlacement.BOTTOM
            val orderedList = if (beforeMain) {
                listOf(expShareContainer, this)
            } else listOf(this, expShareContainer)
            val renderable = if (verticalLayout) {
                verticalLayout(orderedList, spacing = ICON_GROUP_SPACING)
            } else {
                horizontalLayout(orderedList, spacing = ICON_GROUP_SPACING)
            }
            val anchorX = when (placement) {
                ExpSharePlacement.LEFT -> expShareContainer.width + ICON_GROUP_SPACING
                ExpSharePlacement.RIGHT -> 0
                else -> (renderable.width - width) / 2
            }
            val anchorY = when (placement) {
                ExpSharePlacement.TOP -> expShareContainer.height + ICON_GROUP_SPACING
                ExpSharePlacement.BOTTOM -> 0
                else -> (renderable.height - height) / 2
            }
            AnchoredRenderable(renderable, anchorX, anchorY, width, height)
        }
    }

    private fun ExpSharePetState.buildExpShareRenderable(): GuiRenderable? {
        val itemRenderable = petData.buildExpShareIconRenderable(opacity)
        val textConfig = config.text.expSharePets
        val textRenderable = if (
            textConfig.enabled.get() &&
            textConfig.textMode.get() == ExpShareTextMode.ATTACHED_TO_ICONS
        ) {
            petData.buildTextRenderableOrNull(textConfig, opacity = opacity, textScale = textConfig.textScale.get().toDouble())
        } else null
        return combineVisualAndTextRenderables(
            itemRenderable?.anchorToSelf(),
            textRenderable,
            textConfig.textLocation.get(),
            TextCenter.ALL_PET_VISUALS,
        )
    }

    private fun StoredPetData.buildExpShareIconRenderable(opacity: Float): GuiRenderable? = buildIconRenderable(
        icon = expShareConfig.icon,
        petItem = expShareConfig.petItem,
        rarityBackground = expShareConfig.rarityBackground,
        opacity = opacity,
    )

    private fun StoredPetData.buildIconRenderable(
        icon: PetIconConfig,
        petItem: PetItemLayerConfig,
        rarityBackground: PetVisualConfig.BackgroundColorConfig,
        opacity: Float = 1.0f,
    ): GuiRenderable? {
        val iconLayer = buildVisualIconLayerOrNull(icon, petItem, rarityBackground, opacity) ?: return null
        val borderRingConfig = rarityBackground.borderRing
        val xpRingEnabled = iconLayer.backgroundEnabled && borderRingConfig.enabled.get()
        val separatorWrappedRenderable = iconLayer.renderable.wrapInRingOrSelf(
            enabled = xpRingEnabled && borderRingConfig.divider.enabled.get(),
            ringConfig = borderRingConfig.divider,
            opacity = opacity,
        )
        return if (!xpRingEnabled) separatorWrappedRenderable else buildCircularContainer(
            separatorWrappedRenderable,
            backgroundColor = borderRingConfig.progress.filledColor.get().withOpacity(opacity),
            unfilledColor = borderRingConfig.progress.unfilledColor.get().withOpacity(opacity),
            filledPercentage = levelProgressionPercentage,
            padding = borderRingConfig.progress.padding.get().roundToInt(),
        )
    }

    private fun StoredPetData.buildVisualIconLayerOrNull(
        icon: PetIconConfig,
        petItem: PetItemLayerConfig,
        rarityBackground: PetVisualConfig.BackgroundColorConfig,
        opacity: Float = 1.0f,
    ): VisualIconLayer? {
        if (!icon.enabled.get()) return null
        val baseItemRenderable = buildBaseItemRenderable(icon, opacity) ?: return null
        val petItemWrappedRenderable = baseItemRenderable.withPetItemLayer(this, petItem, opacity)
        val backgroundEnabled = rarityBackground.enabled.get()
        val backgroundWrappedRenderable = petItemWrappedRenderable.wrapInBackgroundColorOrSelf(
            enabled = backgroundEnabled,
            backgroundConfig = rarityBackground.customization,
            rarity = rarity,
            opacity = opacity,
        )
        return VisualIconLayer(backgroundWrappedRenderable, backgroundEnabled)
    }

    private fun StoredPetData.buildBaseItemRenderable(icon: PetIconConfig, opacity: Float): GuiRenderable? {
        val frames = getAnimatedItemStackSequence(
            firstFrameOnly = !icon.skinAnimation.get(),
            animationSpeed = icon.skinAnimationSpeed.get(),
        ) ?: return null
        val totalTicks = frames.sumOf { it.ticks }.coerceAtLeast(1)
        val nowMillis = System.currentTimeMillis()
        val tick = ((nowMillis / ANIMATION_TICK_MILLIS) % totalTicks).toInt()
        var cursor = 0
        val frame = frames.firstOrNull {
            cursor += it.ticks
            tick < cursor
        } ?: frames.first()
        val elapsedSeconds = (nowMillis % MILLIS_PER_HOUR) / MILLIS_PER_SECOND_FLOAT
        val staticX = icon.rotation.staticRotation.xRotation.get()
        val spinX = icon.rotation.spinRotation.speedX.get() * elapsedSeconds
        val staticY = icon.rotation.staticRotation.yRotation.get()
        val spinY = icon.rotation.spinRotation.speedY.get() * elapsedSeconds
        val staticZ = icon.rotation.staticRotation.zRotation.get()
        val spinZ = icon.rotation.spinRotation.speedZ.get() * elapsedSeconds
        return ItemIconRenderable(
            frame.stack,
            scale = icon.scale.get().toDouble(),
            xRotationDegrees = staticX + spinX,
            yRotationDegrees = staticY + spinY,
            zRotationDegrees = staticZ + spinZ,
            alpha = opacity,
        )
    }

    private fun StoredPetData.buildTextRenderableOrNull(
        textConfig: PetTextDisplaySettings,
        opacity: Float = 1.0f,
        textScale: Double = textConfig.textScale.get().toDouble(),
    ): GuiRenderable? {
        val textAlpha = (COLOR_CHANNEL_MAX * opacity).roundToInt()
            .coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX)
        val textColor = (textAlpha shl ARGB_ALPHA_SHIFT) or RGB_MASK
        val lines = textConfig.enabledTexts.get().mapNotNull { textElement ->
            val textElementFormat = PetDisplayTextFormatter.formatElement(this, textElement, textConfig) ?: return@mapNotNull null
            val labelFormat = textElement.getFormattedLabel().takeIf { textConfig.textLabels.get() }.orEmpty()
            StringRenderable(
                "$labelFormat$textElementFormat",
                scale = textScale,
                color = textColor,
                horizontalAlign = textConfig.horizontalAlign.get(),
            )
        }
        if (lines.isEmpty()) return null
        return verticalLayout(
            lines,
            horizontalAlign = textConfig.horizontalAlign.get(),
            verticalAlign = textConfig.verticalAlign.get(),
        )
    }

    private fun List<ExpSharePetState>.buildBundledExpShareTextRenderables(): List<GuiRenderable> {
        val textConfig = config.text.expSharePets
        if (!textConfig.enabled.get()) return emptyList()
        if (textConfig.textMode.get() != ExpShareTextMode.BUNDLED_WITH_MAIN) return emptyList()
        return mapNotNull { it.petData.buildTextRenderableOrNull(textConfig, opacity = it.opacity) }
    }

    private fun combineMainAndExpShareTextRenderables(
        mainTextRenderable: GuiRenderable?,
        expShareTextRenderables: List<GuiRenderable>,
    ): GuiRenderable? {
        if (expShareTextRenderables.isEmpty()) return mainTextRenderable
        val renderables = when (config.text.expSharePets.bundledLocation.get()) {
            ExpShareTextLocation.ABOVE -> expShareTextRenderables + listOfNotNull(mainTextRenderable)
            ExpShareTextLocation.BELOW -> listOfNotNull(mainTextRenderable) + expShareTextRenderables
            ExpShareTextLocation.SPLIT -> {
                val aboveCount = (expShareTextRenderables.size + 1) / 2
                expShareTextRenderables.take(aboveCount) + listOfNotNull(mainTextRenderable) + expShareTextRenderables.drop(aboveCount)
            }
        }
        if (renderables.isEmpty()) return null
        return verticalLayout(
            renderables,
            spacing = config.text.expSharePets.bundledSpacing.get(),
            horizontalAlign = config.text.equippedPet.horizontalAlign.get(),
            verticalAlign = config.text.equippedPet.verticalAlign.get(),
        )
    }

    private fun buildWidgetMessageRenderable(lines: List<String>): GuiRenderable {
        val textConfig = config.text.equippedPet
        return verticalLayout(
            lines.map {
                StringRenderable(
                    it,
                    scale = textConfig.textScale.get().toDouble(),
                    horizontalAlign = textConfig.horizontalAlign.get(),
                )
            },
            horizontalAlign = textConfig.horizontalAlign.get(),
            verticalAlign = textConfig.verticalAlign.get(),
        )
    }

    private fun List<ExpSharePetState>.withAnimatedExpShare(): List<ExpSharePetState> {
        val animatedPets = xpAnimations.withAnimatedExpShare(map { it.petData })
        return zip(animatedPets) { state, petData -> state.copy(petData = petData) }
    }

    private fun invalidateAnimations() {
        xpAnimations.clear()
    }

    private data class PetDisplayState(
        val currentPet: StoredPetData? = null,
        val expSharePets: List<ExpSharePetState> = emptyList(),
        val messageLines: List<String>? = null,
    )

    private data class VisualIconLayer(val renderable: GuiRenderable, val backgroundEnabled: Boolean)

    private data class ExpSharePetState(val petData: StoredPetData, val disabled: Boolean) {
        val opacity get() = if (disabled) expShareConfig.disabledOpacity.get() else 1.0f

        fun copyState(): ExpSharePetState = copy(petData = petData.copy())
    }

    private const val EXP_SHARE = "PET_ITEM_EXP_SHARE"
    private const val PREVIEW_WIDTH = 120
    private const val PREVIEW_HEIGHT = 40
    private const val ANIMATION_TICK_MILLIS = 50L
    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_SECOND_FLOAT = 1000f
    private const val ICON_GROUP_SPACING = 2
}

private fun GuiRenderable.withPetItemLayer(
    petData: StoredPetData,
    config: PetItemLayerConfig,
    opacity: Float,
): GuiRenderable =
    wrapInPetItemOrSelf(
        enabled = config.enabled.get(),
        petData = petData,
        petItemConfig = config,
        opacity = opacity,
    )

private fun GuiRenderable.wrapInBackgroundColorOrSelf(
    enabled: Boolean,
    backgroundConfig: PetRarityBackgroundConfig,
    rarity: com.skysoft.data.skyblock.SkyBlockRarity,
    opacity: Float,
): GuiRenderable = if (!enabled) this else buildCircularContainer(
    this,
    backgroundConfig.getRarityBackgroundColor(rarity).withOpacity(opacity),
    padding = backgroundConfig.padding.get().roundToInt(),
)

private fun GuiRenderable.wrapInRingOrSelf(
    enabled: Boolean,
    ringConfig: RingStyleConfig,
    opacity: Float = 1.0f,
): GuiRenderable = if (!enabled) this else buildCircularContainer(
    this,
    ringConfig.color.get().withOpacity(opacity),
    padding = ringConfig.padding.get().roundToInt(),
)

private fun buildCircularContainer(
    root: GuiRenderable,
    backgroundColor: ChromaColour,
    filledPercentage: Double = 100.0,
    unfilledColor: ChromaColour? = null,
    padding: Int = 2,
): GuiRenderable = CircularLayoutRenderable(
    root,
    filledColor = backgroundColor.toArgb(),
    unfilledColor = unfilledColor?.toArgb(),
    filledPercentage = filledPercentage,
    padding = padding,
)

private fun GuiRenderable.wrapInPetItemOrSelf(
    enabled: Boolean,
    petData: StoredPetData,
    petItemConfig: PetItemLayerConfig,
    opacity: Float = 1.0f,
): GuiRenderable {
    if (!enabled) return this
    val item = PetRepository.itemStackOrNull(petData.heldItemInternalName) ?: return this
    val placement = petItemConfig.placement.get()
    return PetItemOverlayRenderable(
        root = this,
        item = ItemIconRenderable(item, scale = petItemConfig.scale.get().toDouble(), alpha = opacity),
        horizontal = placement.horizontal,
        vertical = placement.vertical,
    )
}

private fun combineVisualAndTextRenderables(
    itemRenderable: AnchoredRenderable?,
    textRenderable: GuiRenderable?,
    textLocation: TextLocation,
    centerTarget: TextCenter,
): GuiRenderable? = if (itemRenderable != null && textRenderable != null) {
    if (centerTarget == TextCenter.EQUIPPED_PET_VISUALS) {
        combineAnchoredVisualAndTextRenderables(itemRenderable, textRenderable, textLocation)
    } else {
        val visualRenderable = itemRenderable.renderable
        val orderedList = when (textLocation) {
            TextLocation.TOP, TextLocation.LEFT -> listOf(textRenderable, visualRenderable)
            TextLocation.BOTTOM, TextLocation.RIGHT -> listOf(visualRenderable, textRenderable)
        }
        when (textLocation) {
            TextLocation.TOP, TextLocation.BOTTOM -> verticalLayout(orderedList, spacing = TEXT_VISUAL_SPACING)
            TextLocation.LEFT, TextLocation.RIGHT -> horizontalLayout(orderedList, spacing = TEXT_VISUAL_SPACING)
        }
    }
} else textRenderable ?: itemRenderable?.renderable

private fun combineAnchoredVisualAndTextRenderables(
    itemRenderable: AnchoredRenderable,
    textRenderable: GuiRenderable,
    textLocation: TextLocation,
): GuiRenderable {
    val visualRenderable = itemRenderable.renderable
    val textX = when (textLocation) {
        TextLocation.LEFT -> itemRenderable.anchorX - textRenderable.width - TEXT_VISUAL_SPACING
        TextLocation.RIGHT -> itemRenderable.anchorX + itemRenderable.anchorWidth + TEXT_VISUAL_SPACING
        else -> itemRenderable.anchorX + (itemRenderable.anchorWidth - textRenderable.width) / 2
    }
    val textY = when (textLocation) {
        TextLocation.TOP -> itemRenderable.anchorY - textRenderable.height - TEXT_VISUAL_SPACING
        TextLocation.BOTTOM -> itemRenderable.anchorY + itemRenderable.anchorHeight + TEXT_VISUAL_SPACING
        else -> itemRenderable.anchorY + (itemRenderable.anchorHeight - textRenderable.height) / 2
    }
    val minX = minOf(0, textX)
    val minY = minOf(0, textY)
    val visualX = -minX
    val visualY = -minY
    val shiftedTextX = textX - minX
    val shiftedTextY = textY - minY
    val renderTextFirst = textLocation == TextLocation.TOP || textLocation == TextLocation.LEFT

    return object : GuiRenderable {
        override val width = maxOf(visualRenderable.width, textX + textRenderable.width) - minX
        override val height = maxOf(visualRenderable.height, textY + textRenderable.height) - minY

        override fun render(context: GuiGraphicsExtractor) {
            if (renderTextFirst) {
                textRenderable.renderAt(context, shiftedTextX, shiftedTextY)
                visualRenderable.renderAt(context, visualX, visualY)
            } else {
                visualRenderable.renderAt(context, visualX, visualY)
                textRenderable.renderAt(context, shiftedTextX, shiftedTextY)
            }
        }
    }
}

private fun ChromaColour.toArgb(): Int = toColor().rgb

private const val TEXT_VISUAL_SPACING = 2
