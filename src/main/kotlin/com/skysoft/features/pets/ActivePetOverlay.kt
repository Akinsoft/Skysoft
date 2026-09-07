package com.skysoft.features.pets

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.ProfileStorageApi
import com.skysoft.data.StoredPetData
import com.skysoft.data.skyblock.SkyBlockDataRepository
import com.skysoft.features.pets.PetDisplayRenderer.Companion.ANIMATION_TICK_MILLIS
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.TabDataOverlays
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.decorators.withOverlayPanel
import com.skysoft.utils.renderables.renderRenderable
import java.util.UUID
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

private fun anchorPetPositionToTop(renderable: GuiRenderable) {
    val position = SkysoftConfigGui.config().pets.display.general.position
    position.anchorToTop((renderable.height * position.effectiveScale).roundToInt())
}

object ActivePetOverlay {
    private val config get() = SkysoftConfigGui.config().pets.display
    private val expShareConfig get() = config.visual.expSharePets
    private val xpAnimations = PetXpAnimationState()
    private val renderer = PetDisplayRenderer(System.nanoTime())
    private var animatedPetKey: Any? = null
    private var lastDisplayState: PetDisplayState? = null
    private var displayRenderableFrame = Long.MIN_VALUE
    private var cachedDisplayRenderable: GuiRenderable? = null

    private val previewPet: StoredPetData by lazy {
        StoredPetData(
            petInternalName = "BEE;4",
            skinInternalName = "PET_SKIN_BEE_RGBEE",
            heldItemInternalName = EXP_SHARE,
            exp = 25_353_230.0,
        )
    }

    fun register() {
        SkyBlockDataRepository.Demand.register("Pet Features", PetFeatureDemand::isActive)
        ActivePetEntityTracker.registerConsumer("Pet Display", PetFeatureDemand::isDisplayActive)
        GuiOverlayRegistry.registerHud(
            GuiOverlay(
                id = "pet_display",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = TabDataOverlays.contexts,
                visible = TabDataOverlays::canRender,
                render = { context, _ -> renderHud(context) },
            ),
            object : HudEditorElement {
                override val id: String = "pet_display"
                override val label: String = "Pet Display"
                override val position get() = config.general.position
                override val hasEditorBackground: Boolean get() = !config.general.settings.background.get()
                override fun width(): Int = previewRenderable()?.width ?: PREVIEW_WIDTH
                override fun height(): Int = previewRenderable()?.height ?: PREVIEW_HEIGHT
                override fun isVisible(): Boolean = PetFeatureDemand.isDisplayActive()
                override fun renderEditor(context: GuiGraphicsExtractor) {
                    previewRenderable()?.render(context)
                }
                override fun openConfig() = PetOverlayConfigScreen.open()
            },
        )
        ActivePetTracker.onChange("Active Pet Overlay state change", PetFeatureDemand::isDisplayActive) { petData ->
            displayRenderableFrame = Long.MIN_VALUE
            if (petData == null) lastDisplayState = null
            val petKey = petData?.uuid ?: petData?.fauxInternalName
            if (petKey != animatedPetKey) {
                animatedPetKey = petKey
                invalidateAnimations()
            }
        }
        PetExpShareTooltip.register()
    }

    fun previewRenderable(): GuiRenderable? = (
        buildDisplayRenderable(displayState)
            ?: renderer.build(xpAnimations.withAnimatedEquipped(previewPet), emptyList())
                ?.withOverlayPanel(config.general.settings.background.get())
        )?.also(::anchorPetPositionToTop)

    private fun renderHud(context: GuiGraphicsExtractor) {
        val minecraft = Minecraft.getInstance()
        if (
            MinecraftClient.isGuiHidden(minecraft) ||
            !PetFeatureDemand.isDisplayActive() ||
            config.general.settings.hideInMenus.get() && MinecraftClient.screen(minecraft) is AbstractContainerScreen<*>
        ) return
        context.nextStratum()
        val renderable = currentDisplayRenderable
        renderable?.also {
            anchorPetPositionToTop(it)
            config.general.position.renderRenderable(context, it)
        }
    }

    private val currentDisplayRenderable: GuiRenderable?
        get() {
            val frame = System.currentTimeMillis() / ANIMATION_TICK_MILLIS
            if (frame != displayRenderableFrame || xpAnimations.hasPendingAnimations) {
                displayRenderableFrame = frame
                cachedDisplayRenderable = buildDisplayRenderable(displayState)
            }
            return cachedDisplayRenderable
        }

    private fun buildDisplayRenderable(state: PetDisplayState?): GuiRenderable? {
        val renderable = state?.messageLines?.let(renderer::buildWidgetMessageRenderable)
            ?: state?.currentPet?.let { currentPet ->
                renderer.build(xpAnimations.withAnimatedEquipped(currentPet), state.expSharePets.withAnimatedExpShare())
            }
        return renderable?.withOverlayPanel(config.general.settings.background.get())
    }

    private val displayState: PetDisplayState?
        get() {
            val trackedPet = ActivePetTracker.currentPet
            val dataSource = PetStorageService.petDisplayDataSource
            if (!TabDataOverlays.hasStableData && dataSource.requiresStableTabData) {
                return lastDisplayState
            }
            PetStorageService.petWidgetDisplayMessage?.let { lines ->
                return PetDisplayState(messageLines = lines)
            }
            if (!dataSource.canRefreshDisplay) {
                return lastDisplayState
            }
            val currentPet = trackedPet ?: return lastDisplayState
            val observedTexture = ActivePetEntityTracker.current()
                ?.takeIf { currentPet.skinInternalName != null && it.matches(currentPet) }
                ?.texture
            return PetDisplayState(
                currentPet = currentPet.copy(displayIconTexture = observedTexture ?: currentPet.displayIconTexture),
                expSharePets = getVisibleExpSharePetStates(currentPet.uuid).map { it.copyState() },
            ).also {
                lastDisplayState = it
            }
        }

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

    private const val EXP_SHARE = "PET_ITEM_EXP_SHARE"
    private const val PREVIEW_WIDTH = 120
    private const val PREVIEW_HEIGHT = 40
}
