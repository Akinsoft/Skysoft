package com.skysoft.features.misc

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.TabListApi
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object SkyBlockLevelBar {
    private val isEnabled get() = SkysoftConfigGui.config().gui.isSkyBlockLevelBarEnabled
    private var cachedLevel: SkyBlockLevelProgress? = null

    internal val isReplacingExperience: Boolean get() = currentLevel() != null

    fun register() {
        TabListApi.onChange(
            "SkyBlock Level Bar",
            isActive = { isEnabled },
        ) {
            cachedLevel = if (TabListApi.isSkyBlockDataLoaded) {
                parseSkyBlockLevelProgress(TabListApi.skyBlockLines)
            } else {
                null
            }
        }
    }

    internal fun displayedExperienceProgress(original: Float): Float = currentLevel()?.progress ?: original

    internal fun displayedExperienceLevel(original: Int): Int = currentLevel()?.level ?: original

    internal fun displayedExperienceBarColor(original: Int): Int = currentLevel()?.experienceColor ?: original

    internal fun displayedExperienceLevelColor(original: Int): Int = currentLevel()?.levelColor ?: original

    internal fun experienceLevelVisibility(original: Int): Int =
        currentLevel()?.level?.coerceAtLeast(1) ?: original

    internal fun renderVanillaExperienceBar(context: GuiGraphicsExtractor, render: () -> Unit) {
        val level = currentLevel() ?: return render()
        val x = (context.guiWidth() - VANILLA_EXPERIENCE_WIDTH) / 2
        val y = context.guiHeight() - VANILLA_EXPERIENCE_MARGIN_BOTTOM - VANILLA_EXPERIENCE_BAR_HEIGHT
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            VANILLA_EXPERIENCE_BACKGROUND_SPRITE,
            x,
            y,
            VANILLA_EXPERIENCE_WIDTH,
            VANILLA_EXPERIENCE_BAR_HEIGHT,
        )
        val width = (level.progress * VANILLA_EXPERIENCE_PROGRESS_SCALE).toInt()
            .coerceIn(0, VANILLA_EXPERIENCE_WIDTH)
        drawVanillaExperienceProgress(context, x, y, width, level.experienceColor)
    }

    internal fun renderVanillaExperienceProgress(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        render: () -> Unit,
    ) {
        val color = currentLevel()?.experienceColor ?: return render()
        drawVanillaExperienceProgress(context, x, y, width, color)
    }

    private fun currentLevel(): SkyBlockLevelProgress? =
        cachedLevel.takeIf { isEnabled && TabListApi.isSkyBlockDataLoaded }
}

internal data class SkyBlockLevelProgress(
    val level: Int,
    val progress: Float,
    val levelColor: Int,
    val experienceColor: Int,
)

internal fun parseSkyBlockLevelProgress(lines: Iterable<Component>): SkyBlockLevelProgress? =
    lines.firstNotNullOfOrNull { line ->
        val match = skyBlockLevelPattern.matchEntire(line.string) ?: return@firstNotNullOfOrNull null
        val levelGroup = match.groups["level"] ?: return@firstNotNullOfOrNull null
        val experienceGroup = match.groups["experience"] ?: return@firstNotNullOfOrNull null
        val requiredExperienceGroup = match.groups["requiredExperience"] ?: return@firstNotNullOfOrNull null
        val level = levelGroup.value.toIntOrNull() ?: return@firstNotNullOfOrNull null
        val experience = experienceGroup.value.toIntOrNull() ?: return@firstNotNullOfOrNull null
        val requiredExperience = requiredExperienceGroup.value.toIntOrNull() ?: return@firstNotNullOfOrNull null
        SkyBlockLevelProgress(
            level,
            experience.toFloat() / requiredExperience,
            line.opaqueColorAt(levelGroup.range.first) ?: return@firstNotNullOfOrNull null,
            line.opaqueColorAt(experienceGroup.range.first) ?: return@firstNotNullOfOrNull null,
        ).takeIf { requiredExperience > 0 && experience <= requiredExperience }
    }

private fun Component.opaqueColorAt(index: Int): Int? {
    var offset = 0
    for (part in toFlatList()) {
        val end = offset + part.string.length
        if (index in offset until end) return part.style.color?.value?.or(OPAQUE_ALPHA)
        offset = end
    }
    return null
}

private fun drawVanillaExperienceProgress(
    context: GuiGraphicsExtractor,
    x: Int,
    y: Int,
    width: Int,
    color: Int,
) {
    if (width <= 0) return
    context.fill(x, y + 1, x + width, y + VANILLA_EXPERIENCE_BAR_HEIGHT - 1, color)
    if (width > 2) {
        context.fill(x + 1, y, x + width - 1, y + 1, color)
        context.fill(
            x + 1,
            y + VANILLA_EXPERIENCE_BAR_HEIGHT - 1,
            x + width - 1,
            y + VANILLA_EXPERIENCE_BAR_HEIGHT,
            color,
        )
    }
}

private val skyBlockLevelPattern =
    Regex("""^\s*SB Level: \[(?<level>\d+)] (?<experience>\d+)/(?<requiredExperience>\d+) XP\s*$""")
private const val OPAQUE_ALPHA = 0xFF000000.toInt()
private const val VANILLA_EXPERIENCE_WIDTH = 182
private const val VANILLA_EXPERIENCE_BAR_HEIGHT = 5
private const val VANILLA_EXPERIENCE_MARGIN_BOTTOM = 24
private const val VANILLA_EXPERIENCE_PROGRESS_SCALE = 183f
private val VANILLA_EXPERIENCE_BACKGROUND_SPRITE =
    Identifier.withDefaultNamespace("hud/experience_bar_background")
