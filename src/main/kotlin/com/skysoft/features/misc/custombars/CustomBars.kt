package com.skysoft.features.misc.custombars

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.skyblock.SkyBlockStatGlyph
import com.skysoft.gui.BottomHudLayout
import com.skysoft.gui.GuiOverlay
import com.skysoft.gui.GuiOverlayContextType
import com.skysoft.gui.GuiOverlayLayer
import com.skysoft.gui.GuiOverlayRegistry
import com.skysoft.gui.HudEditorElement
import com.skysoft.gui.HudEditorRegistry
import com.skysoft.config.core.HudPosition
import com.skysoft.utils.MinecraftClient
import com.skysoft.utils.NumberUtilities.addSeparators
import com.skysoft.utils.NumberUtilities.shortFormat
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.chat.ChatEvents
import com.skysoft.utils.chat.ChatMessageVisibility
import com.skysoft.utils.renderables.GuiRenderable
import com.skysoft.utils.renderables.primitives.ItemIconRenderable
import com.skysoft.utils.renderables.renderAt
import com.skysoft.utils.renderables.renderRenderable
import com.skysoft.utils.renderables.withIsolatedPose
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Optional
import kotlin.math.roundToInt

object CustomBars {
    private val config get() = SkysoftConfigGui.config().gui.customBars
    private var health: BarValue? = null
    private var mana: BarValue? = null
    private var defense: Int? = null

    fun register() {
        ChatEvents.onActionBar("Custom Bars tracking", ::isActive) { message ->
            update(CustomBarsActionBarParser.parse(message.plainText))
            ChatMessageVisibility.SHOW
        }
        ChatEvents.onActionBarModify("Custom Bars action bar", ::isActive) { message ->
            val settings = config.settings
            val hidden = buildSet {
                if (settings.health) add(CustomBarStatus.HEALTH)
                if (settings.mana) add(CustomBarStatus.MANA)
                if (settings.defense) add(CustomBarStatus.DEFENSE)
            }
            message.component.withoutRanges(CustomBarsActionBarParser.parse(message.plainText).ranges(hidden))
        }
        SkysoftClientEvents.onDisconnect("Custom Bars reset", ::reset)
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
                override val layoutOffsetY: Int get() = -BottomHudLayout.reservedHeight()
                override val hasEditorBackground: Boolean = false
                override fun width(): Int = part.width
                override fun height(): Int = part.height
                override fun isVisible(): Boolean = config.enabled && part.isEnabled()
                override fun renderDummy(context: GuiGraphicsExtractor) {
                    renderable(part, previewAir = part == CustomBarPart.AIR).render(context)
                }
                override fun openConfig() = SkysoftConfigGui.open("Custom Bars")
            })
        }
    }

    private fun registerVanillaReplacements() {
        replaceVanilla(VanillaHudElements.HEALTH_BAR) { config.settings.health }
        replaceVanilla(VanillaHudElements.ARMOR_BAR) { config.settings.defense }
        replaceVanilla(VanillaHudElements.FOOD_BAR) { config.settings.health || config.settings.mana }
        replaceVanilla(VanillaHudElements.MOUNT_HEALTH) { config.settings.health }
        replaceVanilla(VanillaHudElements.EXPERIENCE_LEVEL) { config.settings.experience }
        replaceVanilla(VanillaHudElements.INFO_BAR) { config.settings.experience }
        replaceVanilla(VanillaHudElements.AIR_BAR) { config.settings.air }
    }

    private fun replaceVanilla(id: Identifier, shouldHide: () -> Boolean) {
        HudElementRegistry.replaceElement(id) { vanilla ->
            HudElement { context, tick ->
                if (!isActive() || !shouldHide()) vanilla.extractRenderState(context, tick)
            }
        }
    }

    private fun isActive(): Boolean = config.enabled && HypixelLocationState.inSkyBlock

    private fun update(parsed: ParsedCustomBarActionBar) {
        parsed.health?.let { health = it }
        parsed.mana?.let { mana = it }
        parsed.defense?.let { defense = it }
    }

    private fun reset() {
        health = null
        mana = null
        defense = null
    }

    private enum class CustomBarPart(val label: String, val width: Int, val height: Int) {
        HEALTH("Health Bar", HALF_RESOURCE_WIDTH, RESOURCE_HEIGHT),
        MANA("Mana Bar", HALF_RESOURCE_WIDTH, RESOURCE_HEIGHT),
        EXPERIENCE("Experience Bar", FULL_RESOURCE_WIDTH, RESOURCE_HEIGHT),
        DEFENSE("Defense", READOUT_WIDTH, READOUT_ELEMENT_HEIGHT),
        SPEED("Speed", READOUT_WIDTH, READOUT_ELEMENT_HEIGHT),
        AIR("Air", READOUT_WIDTH, READOUT_ELEMENT_HEIGHT),
        ;

        fun isEnabled(): Boolean = when (this) {
            HEALTH -> config.settings.health
            MANA -> config.settings.mana
            EXPERIENCE -> config.settings.experience
            DEFENSE -> config.settings.defense
            SPEED -> config.settings.speed
            AIR -> config.settings.air
        }

        fun position(): HudPosition = when (this) {
            HEALTH -> config.healthPosition
            MANA -> config.manaPosition
            EXPERIENCE -> config.experiencePosition
            DEFENSE -> config.defensePosition
            SPEED -> config.speedPosition
            AIR -> config.airPosition
        }
    }

    private fun renderParts(context: GuiGraphicsExtractor) {
        context.withIsolatedPose {
            pose().translate(0f, -BottomHudLayout.reservedHeight().toFloat())
            for (part in CustomBarPart.entries) {
                if (part.isEnabled() &&
                    (part != CustomBarPart.AIR || Minecraft.getInstance().player?.isUnderWater == true)
                ) {
                    part.position().renderRenderable(context, renderable(part))
                }
            }
        }
    }

    private fun renderable(part: CustomBarPart, previewAir: Boolean = false): GuiRenderable =
        CustomBarsRenderable(part, health, mana, defense, previewAir)

    private class CustomBarsRenderable(
        private val part: CustomBarPart,
        private val health: BarValue?,
        private val mana: BarValue?,
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
                    HEALTH_COLOR,
                    HEALTH_OVERFLOW_COLOR,
                    SkyBlockStatGlyph.HEALTH.toString(),
                )
                CustomBarPart.MANA -> drawBar(
                    context,
                    mana,
                    MANA_COLOR,
                    MANA_OVERFLOW_COLOR,
                    SkyBlockStatGlyph.INTELLIGENCE.toString(),
                )
                CustomBarPart.EXPERIENCE -> {
                    drawExperienceIcon(context)
                    drawProgressBar(
                        context,
                        BAR_X,
                        RESOURCE_BAR_Y,
                        FULL_BAR_WIDTH,
                        player?.experienceProgress ?: 0f,
                        XP_COLOR,
                    )
                    drawCenteredText(
                        context,
                        (player?.experienceLevel ?: 0).toString(),
                        BAR_X,
                        RESOURCE_BAR_Y + BAR_TEXT_Y_OFFSET,
                        FULL_BAR_WIDTH,
                        XP_COLOR,
                    )
                }
                CustomBarPart.DEFENSE -> drawReadout(
                    context,
                    SkyBlockStatGlyph.DEFENSE.toString(),
                    defense?.addSeparators() ?: "---",
                    DEFENSE_ICON_COLOR,
                )
                CustomBarPart.SPEED -> drawReadout(
                    context,
                    SkyBlockStatGlyph.SPEED.toString(),
                    player?.skyBlockSpeed()?.addSeparators() ?: "---",
                    SPEED_ICON_COLOR,
                )
                CustomBarPart.AIR -> {
                    val remainingTicks = if (previewAir && player?.isUnderWater != true) {
                        PREVIEW_AIR_TICKS
                    } else {
                        player?.airSupply ?: 0
                    }
                    drawAirReadout(context, remainingTicks.coerceAtLeast(0) / TICKS_PER_SECOND)
                }
            }
        }

        private fun drawBar(
            context: GuiGraphicsExtractor,
            value: BarValue?,
            color: Int,
            overflowColor: Int,
            icon: String,
        ) {
            drawIcon(context, icon, 0, RESOURCE_BAR_Y + ICON_Y_OFFSET, color)
            drawResourceBar(context, BAR_X, RESOURCE_BAR_Y, HALF_BAR_WIDTH, value, color, overflowColor)
            drawCenteredText(
                context,
                resourceText(value, HALF_BAR_WIDTH),
                BAR_X,
                RESOURCE_BAR_Y + BAR_TEXT_Y_OFFSET,
                HALF_BAR_WIDTH,
                color,
            )
        }

        private fun drawResourceBar(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            value: BarValue?,
            color: Int,
            overflowColor: Int,
        ) {
            context.fillRoundedRect(x, y, width, BAR_HEIGHT, TRACK_COLOR)
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
            val fillHeight = BAR_HEIGHT - INNER_PADDING * 2
            if (baseWidth > 0) context.fillGlossyRoundedRect(fillX, fillY, baseWidth, fillHeight, color)
            if (overflowWidth > 0) {
                context.fillGlossyRoundedRect(fillX + baseWidth, fillY, overflowWidth, fillHeight, overflowColor)
            }
            if (baseWidth > 0 && overflowWidth > 0) {
                val baseBridge = CORNER_RADIUS.coerceAtMost(baseWidth)
                val overflowBridge = CORNER_RADIUS.coerceAtMost(overflowWidth)
                context.fillGlossyRect(fillX + baseWidth - baseBridge, fillY, baseBridge, fillHeight, color)
                context.fillGlossyRect(fillX + baseWidth, fillY, overflowBridge, fillHeight, overflowColor)
            }
        }

        private fun resourceText(value: BarValue?, width: Int): String {
            if (value == null) return "---/---"
            val exact = "${value.displayedCurrent.addSeparators()}/${value.maximum.addSeparators()}"
            if (Minecraft.getInstance().font.width(exact) <= width - TEXT_PADDING * 2) return exact
            return "${value.displayedCurrent.toLong().shortFormat()}/${value.maximum.toLong().shortFormat()}"
        }

        private fun drawProgressBar(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            fill: Float,
            color: Int,
        ) {
            context.fillRoundedRect(x, y, width, BAR_HEIGHT, TRACK_COLOR)
            val innerWidth = ((width - INNER_PADDING * 2) * fill.coerceIn(0f, 1f)).roundToInt()
            if (innerWidth > 0) {
                context.fillGlossyRoundedRect(
                    x + INNER_PADDING,
                    y + INNER_PADDING,
                    innerWidth,
                    BAR_HEIGHT - INNER_PADDING * 2,
                    color,
                )
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

        private fun drawExperienceIcon(context: GuiGraphicsExtractor) {
            EXPERIENCE_ICON.renderAt(context, EXPERIENCE_ICON_X, RESOURCE_BAR_Y + EXPERIENCE_ICON_Y_OFFSET)
        }

        private fun drawCenteredText(
            context: GuiGraphicsExtractor,
            text: String,
            x: Int,
            y: Int,
            width: Int,
            color: Int,
        ) {
            val font = Minecraft.getInstance().font
            drawText(context, text, x + (width - font.width(text)) / 2, y, color)
        }

        private fun drawText(context: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
            val font = Minecraft.getInstance().font
            if (config.details.textOutline) {
                context.text(font, text, x + 1, y, TEXT_OUTLINE_COLOR, false)
                context.text(font, text, x - 1, y, TEXT_OUTLINE_COLOR, false)
                context.text(font, text, x, y + 1, TEXT_OUTLINE_COLOR, false)
                context.text(font, text, x, y - 1, TEXT_OUTLINE_COLOR, false)
            }
            context.text(font, text, x, y, color, false)
        }

        private fun drawReadout(
            context: GuiGraphicsExtractor,
            icon: String,
            value: String,
            iconColor: Int,
        ) {
            val font = Minecraft.getInstance().font
            val contentWidth = font.width(icon) + READOUT_CONTENT_GAP + font.width(value)
            val contentX = (READOUT_WIDTH - contentWidth) / 2
            context.fillRoundedRect(0, READOUT_BACKGROUND_Y, READOUT_WIDTH, READOUT_HEIGHT, TRACK_COLOR)
            drawIcon(context, icon, contentX, READOUT_BACKGROUND_Y, iconColor)
            drawText(
                context,
                value,
                contentX + font.width(icon) + READOUT_CONTENT_GAP,
                READOUT_BACKGROUND_Y,
                TEXT_COLOR,
            )
        }

        private fun drawAirReadout(context: GuiGraphicsExtractor, seconds: Int) {
            val text = "${seconds}s"
            val font = Minecraft.getInstance().font
            val contentWidth = ICON_SIZE + READOUT_CONTENT_GAP + font.width(text)
            val contentX = (READOUT_WIDTH - contentWidth) / 2
            context.fillRoundedRect(0, READOUT_BACKGROUND_Y, READOUT_WIDTH, READOUT_HEIGHT, TRACK_COLOR)
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                AIR_SPRITE,
                contentX,
                READOUT_BACKGROUND_Y,
                ICON_SIZE,
                ICON_SIZE,
            )
            drawText(
                context,
                text,
                contentX + ICON_SIZE + READOUT_CONTENT_GAP,
                READOUT_BACKGROUND_Y,
                TEXT_COLOR,
            )
        }
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

    fun parse(text: String): ParsedCustomBarActionBar {
        val normalized = NormalizedActionBar(text)
        val healthMatch = healthPattern.find(normalized.text)
        val manaMatch = manaPattern.find(normalized.text)
        val defenseMatch = defensePattern.find(normalized.text)
        return ParsedCustomBarActionBar(
            health = healthMatch?.let {
                BarValue(it.value("current"), it.value("maximum"))
            },
            mana = manaMatch?.let {
                BarValue(it.value("current"), it.value("maximum"), it.groups["overflow"]?.value?.skyBlockInt() ?: 0)
            },
            defense = defenseMatch?.groups?.get("defense")?.value?.skyBlockInt(),
            removals = buildList {
                healthMatch?.let {
                    val healingFollows = normalized.text.getOrNull(it.range.last + 1) == '+'
                    val range = normalized.rawRange(it.range, preserveLastCharacter = healingFollows)
                    add(StatusRemoval(CustomBarStatus.HEALTH, if (healingFollows) range else text.statusRange(range)))
                }
                manaMatch?.let {
                    add(StatusRemoval(CustomBarStatus.MANA, text.statusRange(normalized.rawRange(it.range))))
                }
                defenseMatch?.let {
                    add(StatusRemoval(CustomBarStatus.DEFENSE, text.statusRange(normalized.rawRange(it.range))))
                }
            },
        )
    }

    fun filter(text: String, hidden: Set<CustomBarStatus>): String {
        val ranges = parse(text).ranges(hidden)
        return text.filterIndexed { index, _ -> ranges.none { index in it } }
    }

    private fun MatchResult.value(name: String): Int = groups[name]!!.value.skyBlockInt()

    private fun String.statusRange(match: IntRange): IntRange {
        var start = match.first
        val endExclusive = match.last + 1
        while (start > 0 && this[start - 1] == ' ') start--
        if (match.first - start >= STATUS_SEPARATOR_LENGTH) return start until endExclusive
        var trailingEnd = endExclusive
        while (getOrNull(trailingEnd) == ' ') trailingEnd++
        return if (trailingEnd - endExclusive >= STATUS_SEPARATOR_LENGTH) match.first until trailingEnd else match
    }

    private fun String.skyBlockInt(): Int = replace(",", "").toInt()
}

private class NormalizedActionBar(private val raw: String) {
    private val rawIndices: IntArray
    val text: String

    init {
        val indices = mutableListOf<Int>()
        text = buildString {
            var index = 0
            while (index < raw.length) {
                if (raw[index] == LEGACY_FORMAT_PREFIX && index + 1 < raw.length) {
                    index += LEGACY_FORMAT_LENGTH
                } else {
                    append(raw[index])
                    indices += index
                    index++
                }
            }
        }
        rawIndices = indices.toIntArray()
    }

    fun rawRange(range: IntRange, preserveLastCharacter: Boolean = false): IntRange {
        val start = formattingStart(rawIndices[range.first])
        val last = rawIndices[range.last]
        return if (preserveLastCharacter) start until formattingStart(last) else start..last
    }

    private fun formattingStart(index: Int): Int {
        var start = index
        while (start >= LEGACY_FORMAT_LENGTH && raw[start - LEGACY_FORMAT_LENGTH] == LEGACY_FORMAT_PREFIX) {
            start -= LEGACY_FORMAT_LENGTH
        }
        return start
    }
}

internal enum class CustomBarStatus {
    HEALTH,
    MANA,
    DEFENSE,
}

internal data class BarValue(val current: Int, val maximum: Int, val overflow: Int = 0) {
    val displayOverflow: Int get() = overflow.coerceAtLeast((current - maximum).coerceAtLeast(0))
    val regularCurrent: Int get() = current.coerceAtMost(maximum)
    val displayedCurrent: Int get() = regularCurrent + displayOverflow
}

internal data class ParsedCustomBarActionBar(
    val health: BarValue?,
    val mana: BarValue?,
    val defense: Int?,
    private val removals: List<StatusRemoval>,
) {
    fun ranges(hidden: Set<CustomBarStatus>): List<IntRange> =
        removals.filter { it.status in hidden }.map(StatusRemoval::range)
}

internal data class StatusRemoval(val status: CustomBarStatus, val range: IntRange)

private fun Component.withoutRanges(ranges: List<IntRange>): Component {
    if (ranges.isEmpty()) return this
    val output = Component.empty()
    var offset = 0
    visit({ style: Style, text: String ->
        val kept = text.filterIndexed { index, _ -> ranges.none { offset + index in it } }
        if (kept.isNotEmpty()) output.append(Component.literal(kept).withStyle(style))
        offset += text.length
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return output
}

private fun GuiGraphicsExtractor.fillRoundedRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    if (width <= CORNER_RADIUS * 2 || height <= CORNER_RADIUS * 2) {
        fill(x, y, x + width, y + height, color)
        return
    }
    fill(x + CORNER_RADIUS, y, x + width - CORNER_RADIUS, y + 1, color)
    fill(x + 1, y + 1, x + width - 1, y + CORNER_RADIUS, color)
    fill(x, y + CORNER_RADIUS, x + width, y + height - CORNER_RADIUS, color)
    fill(x + 1, y + height - CORNER_RADIUS, x + width - 1, y + height - 1, color)
    fill(x + CORNER_RADIUS, y + height - 1, x + width - CORNER_RADIUS, y + height, color)
}

private fun GuiGraphicsExtractor.fillGlossyRoundedRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    fillRoundedRect(x, y, width, height, color)
    if (width <= CORNER_RADIUS * 2 || height <= CORNER_RADIUS * 2) return
    fill(x + CORNER_RADIUS, y + 1, x + width - CORNER_RADIUS, y + 2, color.adjustRgb(GLOSS_HIGHLIGHT))
    fill(
        x + CORNER_RADIUS,
        y + height - 2,
        x + width - CORNER_RADIUS,
        y + height - 1,
        color.adjustRgb(GLOSS_SHADE),
    )
}

private fun GuiGraphicsExtractor.fillGlossyRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
    fill(x, y, x + width, y + height, color)
    if (height <= CORNER_RADIUS * 2) return
    fill(x, y + 1, x + width, y + 2, color.adjustRgb(GLOSS_HIGHLIGHT))
    fill(x, y + height - 2, x + width, y + height - 1, color.adjustRgb(GLOSS_SHADE))
}

private fun Int.adjustRgb(amount: Int): Int {
    val red = ((this ushr RED_SHIFT) and COLOR_CHANNEL_MASK).plus(amount).coerceIn(0, COLOR_CHANNEL_MASK)
    val green = ((this ushr GREEN_SHIFT) and COLOR_CHANNEL_MASK).plus(amount).coerceIn(0, COLOR_CHANNEL_MASK)
    val blue = (this and COLOR_CHANNEL_MASK).plus(amount).coerceIn(0, COLOR_CHANNEL_MASK)
    return (this and ALPHA_MASK) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

private fun net.minecraft.world.entity.player.Player.skyBlockSpeed(): Int =
    ((if (isSprinting) speed / SPRINT_SPEED_MULTIPLIER else speed) * SPEED_SCALE).roundToInt()

private const val PREVIEW_AIR_TICKS = 220
private const val HOTBAR_WIDTH = 182
private const val BAR_GAP = 4
private const val ICON_SLOT_WIDTH = 10
private const val HALF_BAR_WIDTH = (HOTBAR_WIDTH - BAR_GAP - ICON_SLOT_WIDTH * 2) / 2
private const val HALF_RESOURCE_WIDTH = ICON_SLOT_WIDTH + HALF_BAR_WIDTH
private const val FULL_RESOURCE_WIDTH = HOTBAR_WIDTH
private const val FULL_BAR_WIDTH = HOTBAR_WIDTH - ICON_SLOT_WIDTH
private const val BAR_X = ICON_SLOT_WIDTH
private const val RESOURCE_HEIGHT = 14
private const val RESOURCE_BAR_Y = 5
private const val BAR_HEIGHT = 7
private const val READOUT_WIDTH = 44
private const val READOUT_HEIGHT = 9
private const val READOUT_ELEMENT_HEIGHT = 11
private const val READOUT_BACKGROUND_Y = 1
private const val READOUT_CONTENT_GAP = 1
private const val BAR_TEXT_Y_OFFSET = -4
private const val ICON_Y_OFFSET = -1
private const val INNER_PADDING = 1
private const val TEXT_PADDING = 2
private const val ICON_SIZE = 9
private const val EXPERIENCE_ICON_SIZE = 11
private const val EXPERIENCE_ICON_X = -2
private const val EXPERIENCE_ICON_Y_OFFSET = -2
private const val CORNER_RADIUS = 2
private const val GLOSS_HIGHLIGHT = 42
private const val GLOSS_SHADE = -48
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val COLOR_CHANNEL_MASK = 0xFF
private const val ALPHA_MASK = 0xFF000000.toInt()
private const val STATUS_SEPARATOR_LENGTH = 2
private const val LEGACY_FORMAT_PREFIX = '§'
private const val LEGACY_FORMAT_LENGTH = 2
private const val TICKS_PER_SECOND = 20
private const val SPEED_SCALE = 1_000f
private const val SPRINT_SPEED_MULTIPLIER = 1.3f
private const val TRACK_COLOR = 0xC0101010.toInt()
private const val HEALTH_COLOR = 0xFFFF5555.toInt()
private const val HEALTH_OVERFLOW_COLOR = 0xFFFFB42B.toInt()
private const val MANA_COLOR = 0xFF55FFFF.toInt()
private const val MANA_OVERFLOW_COLOR = 0xFFAA00FF.toInt()
private const val XP_COLOR = 0xFF80FF20.toInt()
private const val DEFENSE_ICON_COLOR = 0xFF55FF55.toInt()
private const val SPEED_ICON_COLOR = 0xFFFFFFFF.toInt()
private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val TEXT_OUTLINE_COLOR = 0xFF000000.toInt()
private val AIR_SPRITE = Identifier.withDefaultNamespace("hud/air")
private val EXPERIENCE_ICON = ItemIconRenderable(ItemStack(Items.EXPERIENCE_BOTTLE), EXPERIENCE_ICON_SIZE / 16.0)
