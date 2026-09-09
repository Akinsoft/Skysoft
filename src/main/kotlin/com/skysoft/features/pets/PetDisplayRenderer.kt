package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.config.features.pets.display.text.PetTextConfig
import com.skysoft.config.features.pets.display.text.PetTextDisplaySettings
import com.skysoft.config.features.pets.display.visual.PetIconConfig
import com.skysoft.config.features.pets.display.visual.PetItemLayerConfig
import com.skysoft.config.features.pets.display.visual.PetRarityBackgroundConfig
import com.skysoft.config.features.pets.display.visual.PetVisualConfig
import com.skysoft.config.features.pets.display.visual.RingStyleConfig
import com.skysoft.config.features.pets.display.visual.SharedPetLayoutConfig
import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.misc.PlayerHeadSkinFix
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MAX
import com.skysoft.utils.ColorUtilities.COLOR_CHANNEL_MIN
import com.skysoft.utils.ColorUtilities.RGB_MASK
import com.skysoft.utils.ColorUtilities.toColor
import com.skysoft.utils.ColorUtilities.withAlpha
import com.skysoft.utils.ColorUtilities.withOpacity
import com.skysoft.utils.gui.GuiAlignment
import com.skysoft.utils.renderables.AnchoredRenderable
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.anchorToSelf
import com.skysoft.utils.renderables.animated.OrbitLayoutRenderable
import com.skysoft.utils.renderables.container.horizontalLayout
import com.skysoft.utils.renderables.container.verticalLayout
import com.skysoft.utils.renderables.decorators.CircularLayoutRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.primitives.StringRenderable
import com.skysoft.utils.renderables.renderAt
import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.UUID
import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor

private typealias TextLocation = PetTextConfig.TextLocationOption
private typealias ExpShareTextMode = PetTextConfig.ExpSharePetTextConfig.TextMode
private typealias TextCenter = PetTextConfig.EquippedPetTextConfig.CenterTarget
private typealias ExpShareTextLocation = PetTextConfig.ExpSharePetTextConfig.BundledTextLocation
private typealias ExpSharePlacement = SharedPetLayoutConfig.ExpShareLocationOption
private typealias ExpShareOrientation = SharedPetLayoutConfig.GroupOrientation

internal class PetDisplayRenderer(private val orbitStartedAtNanos: Long) {
    private val config get() = SkysoftConfigGui.config().pets.display
    private val equippedVisualConfig get() = config.visual.equippedPet
    private val expShareConfig get() = config.visual.expSharePets

    fun build(pet: StoredPetData, expSharePets: List<ExpSharePetState>): GuiRenderable? {
        val itemRenderable = pet.buildMainIconRenderableOrNull()
            ?.wrapInExpShareIconsOrSelf(expSharePets)
        val mainTextRenderable = pet.buildTextRenderableOrNull(config.text.equippedPet)
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
        val stableStack = PlayerHeadSkinFix.stableHeadStack(
            PetHeadRenderKey(uuid, petInternalName, skinInternalName, displayIconTexture),
            frame.stack,
        ) ?: return null
        return ItemIconRenderable(
            stableStack,
            scale = icon.scale.get().toDouble(),
            xRotationDegrees = icon.rotation.staticRotation.xRotation.get(),
            yRotationDegrees = icon.rotation.staticRotation.yRotation.get(),
            zRotationDegrees = icon.rotation.staticRotation.zRotation.get(),
            xRotationSpeedDegreesPerSecond = icon.rotation.spinRotation.speedX.get(),
            yRotationSpeedDegreesPerSecond = icon.rotation.spinRotation.speedY.get(),
            zRotationSpeedDegreesPerSecond = icon.rotation.spinRotation.speedZ.get(),
            alpha = opacity,
            highQualityScaling = true,
        )
    }

    private fun StoredPetData.buildTextRenderableOrNull(
        textConfig: PetTextDisplaySettings,
        opacity: Float = 1.0f,
        textScale: Double = textConfig.textScale.get().toDouble(),
    ): GuiRenderable? {
        val textAlpha = (COLOR_CHANNEL_MAX * opacity).roundToInt()
            .coerceIn(COLOR_CHANNEL_MIN, COLOR_CHANNEL_MAX)
        val textColor = RGB_MASK.withAlpha(textAlpha)
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

    fun buildWidgetMessageRenderable(lines: List<String>): GuiRenderable {
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

    private data class VisualIconLayer(val renderable: GuiRenderable, val backgroundEnabled: Boolean)

    companion object {
        const val ANIMATION_TICK_MILLIS = 50L
        private const val ICON_GROUP_SPACING = 2
    }
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
    val itemId = petData.heldItemInternalName ?: return this
    val item = SkyBlockDataRepository.stack(SkyBlockDataRepository.itemKey(itemId)) ?: return this
    val placement = petItemConfig.placement.get()
    return PetItemOverlayRenderable(
        root = this,
        item = ItemIconRenderable(
            item,
            scale = petItemConfig.scale.get().toDouble(),
            alpha = opacity,
            highQualityScaling = true,
        ),
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

private data class PetHeadRenderKey(
    val uuid: UUID?,
    val petInternalName: String,
    val skinInternalName: String?,
    val displayIconTexture: String?,
)

private const val TEXT_VISUAL_SPACING = 2
