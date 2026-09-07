package com.skysoft.features.misc.custombars

import com.skysoft.config.core.HudPosition
import com.skysoft.features.misc.AbsorptionHeartLayout
import com.skysoft.features.misc.SkyBlockLevelBar
import com.skysoft.features.misc.custombars.VanillaCustomBarDisplay.Companion.HOTBAR_WIDTH
import com.skysoft.features.misc.custombars.VanillaCustomBarDisplay.Companion.ICON_SIZE
import com.skysoft.gui.transform
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import kotlin.math.ceil

internal enum class VanillaCustomBarDisplay {
    HEALTH,
    EXPERIENCE,
    AIR,
    ;

    val width: Int
        get() = when (this) {
            HEALTH, AIR -> VANILLA_STATUS_WIDTH
            EXPERIENCE -> VANILLA_EXPERIENCE_WIDTH
        }

    val height: Int
        get() = when (this) {
            HEALTH -> vanillaHealthLayout().height
            AIR -> ICON_SIZE
            EXPERIENCE -> VANILLA_EXPERIENCE_HEIGHT
        }

    fun renderPositioned(
        context: GuiGraphicsExtractor,
        position: HudPosition,
        render: () -> Unit,
    ) {
        val displayHeight = height
        val transform = position.transform(width, displayHeight)
        val sourceX: Int
        val sourceY: Int
        when (this) {
            HEALTH -> {
                sourceX = context.guiWidth() / 2 - VANILLA_HUD_HALF_WIDTH
                sourceY = context.guiHeight() - VANILLA_HEALTH_TOP_OFFSET - (displayHeight - ICON_SIZE)
            }
            EXPERIENCE -> {
                sourceX = (context.guiWidth() - VANILLA_EXPERIENCE_WIDTH) / 2
                sourceY = context.guiHeight() - VANILLA_EXPERIENCE_TOP_OFFSET
            }
            AIR -> {
                sourceX = context.guiWidth() / 2 + VANILLA_AIR_LEFT_OFFSET
                sourceY = context.guiHeight() - VANILLA_AIR_TOP_OFFSET
            }
        }
        transform.render(context) {
            pose().translate(-sourceX.toFloat(), -sourceY.toFloat())
            render()
        }
    }

    fun renderPreview(context: GuiGraphicsExtractor, showExperienceLevel: Boolean) {
        when (this) {
            HEALTH -> renderVanillaHealthPreview(context)
            EXPERIENCE -> {
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
                if (showExperienceLevel) drawVanillaExperienceLevelPreview(context)
            }
            AIR -> repeat(VANILLA_STATUS_ICON_COUNT) { index ->
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    AIR_SPRITE,
                    index * VANILLA_STATUS_ICON_SPACING,
                    0,
                    ICON_SIZE,
                    ICON_SIZE,
                )
            }
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

    companion object {
        const val HOTBAR_WIDTH = 182
        const val ICON_SIZE = 9
        val AIR_SPRITE = Identifier.withDefaultNamespace("hud/air")
    }
}

private data class VanillaHealthLayout(
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
private const val ALPHA_MASK = 0xFF000000.toInt()
private const val TEXT_OUTLINE_RGB = ALPHA_MASK
private const val VANILLA_EXPERIENCE_LEVEL_RGB = 0xFF80FF20.toInt()
private val VANILLA_HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container")
private val VANILLA_HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full")
private val VANILLA_HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half")
private val VANILLA_ABSORPTION_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full")
private val VANILLA_ABSORPTION_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half")
private val VANILLA_EXPERIENCE_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_background")
private val VANILLA_EXPERIENCE_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_progress")
