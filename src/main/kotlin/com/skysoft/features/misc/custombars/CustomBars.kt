package com.skysoft.features.misc.custombars

import com.skysoft.config.CustomBarDisplayMode
import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.features.misc.actionbar.withoutRanges
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.renderables.renderRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

object CustomBars {
    private val config get() = SkysoftConfigGui.config().gui.customBars
    private val inRift get() = SkyBlockIsland.THE_RIFT.isInIsland()
    private val textElements by lazy {
        CustomBarPart.entries.filter(CustomBarPart::isResource).map(::CustomBarTextElement)
    }

    fun register() {
        CustomBarState.register(::isActive)
        ChatEvents.onActionBarModify("Custom Bars action bar", ::isActive) { message ->
            message.component.withoutRanges(
                CustomBarsActionBarParser.parse(message.plainText).ranges(
                    CustomBarStatus.hiddenBy(config.settings, inRift),
                ),
            )
        }
        registerVanillaReplacements()
        GuiOverlayRegistry.register(
            GuiOverlay(
                id = "custom_bars",
                layer = GuiOverlayLayer.BELOW_SCREEN,
                contexts = GuiOverlayContextType.entries.toSet(),
                visible = { isHudVisible() },
                render = { context, _ -> renderParts(context) },
            ),
        )
        CustomBarPart.entries.forEach { part ->
            HudEditorRegistry.register(CustomBarEditorElement(part))
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
            part.vanillaDisplay().renderPositioned(context, part.position(), render)
        }
    }

    private fun isActive(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    internal fun isHudVisible(): Boolean = isActive() && Minecraft.getInstance().player != null &&
        !MinecraftClient.isGuiHidden(Minecraft.getInstance())

    private fun renderParts(context: GuiGraphicsExtractor) {
        context.withIsolatedPose {
            pose().translate(0f, -BottomHudLayout.reservedHeight().toFloat())
            for (part in CustomBarPart.entries) {
                if (part.isCustomVisible()) {
                    context.withIsolatedPose {
                        pose().translate(part.layoutOffsetX().toFloat(), 0f)
                        part.position().renderRenderable(context, CustomBarRenderable.create(part))
                    }
                }
            }
            textElements.filter(CustomBarTextElement::isVisible).forEach { it.renderLive(context) }
        }
    }
}
